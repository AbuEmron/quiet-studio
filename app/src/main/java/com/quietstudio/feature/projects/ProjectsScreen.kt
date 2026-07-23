package com.quietstudio.feature.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.ui.components.QuietCard
import com.quietstudio.ui.components.QuietTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val versionsFor by viewModel.versionsFor.collectAsStateWithLifecycle()
    var newFolder by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 86.dp), // clear the floating bottom nav
    ) {
        QuietTopBar("Projects", onBack) {
            IconButton(onClick = { newFolder = true }) {
                Icon(
                    Icons.Rounded.CreateNewFolder, "New folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = viewModel.query, onValueChange = viewModel::setSearch,
            placeholder = { Text("Search titles and tags…") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            singleLine = true,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = viewModel.folderId == null,
                    onClick = { viewModel.setFolder(null) },
                    label = { Text("All") },
                )
            }
            items(folders, key = { it.id }) { f ->
                FilterChip(
                    selected = viewModel.folderId == f.id,
                    onClick = { viewModel.setFolder(f.id) },
                    label = { Text(f.name) },
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            items(projects, key = { it.id }) { p ->
                QuietCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    onClick = { onOpen(p.id) },
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(
                                p.title, style = MaterialTheme.typography.titleMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
                                    .format(Date(p.updatedAt)) +
                                    (if (p.tags.isEmpty()) "" else "  ·  " + p.tags.joinToString(" ")),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.showVersions(p.id) }) {
                            Icon(
                                Icons.Rounded.History, "Versions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        IconButton(onClick = { viewModel.duplicate(p.id) }) {
                            Icon(
                                Icons.Rounded.ContentCopy, "Duplicate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        IconButton(onClick = { viewModel.delete(p.id) }) {
                            Icon(
                                Icons.Rounded.Delete, "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.createFolder(name)
                    newFolder = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Cancel") } },
        )
    }

    versionsFor?.let { (projectId, versions) ->
        AlertDialog(
            onDismissRequest = viewModel::hideVersions,
            title = { Text("Version history") },
            text = {
                LazyColumn {
                    items(versions, key = { it.versionId }) { v ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.restore(projectId, v) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                "${v.label} · " + SimpleDateFormat("MMM d HH:mm:ss", Locale.getDefault())
                                    .format(Date(v.createdAt)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::hideVersions) { Text("Close") } },
        )
    }
}
