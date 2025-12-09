package cc.unitmesh.agent.database

import kotlin.test.*

/**
 * Database connection unit tests
 */
class DatabaseConnectionTest {

    @Test
    fun testQueryResultFormatting() {
        val result = QueryResult(
            columns = listOf("id", "name", "email"),
            rows = listOf(
                listOf("1", "Alice", "alice@example.com"),
                listOf("2", "Bob", "bob@example.com"),
                listOf("3", "Charlie", "charlie@example.com")
            ),
            rowCount = 3
        )
        
        assertEquals(3, result.rowCount)
        assertFalse(result.isEmpty())
        assertEquals(3, result.columns.size)
        assertEquals(3, result.rows.size)
        
        val csv = result.toCsvString()
        assertTrue(csv.contains("id,name,email"))
        assertTrue(csv.contains("Alice"))
        
        val table = result.toTableString()
        assertTrue(table.contains("id"))
        assertTrue(table.contains("Alice"))
        
        println("Query result table:")
        println(result.toTableString())
    }

    @Test
    fun testEmptyResult() {
        val result = QueryResult(
            columns = listOf("id", "name"),
            rows = emptyList(),
            rowCount = 0
        )
        
        assertTrue(result.isEmpty())
        assertEquals(0, result.rowCount)
        
        val csvStr = result.toCsvString()
        assertEquals("No results", csvStr)
    }

    @Test
    fun testColumnSchemaDescription() {
        val column = ColumnSchema(
            name = "user_id",
            type = "INT",
            nullable = false,
            comment = "User unique identifier",
            isPrimaryKey = true,
            defaultValue = "0"
        )

        val desc = column.getDescription()
        assertTrue(desc.contains("user_id"))
        assertTrue(desc.contains("INT"))
        assertTrue(desc.contains("PRIMARY KEY"))
        assertTrue(desc.contains("NOT NULL"))
        assertTrue(desc.contains("Comment: User unique identifier"))
        
        println("Column description: $desc")
    }

    @Test
    fun testTableSchemaDescription() {
        val table = TableSchema(
            name = "orders",
            columns = listOf(
                ColumnSchema("id", "INT", nullable = false, isPrimaryKey = true),
                ColumnSchema("user_id", "INT", nullable = false),
                ColumnSchema("total", "DECIMAL", nullable = false),
                ColumnSchema("created_at", "TIMESTAMP", nullable = false)
            ),
            comment = "Orders table"
        )

        val desc = table.getDescription()
        assertTrue(desc.contains("Table: orders"))
        assertTrue(desc.contains("Comment: Orders table"))
        assertTrue(desc.contains("id"))
        assertTrue(desc.contains("user_id"))
        
        println("Table description:")
        println(desc)
    }

    @Test
    fun testDatabaseConfigJdbcUrl() {
        val mysqlConfig = DatabaseConfig(
            host = "localhost",
            port = 3306,
            databaseName = "test_db",
            username = "root",
            password = "password",
            dialect = "MySQL"
        )

        val url = mysqlConfig.getJdbcUrl()
        assertTrue(url.contains("jdbc:mysql://"))
        assertTrue(url.contains("localhost"))
        assertTrue(url.contains("3306"))
        assertTrue(url.contains("test_db"))
        
        println("MySQL JDBC URL: $url")
        
        val mariadbConfig = DatabaseConfig(
            host = "192.168.1.1",
            port = 3307,
            databaseName = "mariadb_test",
            username = "admin",
            password = "secret",
            dialect = "MariaDB"
        )

        val mariadbUrl = mariadbConfig.getJdbcUrl()
        assertTrue(mariadbUrl.contains("jdbc:mysql://"))
        assertTrue(mariadbUrl.contains("192.168.1.1"))
        
        println("MariaDB JDBC URL: $mariadbUrl")
    }

    @Test
    fun testDatabaseSchema() {
        val schema = DatabaseSchema(
            databaseName = "test_db",
            tables = listOf(
                TableSchema(
                    name = "users",
                    columns = listOf(
                        ColumnSchema("id", "INT", nullable = false, isPrimaryKey = true),
                        ColumnSchema("name", "VARCHAR", nullable = false),
                        ColumnSchema("email", "VARCHAR", nullable = true)
                    ),
                    comment = "Users table"
                ),
                TableSchema(
                    name = "products",
                    columns = listOf(
                        ColumnSchema("id", "INT", nullable = false, isPrimaryKey = true),
                        ColumnSchema("title", "VARCHAR", nullable = false),
                        ColumnSchema("price", "DECIMAL", nullable = false),
                        ColumnSchema("user_id", "INT", nullable = false, isForeignKey = true)
                    ),
                    comment = "Products table"
                )
            )
        )
        
        assertEquals(2, schema.tables.size)
        assertEquals("users", schema.tables[0].name)
        assertEquals("products", schema.tables[1].name)
        
        val usersTable = schema.getTable("users")
        assertNotNull(usersTable)
        assertEquals(3, usersTable.columns.size)
        
        val description = schema.getDescription()
        assertTrue(description.contains("Database: test_db"))
        assertTrue(description.contains("Table: users"))
        
        println("Database schema description:")
        println(description)
    }

    @Test
    fun testDatabaseException() {
        val connException = DatabaseException.connectionFailed("Connection timeout")
        assertTrue(connException.message!!.contains("connection failed"))
        
        val queryException = DatabaseException.queryFailed(
            "SELECT * FROM users",
            "Column 'name' not found"
        )
        assertTrue(queryException.message!!.contains("Query failed"))
        assertTrue(queryException.message!!.contains("SELECT"))
    }
}
