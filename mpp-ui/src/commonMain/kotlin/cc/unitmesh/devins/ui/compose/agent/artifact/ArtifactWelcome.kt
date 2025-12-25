package cc.unitmesh.devins.ui.compose.agent.artifact

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * Predefined artifact scenarios for quick start
 */
data class ArtifactScenario(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val prompt: String,
    val category: ScenarioCategory
)

enum class ScenarioCategory(val displayName: String) {
    APP("Applications"),
    WIDGET("Widgets"),
    GAME("Games"),
    DATA("Data Visualization")
}

/**
 * Predefined scenarios for artifact generation
 */
object ArtifactScenarios {
    val scenarios = listOf(
        ArtifactScenario(
            id = "dashboard",
            name = "Analytics Dashboard",
            description = "Interactive dashboard with stat cards and CSS charts",
            icon = Icons.Default.Dashboard,
            prompt = """
                Create an interactive dashboard with:
                1. A header showing "Analytics Dashboard" 
                2. 3 stat cards showing: Users (1,234), Revenue ($45,678), Orders (567)
                3. A simple bar chart using CSS (no libraries) showing monthly data
                4. Dark theme with modern styling
                5. Console.log the current time when the page loads
            """.trimIndent(),
            category = ScenarioCategory.DATA
        ),
        ArtifactScenario(
            id = "todolist",
            name = "Todo List",
            description = "Full-featured todo app with local storage",
            icon = Icons.Default.ChecklistRtl,
            prompt = """
                Create a todo list app with:
                1. Input field to add new todos
                2. List of todos with checkbox to mark complete
                3. Button to delete todos
                4. Local storage persistence
                5. Show count of remaining todos
                6. Console.log when items are added/completed/deleted
            """.trimIndent(),
            category = ScenarioCategory.APP
        ),
        ArtifactScenario(
            id = "calculator",
            name = "Calculator",
            description = "Functional calculator with responsive grid",
            icon = Icons.Default.Calculate,
            prompt = """
                Create a calculator widget with:
                1. Display showing current input and result
                2. Number buttons 0-9
                3. Operation buttons: +, -, *, /, =, C
                4. Responsive grid layout
                5. Handle decimal numbers
                6. Console.log each calculation
            """.trimIndent(),
            category = ScenarioCategory.WIDGET
        ),
        ArtifactScenario(
            id = "pomodoro",
            name = "Pomodoro Timer",
            description = "Work/break timer with circular progress",
            icon = Icons.Default.Timer,
            prompt = """
                Create a Pomodoro timer with:
                1. 25-minute work sessions, 5-minute breaks
                2. Circular progress indicator
                3. Session counter
                4. Start/Pause/Skip buttons
                5. Different colors for work vs break
                6. Browser notification when session ends
                7. Console.log session transitions
            """.trimIndent(),
            category = ScenarioCategory.APP
        ),
        ArtifactScenario(
            id = "weather",
            name = "Weather Card",
            description = "Glassmorphism weather widget",
            icon = Icons.Default.WbSunny,
            prompt = """
                Create a weather card widget that shows:
                1. City name input field
                2. Current temperature display (use mock data)
                3. Weather icon (sun/cloud/rain using CSS/emoji)
                4. 5-day forecast preview
                5. Toggle between Celsius and Fahrenheit
                6. Modern glassmorphism design
                7. Console.log temperature conversions
            """.trimIndent(),
            category = ScenarioCategory.WIDGET
        ),
        ArtifactScenario(
            id = "game",
            name = "Tic-Tac-Toe",
            description = "Classic game with win detection",
            icon = Icons.Default.Games,
            prompt = """
                Create a Tic-Tac-Toe game with:
                1. 3x3 grid board
                2. Two players: X and O
                3. Turn indicator
                4. Win detection with highlighting
                5. Reset button
                6. Score tracking
                7. Console.log game moves and results
            """.trimIndent(),
            category = ScenarioCategory.GAME
        ),
        ArtifactScenario(
            id = "kanban",
            name = "Kanban Board",
            description = "Drag & drop task management board",
            icon = Icons.Default.ViewColumn,
            prompt = """
                Create a Kanban board with:
                1. Three columns: Todo, In Progress, Done
                2. Draggable task cards between columns
                3. Add new task button for each column
                4. Delete task functionality
                5. Task count per column
                6. Clean, modern styling with shadows
                7. Console.log when tasks are moved
            """.trimIndent(),
            category = ScenarioCategory.APP
        ),
        ArtifactScenario(
            id = "markdown",
            name = "Markdown Editor",
            description = "Split-view markdown editor with preview",
            icon = Icons.Default.Edit,
            prompt = """
                Create a Markdown editor with:
                1. Split view: editor on left, preview on right
                2. Support basic markdown: headers, bold, italic, lists, code blocks
                3. Toolbar with formatting buttons
                4. Live preview as you type
                5. Copy rendered HTML button
                6. Dark theme compatible
                7. Console.log on format actions
            """.trimIndent(),
            category = ScenarioCategory.APP
        )
    )

    fun getByCategory(): Map<ScenarioCategory, List<ArtifactScenario>> {
        return scenarios.groupBy { it.category }
    }

    fun getById(id: String): ArtifactScenario? {
        return scenarios.find { it.id == id }
    }
}

/**
 * Welcome screen for ArtifactPage with predefined scenarios
 */
@Composable
fun ArtifactWelcome(
    onSelectScenario: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        item {
            WelcomeHeader()
        }

        // Scenario categories
        ArtifactScenarios.getByCategory().forEach { (category, scenarios) ->
            item {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(scenarios.chunked(2)) { rowScenarios ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowScenarios.forEach { scenario ->
                        ScenarioCard(
                            scenario = scenario,
                            onClick = { onSelectScenario(scenario.prompt) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty space if odd number
                    if (rowScenarios.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Footer hint
        item {
            Text(
                text = "Or describe what you want to create in the input below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun WelcomeHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        // Gradient icon container
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AutoDevComposeIcons.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Artifact Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create interactive HTML applications with AI",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Choose a template below or describe your own",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScenarioCard(
    scenario: ArtifactScenario,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon container
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = scenario.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = scenario.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = scenario.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

