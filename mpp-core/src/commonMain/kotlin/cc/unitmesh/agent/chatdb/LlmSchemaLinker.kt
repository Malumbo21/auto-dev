package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.database.DatabaseConnection
import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.llm.LLMService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLM-based Schema Linker - Uses LLM to extract keywords and link schema
 *
 * This implementation uses the LLM to:
 * 1. Extract semantic keywords from the natural language query
 * 2. Map natural language terms to database schema elements
 * 3. Use sample data for better value matching (RSL-SQL approach)
 *
 * Falls back to KeywordSchemaLinker if LLM fails.
 *
 * Based on research from:
 * - RSL-SQL: Robust Schema Linking in Text-to-SQL Generation
 * - A Survey of NL2SQL with Large Language Models
 */
class LlmSchemaLinker(
    private val llmService: LLMService,
    private val databaseConnection: DatabaseConnection? = null,
    private val fallbackLinker: KeywordSchemaLinker = KeywordSchemaLinker()
) : SchemaLinker() {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEYWORD_EXTRACTION_PROMPT = """You are a database schema expert. Extract keywords from the user's natural language query that are relevant for finding database tables and columns.

Given the user query, extract:
1. Entity names (nouns that might be table names)
2. Attribute names (properties that might be column names)
3. Semantic synonyms (alternative terms for the same concept)

Respond ONLY with a JSON object in this exact format:
{"keywords": ["keyword1", "keyword2", ...], "entities": ["entity1", ...], "attributes": ["attr1", ...]}

User Query: """

        // Enhanced Schema Linking prompt based on RSL-SQL research
        private const val SCHEMA_LINKING_PROMPT = """You are a database schema expert. Given a user query and database schema with sample data, identify the most relevant tables and columns.

CRITICAL RULES:
1. You MUST only use table and column names that EXACTLY exist in the provided schema
2. Do NOT invent or hallucinate table/column names
3. Look at the sample data to understand what each table contains
4. Match user's intent with the actual table/column names, not assumed names

Database Schema with Sample Data:
{{SCHEMA}}

User Query: {{QUERY}}

Respond ONLY with a JSON object in this exact format:
{"tables": ["table1", "table2"], "columns": ["table1.column1", "table2.column2"], "confidence": 0.8}

Only include tables and columns that are directly relevant to answering the query."""
    }
    
    @Serializable
    private data class KeywordExtractionResult(
        val keywords: List<String> = emptyList(),
        val entities: List<String> = emptyList(),
        val attributes: List<String> = emptyList()
    )
    
    @Serializable
    private data class SchemaLinkingLlmResult(
        val tables: List<String> = emptyList(),
        val columns: List<String> = emptyList(),
        val confidence: Double = 0.0
    )
    
    /**
     * Link natural language query to relevant schema elements using LLM
     */
    override suspend fun link(query: String, schema: DatabaseSchema): SchemaLinkingResult {
        return try {
            // Build schema description for LLM
            val schemaDescription = buildSchemaDescription(schema)

            // Ask LLM to identify relevant tables and columns
            val prompt = SCHEMA_LINKING_PROMPT
                .replace("{{SCHEMA}}", schemaDescription)
                .replace("{{QUERY}}", query)
            val response = llmService.sendPrompt(prompt)
            
            // Parse LLM response
            val llmResult = parseSchemaLinkingResponse(response)
            
            // Validate that tables/columns exist in schema
            val validTables = llmResult.tables.filter { tableName ->
                schema.tables.any { it.name.equals(tableName, ignoreCase = true) }
            }
            val validColumns = llmResult.columns.filter { colRef ->
                val parts = colRef.split(".")
                if (parts.size == 2) {
                    val table = schema.tables.find { it.name.equals(parts[0], ignoreCase = true) }
                    table?.columns?.any { it.name.equals(parts[1], ignoreCase = true) } == true
                } else false
            }
            
            // Extract keywords for the result
            val keywords = extractKeywords(query)
            
            // If LLM didn't find valid tables, fall back to keyword linker
            if (validTables.isEmpty()) {
                return fallbackLinker.link(query, schema)
            }
            
            SchemaLinkingResult(
                relevantTables = validTables,
                relevantColumns = validColumns,
                keywords = keywords,
                confidence = llmResult.confidence
            )
        } catch (e: Exception) {
            // Fall back to keyword-based linking on any error
            fallbackLinker.link(query, schema)
        }
    }
    
    /**
     * Extract keywords from natural language query using LLM
     */
    override suspend fun extractKeywords(query: String): List<String> {
        return try {
            val prompt = KEYWORD_EXTRACTION_PROMPT + query
            val response = llmService.sendPrompt(prompt)
            
            val result = parseKeywordExtractionResponse(response)
            (result.keywords + result.entities + result.attributes).distinct()
        } catch (e: Exception) {
            // Fall back to simple keyword extraction
            fallbackLinker.extractKeywords(query)
        }
    }
    
    /**
     * Build schema description with sample data for better Schema Linking
     * Based on RSL-SQL research: sample data helps LLM understand table semantics
     */
    private suspend fun buildSchemaDescription(schema: DatabaseSchema): String {
        val tableDescriptions = mutableListOf<String>()

        for (table in schema.tables) {
            val description = buildString {
                appendLine("Table: ${table.name}")

                // Column information
                val columns = table.columns.joinToString(", ") { col ->
                    "${col.name} (${col.type}${if (col.isPrimaryKey) ", PK" else ""})"
                }
                appendLine("Columns: $columns")

                // Add sample data if database connection is available
                if (databaseConnection != null) {
                    try {
                        val sampleRows = databaseConnection.getSampleRows(table.name, 2)
                        if (!sampleRows.isEmpty()) {
                            appendLine("Sample Data:")
                            appendLine("  ${sampleRows.columns.joinToString(" | ")}")
                            sampleRows.rows.take(2).forEach { row ->
                                appendLine("  ${row.joinToString(" | ") { it.take(30) }}")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore sample data errors
                    }
                }
            }.trim()
            tableDescriptions.add(description)
        }

        return tableDescriptions.joinToString("\n\n")
    }

    /**
     * Build schema description without sample data (synchronous version)
     */
    private fun buildSchemaDescriptionSync(schema: DatabaseSchema): String {
        return schema.tables.joinToString("\n\n") { table ->
            val columns = table.columns.joinToString(", ") { col ->
                "${col.name} (${col.type}${if (col.isPrimaryKey) ", PK" else ""})"
            }
            "Table: ${table.name}\nColumns: $columns"
        }
    }
    
    private fun parseKeywordExtractionResponse(response: String): KeywordExtractionResult {
        val jsonStr = extractJsonFromResponse(response)
        return try {
            json.decodeFromString<KeywordExtractionResult>(jsonStr)
        } catch (e: Exception) {
            KeywordExtractionResult()
        }
    }
    
    private fun parseSchemaLinkingResponse(response: String): SchemaLinkingLlmResult {
        val jsonStr = extractJsonFromResponse(response)
        return try {
            json.decodeFromString<SchemaLinkingLlmResult>(jsonStr)
        } catch (e: Exception) {
            SchemaLinkingLlmResult()
        }
    }
    
    private fun extractJsonFromResponse(response: String): String {
        // Try to find JSON object in the response
        val jsonPattern = Regex("""\{[^{}]*\}""")
        return jsonPattern.find(response)?.value ?: "{}"
    }
}

