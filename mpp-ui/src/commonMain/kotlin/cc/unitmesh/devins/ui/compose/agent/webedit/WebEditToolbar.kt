package cc.unitmesh.devins.ui.compose.agent.webedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WebEdit Toolbar with URL input and navigation controls
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    onGoBack: () -> Unit = {},
    onGoForward: () -> Unit = {},
    onToggleSelectionMode: () -> Unit,
    onToggleDOMSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val urlFocusRequester = remember { FocusRequester() }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { keyEvent ->
                // Command+L (Mac) or Ctrl+L (Windows/Linux) to focus URL bar
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.L &&
                    (keyEvent.isMetaPressed || keyEvent.isCtrlPressed)
                ) {
                    urlFocusRequester.requestFocus()
                    true
                } else {
                    false
                }
            },
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
                onClick = onGoBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onGoForward,
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

            // URL input field - use BasicTextField for compact height
            BasicTextField(
                value = inputUrl,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .focusRequester(urlFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        // Handle Enter key to navigate
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                            onNavigate(inputUrl)
                            true
                        } else {
                            false
                        }
                    }
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp),
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onNavigate(inputUrl) }),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputUrl.isEmpty()) {
                            Text(
                                "Enter URL (e.g., https://www.xuiper.com) • Cmd/Ctrl+L to focus",
                                style = TextStyle(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
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

