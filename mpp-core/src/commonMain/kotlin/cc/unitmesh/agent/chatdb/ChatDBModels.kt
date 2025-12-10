package cc.unitmesh.agent.chatdb

import cc.unitmesh.agent.database.DatabaseSchema
import cc.unitmesh.agent.database.QueryResult
import kotlinx.serialization.Serializable

/**
 * ChatDB Task - Input for the Text2SQL Agent
 * 
 * Contains the natural language query and database context
 */
@Serializable
data class ChatDBTask(
    /**
     * Natural language query from user
     * e.g., "Show me the top 10 customers by total order amount"
     */
    val query: String,
    
    /**
     * Database schema for context (optional, will be fetched if not provided)
     */
    val schema: DatabaseSchema? = null,
    
    /**
     * Additional context or constraints
     * e.g., "Only consider orders from 2024"
     */
    val additionalContext: String = "",
    
    /**
     * Maximum number of rows to return
     */
    val maxRows: Int = 100,
    
    /**
     * Whether to generate visualization after query
     */
    val generateVisualization: Boolean = true
)

/**
 * ChatDB Result - Output from the Text2SQL Agent
 */
@Serializable
data class ChatDBResult(
    /**
     * Whether the query was successful
     */
    val success: Boolean,
    
    /**
     * Human-readable message
     */
    val message: String,
    
    /**
     * Generated SQL query
     */
    val generatedSql: String? = null,
    
    /**
     * Query execution result
     */
    val queryResult: QueryResult? = null,
    
    /**
     * Generated PlotDSL code for visualization (if applicable)
     */
    val plotDslCode: String? = null,
    
    /**
     * Number of revision attempts made
     */
    val revisionAttempts: Int = 0,
    
    /**
     * Errors encountered during execution
     */
    val errors: List<String> = emptyList(),
    
    /**
     * Metadata about the execution
     */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Schema Linking Result - Tables and columns relevant to the query
 */
@Serializable
data class SchemaLinkingResult(
    /**
     * Relevant table names
     */
    val relevantTables: List<String>,
    
    /**
     * Relevant column names (table.column format)
     */
    val relevantColumns: List<String>,
    
    /**
     * Keywords extracted from the query
     */
    val keywords: List<String>,
    
    /**
     * Confidence score (0.0 - 1.0)
     */
    val confidence: Double = 0.0
)
