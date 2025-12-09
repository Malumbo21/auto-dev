package cc.unitmesh.agent.database

import kotlinx.coroutines.runBlocking

/**
 * Text2SQL Agent database connection usage example
 */
fun main(args: Array<String>) {
    // Example 1: Create MySQL/MariaDB connection
    val mariadbConfig = DatabaseConfig(
        host = "localhost",
        port = 3306,
        databaseName = "afs",
        username = "root",
        password = "prisma",
        dialect = "MariaDB"
    )

    runBlocking {
        try {
            // Create connection
            val connection = createDatabaseConnection(mariadbConfig)

            // Get database schema
            println("=== Get Database Schema ===")
            val schema = connection.getSchema()
            println(schema.getDescription())

            // Execute query
            println("\n=== Execute Query ===")
            val result = connection.executeQuery("SELECT * FROM article LIMIT 10")
            println("Row count: ${result.rowCount}")
            println("Columns: ${result.columns}")
            println("\nTable view:")
            println(result.toTableString())
            println("\nCSV view:")
            println(result.toCsvString())

            // Get table information
            println("\n=== Get Table Info ===")
            println("users table exists: ${connection.tableExists("article")}")
            println("users table row count: ${connection.getTableRowCount("article")}")

            // Close connection
            connection.close()
            println("\nConnection closed")
        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        }
    }
}
