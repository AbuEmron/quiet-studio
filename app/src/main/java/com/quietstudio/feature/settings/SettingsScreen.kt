package com.quietstudio.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.ui.components.QuietCard
import com.quietstudio.ui.components.QuietTopBar
import com.quietstudio.ui.components.SectionLabel
import com.quietstudio.ui.theme.Highlight

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importModel(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(bottom = 86.dp) // clear the floating bottom nav
            .verticalScroll(rememberScrollState()),
    ) {
        QuietTopBar("Settings", onBack)

        SectionLabel("Transcription")
        QuietCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CheckCircle, null,
                        tint = if (ui.modelInstalled) Highlight else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (ui.modelInstalled) "  Speech model ready — subtitles generate on-device"
                        else "  No speech model yet",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Everything runs locally, with word-level timing. The model file is the " +
                        "only thing Quiet Studio ever needs from outside — import one you " +
                        "already have, or fetch one once.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )

                ui.rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.label + if (row.recommended) "  ·  Recommended" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (row.recommended) Highlight
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                row.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            row.active -> Text(
                                "Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = Highlight,
                            )
                            row.installed -> OutlinedButton(onClick = { viewModel.useModel(row.fileName) }) {
                                Text("Use")
                            }
                            row.spec != null -> OutlinedButton(
                                onClick = { viewModel.download(row.spec) },
                                enabled = ui.downloadingFile == null,
                            ) {
                                if (ui.downloadingFile == row.fileName && ui.downloadProgress != null) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Text("  ${(ui.downloadProgress!! * 100).toInt()}%")
                                } else {
                                    Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                                    Text("  ~${row.approxSizeMb} MB")
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(16.dp))
                    Text("  Import a model file (offline)")
                }

                ui.error?.let {
                    Text(
                        it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        SectionLabel("Privacy")
        QuietCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    "No accounts, no analytics, no ads",
                    "Recordings, projects and exports never leave this device",
                    "Bring your own music — imports are copied to this device and stay here",
                    "Cloud sync will only ever exist as an explicit opt-in module",
                ).forEach {
                    Text(
                        "·  $it",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }

        SectionLabel("About")
        Text(
            "Quiet Studio 1.3 — a private production room for one creator.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
