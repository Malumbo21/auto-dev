package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * WebEdit Toolbar with URL input and navigation controls
 */
@Composable
fun WebEditToolbar(
    currentUrl: String,
    inputUrl: String,
    isLoading: Boolean,
    isSelectionMode: Boolean,
    showDOMSidebar: Boolean,
    onUrlChange: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleDOMSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Back to main app button
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close WebEdit",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Navigation buttons
            IconButton(
                onClick = { /* Go back in browser history */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            IconButton(
                onClick = { /* Go forward in browser history */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            IconButton(
                onClick = onReload,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reload",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // URL input field
            OutlinedTextField(
                value = inputUrl,
                onValueChange = onUrlChange,
                modifier = Modifier.weight(1f).height(40.dp),
                placeholder = { Text("Enter URL", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onNavigate(inputUrl) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            
            // Selection mode toggle
            IconButton(
                onClick = onToggleSelectionMode,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isSelectionMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Toggle Selection Mode",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // DOM sidebar toggle
            IconButton(
                onClick = onToggleDOMSidebar,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (showDOMSidebar) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = "Toggle DOM Sidebar",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

