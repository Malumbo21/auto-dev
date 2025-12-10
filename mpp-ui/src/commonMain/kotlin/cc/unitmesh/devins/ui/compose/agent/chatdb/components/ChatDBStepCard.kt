package cc.unitmesh.devins.ui.compose.agent.chatdb.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
                details["totalTables"]?.let {
                    DetailRow("Total Tables", it.toString())
                }
                details["tables"]?.let { tables ->
                    if (tables is List<*>) {
                        DetailRow("Tables", tables.joinToString(", "))
                    }
                }
            }
            ChatDBStepType.SCHEMA_LINKING -> {
                details["relevantTables"]?.let { tables ->
                    if (tables is List<*>) {
                        DetailRow("Relevant Tables", tables.joinToString(", "))
                    }
                }
                details["keywords"]?.let { keywords ->
                    if (keywords is List<*>) {
                        DetailRow("Keywords", keywords.joinToString(", "))
                    }
                }
            }
            ChatDBStepType.GENERATE_SQL, ChatDBStepType.REVISE_SQL -> {
                details["sql"]?.let { sql ->
                    CodeBlock(code = sql.toString(), language = "sql")
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
                details["rowCount"]?.let {
                    DetailRow("Rows Returned", it.toString())
                }
                details["columns"]?.let { columns ->
                    if (columns is List<*>) {
                        DetailRow("Columns", columns.joinToString(", "))
                    }
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

