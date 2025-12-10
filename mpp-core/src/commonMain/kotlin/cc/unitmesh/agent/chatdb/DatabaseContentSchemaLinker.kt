package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.database.DatabaseConnection
import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.agent.database.TableSchema
import cc.unitmesh.llm.KoogLLMService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Database Content Schema Linker - Uses database content to improve Schema Linking accuracy
 * 
 * Based on RSL-SQL research, this linker:
 * 1. Filters out system tables (sys_*, x$*, etc.)
 * 2. Retrieves sample data from each table
 * 3. Uses LLM with enriched context to identify relevant tables
 * 
 * This approach is more accurate than pure keyword matching because it uses
 * actual database content to understand table semantics.
 */
class DatabaseContentSchemaLinker(
    private val llmService: KoogLLMService,
    private val databaseConnection: DatabaseConnection,
    private val fallbackLinker: KeywordSchemaLinker = KeywordSchemaLinker()
) : SchemaLinker() {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        // System table prefixes to filter out
        private val SYSTEM_TABLE_PREFIXES = listOf(
            "sys_", "x$", "innodb_", "io_", "memory_", "schema_", "statement",
            "user_summary", "host_summary", "wait", "process", "session",
            "metrics", "privileges", "ps_", "flyway_", "hibernate_"
        )
        
        // System table exact names
        private val SYSTEM_TABLE_NAMES = setOf(
            "version", "latest_file_io", "session_ssl_status"
        )
        
        private const val SCHEMA_LINKING_PROMPT = """You are a database schema expert. Given a user query and database tables with sample data, identify the most relevant tables.

CRITICAL RULES:
1. ONLY select tables from the provided list - do NOT invent table names
2. Look at sample data to understand what each table actually contains
3. Match the user's semantic intent, not just keywords
4. For "文章/article/post" queries, look for tables containing blog/post content
5. For "作者/author/creator" queries, look for tables with author/creator information

Available Tables with Sample Data:
{{TABLES_WITH_SAMPLES}}

User Query: {{QUERY}}

Respond ONLY with a JSON object:
{"tables": ["table1", "table2"], "reason": "brief explanation", "confidence": 0.9}

Select ONLY tables that are directly needed to answer the query."""
    }
    
    @Serializable
    private data class SchemaLinkingResult(
        val tables: List<String> = emptyList(),
        val reason: String = "",
        val confidence: Double = 0.0
    )
    
    /**
     * Filter out system tables that are not relevant for user queries
     */
    private fun filterUserTables(schema: DatabaseSchema): List<TableSchema> {
        return schema.tables.filter { table ->
            val lowerName = table.name.lowercase()
            // Filter out system tables
            val isSystemTable = SYSTEM_TABLE_PREFIXES.any { prefix -> 
                lowerName.startsWith(prefix) 
            } || SYSTEM_TABLE_NAMES.contains(lowerName)
            !isSystemTable
        }
    }
    
    /**
     * Build table description with sample data for Schema Linking
     */
    private suspend fun buildTableWithSamples(table: TableSchema): String {
        return buildString {
            appendLine("Table: ${table.name}")
            appendLine("  Columns: ${table.columns.joinToString(", ") { "${it.name}(${it.type})" }}")
            
            // Get sample data to help understand table content
            try {
                val samples = databaseConnection.getSampleRows(table.name, 2)
                if (!samples.isEmpty()) {
                    appendLine("  Sample Data:")
                    appendLine("    ${samples.columns.joinToString(" | ")}")
                    samples.rows.take(2).forEach { row ->
                        appendLine("    ${row.joinToString(" | ") { it.take(30) }}")
                    }
                }
            } catch (e: Exception) {
                // Table might not exist in current database, skip it
                appendLine("  (No sample data available)")
            }
        }
    }
    
    override suspend fun link(query: String, schema: DatabaseSchema): cc.unitmesh.agent.chatdb.SchemaLinkingResult {
        return try {
            // Step 1: Filter out system tables
            val userTables = filterUserTables(schema)
            if (userTables.isEmpty()) {
                return fallbackLinker.link(query, schema)
            }
            
            // Step 2: Build table descriptions with sample data
            val tablesWithSamples = userTables.map { table ->
                buildTableWithSamples(table)
            }.joinToString("\n")
            
            // Step 3: Ask LLM to identify relevant tables
            val prompt = SCHEMA_LINKING_PROMPT
                .replace("{{TABLES_WITH_SAMPLES}}", tablesWithSamples)
                .replace("{{QUERY}}", query)
            
            val response = llmService.sendPrompt(prompt)
            val result = parseResponse(response)
            
            // Step 4: Validate tables exist
            val validTables = result.tables.filter { tableName ->
                userTables.any { it.name.equals(tableName, ignoreCase = true) }
            }
            
            if (validTables.isEmpty()) {
                return fallbackLinker.link(query, schema)
            }
            
            // Extract keywords for additional context
            val keywords = fallbackLinker.extractKeywords(query)
            
            cc.unitmesh.agent.chatdb.SchemaLinkingResult(
                relevantTables = validTables,
                relevantColumns = emptyList(),
                keywords = keywords,
                confidence = result.confidence
            )
        } catch (e: Exception) {
            fallbackLinker.link(query, schema)
        }
    }
    
    override suspend fun extractKeywords(query: String): List<String> {
        return fallbackLinker.extractKeywords(query)
    }
    
    private fun parseResponse(response: String): SchemaLinkingResult {
        val jsonPattern = Regex("""\{[^{}]*\}""")
        val jsonStr = jsonPattern.find(response)?.value ?: "{}"
        return try {
            json.decodeFromString<SchemaLinkingResult>(jsonStr)
        } catch (e: Exception) {
            SchemaLinkingResult()
        }
    }
}

