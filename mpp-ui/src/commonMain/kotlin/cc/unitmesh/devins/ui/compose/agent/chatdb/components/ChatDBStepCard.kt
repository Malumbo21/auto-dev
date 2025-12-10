package cc.unitmesh.devins.ui.compose.agent.chatdb.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.agent.render.ChatDBStepStatus
import cc.unitmesh.agent.render.ChatDBStepType
import cc.unitmesh.agent.render.TimelineItem

/**
 * Composable for rendering a ChatDB execution step card.
 * Displays step status, title, and expandable details.
 */
@Composable
fun ChatDBStepCard(
    step: TimelineItem.ChatDBStepItem,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(step.status == ChatDBStepStatus.ERROR) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp)
        ) {
            // Header row with icon, title, and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Expand/collapse icon
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Step icon
                    Text(
                        text = step.stepType.icon,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    // Step title
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status indicator
                StepStatusBadge(status = step.status)
            }

            // Expandable details section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // Error message if present
                    step.error?.let { error ->
                        Text(
                            text = "Error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // Details
                    if (step.details.isNotEmpty()) {
                        StepDetails(details = step.details, stepType = step.stepType)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepStatusBadge(status: ChatDBStepStatus) {
    val (color, text) = when (status) {
        ChatDBStepStatus.PENDING -> MaterialTheme.colorScheme.outline to "Pending"
        ChatDBStepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary to "In Progress"
        ChatDBStepStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary to "Success"
        ChatDBStepStatus.WARNING -> MaterialTheme.colorScheme.secondary to "Warning"
        ChatDBStepStatus.ERROR -> MaterialTheme.colorScheme.error to "Error"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StepDetails(details: Map<String, Any>, stepType: ChatDBStepType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (stepType) {
            ChatDBStepType.FETCH_SCHEMA -> {
                // Single database mode
                details["databaseName"]?.let { dbName ->
                    DetailRow("Database", dbName.toString())
                }
                details["totalTables"]?.let {
                    DetailRow("Total Tables", it.toString())
                }

                // Multi-database mode - show databases list
                @Suppress("UNCHECKED_CAST")
                val databases = details["databases"] as? List<Map<String, Any>>
                if (databases != null && databases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connected Databases",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    databases.forEach { dbInfo ->
                        DatabaseInfoCard(dbInfo)
                    }
                }

                // Show table schema cards (single database mode)
                @Suppress("UNCHECKED_CAST")
                val tableSchemas = details["tableSchemas"] as? List<Map<String, Any>>
                if (tableSchemas != null && tableSchemas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tables",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    tableSchemas.forEach { tableInfo ->
                        TableSchemaCard(tableInfo)
                    }
                } else if (databases == null) {
                    // Fallback to simple table list
                    details["tables"]?.let { tables ->
                        if (tables is List<*>) {
                            DetailRow("Tables", tables.joinToString(", "))
                        }
                    }
                }
            }

            ChatDBStepType.SCHEMA_LINKING -> {
                // Multi-database mode - show analyzed databases
                @Suppress("UNCHECKED_CAST")
                val databasesAnalyzed = details["databasesAnalyzed"] as? List<String>
                if (databasesAnalyzed != null && databasesAnalyzed.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Databases Analyzed:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        databasesAnalyzed.forEach { dbName ->
                            KeywordChip(dbName)
                        }
                    }
                }

                // Show schema context preview
                details["schemaContext"]?.let { context ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Schema Context",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = context.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 10
                        )
                    }
                }

                // Show keywords (single database mode)
                details["keywords"]?.let { keywords ->
                    if (keywords is List<*> && keywords.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Keywords:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            keywords.forEach { keyword ->
                                KeywordChip(keyword.toString())
                            }
                        }
                    }
                }

                // Show relevant table schemas
                @Suppress("UNCHECKED_CAST")
                val relevantTableSchemas = details["relevantTableSchemas"] as? List<Map<String, Any>>
                if (relevantTableSchemas != null && relevantTableSchemas.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Relevant Tables",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    relevantTableSchemas.forEach { tableInfo ->
                        TableSchemaCard(tableInfo, highlightRelevant = true)
                    }
                } else if (databasesAnalyzed == null) {
                    // Fallback
                    details["relevantTables"]?.let { tables ->
                        if (tables is List<*>) {
                            DetailRow("Relevant Tables", tables.joinToString(", "))
                        }
                    }
                }
            }

            ChatDBStepType.GENERATE_SQL, ChatDBStepType.REVISE_SQL -> {
                // Multi-database mode - show target databases
                @Suppress("UNCHECKED_CAST")
                val targetDatabases = details["targetDatabases"] as? List<String>
                if (targetDatabases != null && targetDatabases.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Target Databases:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        targetDatabases.forEach { dbName ->
                            KeywordChip(dbName)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Multi-database mode - show SQL blocks
                @Suppress("UNCHECKED_CAST")
                val sqlBlocks = details["sqlBlocks"] as? List<Map<String, Any>>
                if (sqlBlocks != null && sqlBlocks.isNotEmpty()) {
                    sqlBlocks.forEach { block ->
                        val database = block["database"]?.toString() ?: "Unknown"
                        val sql = block["sql"]?.toString() ?: ""
                        Text(
                            text = "Database: $database",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        CodeBlock(code = sql, language = "sql")
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    // Single database mode
                    details["sql"]?.let { sql ->
                        CodeBlock(code = sql.toString(), language = "sql")
                    }
                }
            }

            ChatDBStepType.VALIDATE_SQL -> {
                details["errorType"]?.let {
                    DetailRow("Error Type", it.toString())
                }
                details["errors"]?.let { errors ->
                    if (errors is List<*>) {
                        Column {
                            Text(
                                text = "Errors:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            errors.forEach { error ->
                                Text(
                                    text = "• $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            ChatDBStepType.EXECUTE_SQL -> {
                // Multi-database mode - show database name
                details["database"]?.let { dbName ->
                    DetailRow("Database", dbName.toString())
                }

                // Show SQL that was executed
                details["sql"]?.let { sql ->
                    CodeBlock(code = sql.toString(), language = "sql")
                }

                // Show result summary
                details["rowCount"]?.let {
                    DetailRow("Rows Returned", it.toString())
                }

                // Show data preview table
                @Suppress("UNCHECKED_CAST")
                val columns = details["columns"] as? List<String>
                @Suppress("UNCHECKED_CAST")
                val previewRows = details["previewRows"] as? List<List<String>>

                if (columns != null && previewRows != null && previewRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Data Preview",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DataPreviewTable(columns = columns, rows = previewRows)

                    val totalRows = (details["rowCount"] as? Int) ?: previewRows.size
                    if (totalRows > previewRows.size) {
                        Text(
                            text = "Showing ${previewRows.size} of $totalRows rows",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else if (columns != null) {
                    DetailRow("Columns", columns.joinToString(", "))
                }
            }

            ChatDBStepType.FINAL_RESULT -> {
                // Multi-database mode - show databases queried
                @Suppress("UNCHECKED_CAST")
                val databases = details["databases"] as? List<String>
                if (databases != null && databases.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Databases Queried:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        databases.forEach { dbName ->
                            KeywordChip(dbName)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Show final SQL (single database mode)
                details["sql"]?.let { sql ->
                    CodeBlock(code = sql.toString(), language = "sql")
                }

                // Show result summary
                details["totalRows"]?.let {
                    DetailRow("Total Rows", it.toString())
                }
                details["rowCount"]?.let {
                    if (details["totalRows"] == null) {
                        DetailRow("Total Rows", it.toString())
                    }
                }
                details["revisionAttempts"]?.let { attempts ->
                    if ((attempts as? Int ?: 0) > 0) {
                        DetailRow("Revision Attempts", attempts.toString())
                    }
                }

                // Show errors if any
                @Suppress("UNCHECKED_CAST")
                val errors = details["errors"] as? List<String>
                if (errors != null && errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Errors",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    errors.forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // Show data preview
                @Suppress("UNCHECKED_CAST")
                val columns = details["columns"] as? List<String>
                @Suppress("UNCHECKED_CAST")
                val previewRows = details["previewRows"] as? List<List<String>>

                if (columns != null && previewRows != null && previewRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Query Results",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DataPreviewTable(columns = columns, rows = previewRows)
                }
            }

            else -> {
                // Generic detail rendering
                details.forEach { (key, value) ->
                    DetailRow(key, value.toString())
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CodeBlock(code: String, language: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = language.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/**
 * Card displaying database information for multi-database mode
 */
@Composable
private fun DatabaseInfoCard(dbInfo: Map<String, Any>) {
    var isExpanded by remember { mutableStateOf(false) }
    val name = dbInfo["name"]?.toString() ?: "Unknown"
    val displayName = dbInfo["displayName"]?.toString() ?: name
    val tableCount = dbInfo["tableCount"]?.toString() ?: "0"

    @Suppress("UNCHECKED_CAST")
    val tables = dbInfo["tables"] as? List<String> ?: emptyList()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown
                            else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "🗄️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (name != displayName) {
                        Text(
                            text = " ($name)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "$tableCount tables",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (tables.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 24.dp)
                    ) {
                        Text(
                            text = tables.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card displaying table schema information
 */
@Composable
private fun TableSchemaCard(
    tableInfo: Map<String, Any>,
    highlightRelevant: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(highlightRelevant) }
    val tableName = tableInfo["name"]?.toString() ?: "Unknown"
    val comment = tableInfo["comment"]?.toString()

    @Suppress("UNCHECKED_CAST")
    val columns = tableInfo["columns"] as? List<Map<String, Any>> ?: emptyList()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
        color = if (highlightRelevant)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown
                            else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "📋",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tableName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${columns.size} columns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            comment?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (columns.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 8.dp)
                    ) {
                        columns.forEach { col ->
                            ColumnInfoRow(col)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Row displaying column information
 */
@Composable
private fun ColumnInfoRow(columnInfo: Map<String, Any>) {
    val name = columnInfo["name"]?.toString() ?: ""
    val type = columnInfo["type"]?.toString() ?: ""
    val isPrimaryKey = columnInfo["isPrimaryKey"] as? Boolean ?: false
    val isForeignKey = columnInfo["isForeignKey"] as? Boolean ?: false
    val nullable = columnInfo["nullable"] as? Boolean ?: true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Column name with key indicators
            if (isPrimaryKey) {
                Text(text = "🔑", fontSize = 10.sp)
                Spacer(modifier = Modifier.width(2.dp))
            } else if (isForeignKey) {
                Text(text = "🔗", fontSize = 10.sp)
                Spacer(modifier = Modifier.width(2.dp))
            }

            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = if (isPrimaryKey) FontWeight.Bold else FontWeight.Normal,
                color = if (isPrimaryKey) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = type,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            // Nullable indicator
            if (!nullable) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "NOT NULL",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Keyword chip for schema linking
 */
@Composable
private fun KeywordChip(keyword: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Text(
            text = keyword,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Data preview table for query results
 */
@Composable
private fun DataPreviewTable(
    columns: List<String>,
    rows: List<List<String>>,
    maxDisplayRows: Int = 10
) {
    val displayRows = rows.take(maxDisplayRows)
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
        ) {
            Column {
                // Header row
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(1.dp)
                ) {
                    columns.forEach { column ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 80.dp, max = 200.dp)
                                .border(
                                    width = 0.5.dp,
                                    color = borderColor
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = column,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Data rows
                displayRows.forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (rowIndex % 2 == 0)
                                    MaterialTheme.colorScheme.surface
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .padding(1.dp)
                    ) {
                        row.forEachIndexed { colIndex, cell ->
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 80.dp, max = 200.dp)
                                    .border(
                                        width = 0.5.dp,
                                        color = borderColor.copy(alpha = 0.5f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cell.ifEmpty { "NULL" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = if (cell.isEmpty())
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
