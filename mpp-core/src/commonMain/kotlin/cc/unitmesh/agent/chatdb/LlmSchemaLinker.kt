package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.agent.database.TableSchema
import cc.unitmesh.llm.KoogLLMService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLM-based Schema Linker - Uses LLM to extract keywords and link schema
 * 
 * This implementation uses the LLM to:
 * 1. Extract semantic keywords from the natural language query
 * 2. Map natural language terms to database schema elements
 * 
 * Falls back to KeywordSchemaLinker if LLM fails.
 */
class LlmSchemaLinker(
    private val llmService: KoogLLMService,
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

        private const val SCHEMA_LINKING_PROMPT = """You are a database schema expert. Given a user query and database schema, identify the most relevant tables and columns.

IMPORTANT: You MUST only use table and column names that exist in the provided schema. Do NOT invent or hallucinate table/column names.

Database Schema:
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
    
    private fun buildSchemaDescription(schema: DatabaseSchema): String {
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

