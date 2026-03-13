package com.nexopos.desktop.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.nexopos.desktop.core.settings.KeyboardShortcutsManager
import com.nexopos.desktop.core.settings.ShortcutAction
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Keyboard Shortcuts Settings Screen
 * Allows users to customize keyboard shortcuts without recompiling
 */
@Composable
fun KeyboardShortcutsSettings(
    onBack: () -> Unit
) {
    val manager = koinInject<KeyboardShortcutsManager>()
    val shortcuts by manager.shortcuts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var editingAction by remember { mutableStateOf<ShortcutAction?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Keyboard Shortcuts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset to Defaults")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Click on a shortcut to change its key binding. Changes are saved automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            items(ShortcutAction.values().toList()) { action ->
                val shortcut = shortcuts[action]
                if (shortcut != null) {
                    ShortcutCard(
                        shortcut = shortcut,
                        isEditing = editingAction == action,
                        onClick = { editingAction = action }
                    )
                }
            }
        }
    }
    
    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Reset to Defaults") },
            text = { Text("Are you sure you want to reset all keyboard shortcuts to their default values?") },
            confirmButton = {
                TextButton(onClick = {
                    manager.resetToDefaults()
                    showResetDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Shortcuts reset to defaults")
                    }
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Edit shortcut dialog
    editingAction?.let { action ->
        EditShortcutDialog(
            action = action,
            currentShortcut = shortcuts[action]!!,
            onDismiss = { editingAction = null },
            onSave = { newKey ->
                val result = manager.updateShortcut(action, newKey)
                result.fold(
                    onSuccess = {
                        editingAction = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Shortcut updated successfully")
                        }
                    },
                    onFailure = { error ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Error: ${error.message}")
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun ShortcutCard(
    shortcut: com.nexopos.desktop.core.settings.KeyboardShortcut,
    isEditing: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = shortcut.action.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Surface(
                color = if (isEditing) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = shortcut.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isEditing) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun EditShortcutDialog(
    action: ShortcutAction,
    currentShortcut: com.nexopos.desktop.core.settings.KeyboardShortcut,
    onDismiss: () -> Unit,
    onSave: (Key) -> Unit
) {
    var pressedKey by remember { mutableStateOf<Key?>(null) }
    val focusRequester = remember { FocusRequester() }
    
    // Request focus when dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null) },
        title = { Text("Change Shortcut") },
        text = {
            // Focusable box to capture key events
            Box(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key != Key.Escape) {
                            pressedKey = event.key
                            println("[KeyboardShortcuts] Captured key: ${event.key}")
                            true
                        } else false
                    }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Current: ${currentShortcut.label}")
                    Text(currentShortcut.description)
                    
                    Divider()
                    
                    Text(
                        "Press a key to assign it to this action...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (pressedKey != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = "New: ${pressedKey.toString()}",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    pressedKey?.let { onSave(it) }
                },
                enabled = pressedKey != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
