package com.quietstudio.feature.export

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.ui.components.QuietCard
import com.quietstudio.ui.components.QuietTopBar
import com.quietstudio.ui.theme.Highlight
import com.quietstudio.ui.theme.Danger
import java.io.File

@Composable
fun ExportQueueScreen(
    onBack: () -> Unit,
    viewModel: ExportQueueViewModel = hiltViewModel(),
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        QuietTopBar("Exports", onBack) {
            TextButton(onClick = viewModel::clearFinished) { Text("Clear done") }
        }

        if (jobs.isEmpty()) {
            Text(
                "Renders queue here and finish in the background — you can keep editing " +
                    "or leave the app entirely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }

        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            items(jobs, key = { it.id }) { job ->
                QuietCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (job.state) {
                                    "DONE" -> Icons.Rounded.CheckCircle
                                    "FAILED" -> Icons.Rounded.ErrorOutline
                                    "RENDERING" -> Icons.Rounded.Movie
                                    else -> Icons.Rounded.HourglassEmpty
                                },
                                null,
                                tint = when (job.state) {
                                    "DONE" -> Highlight
                                    "FAILED" -> Danger
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(job.projectTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when (job.state) {
                                        "RENDERING" -> "Rendering · ${(job.progress * 100).toInt()}%"
                                        "DONE" -> "Finished"
                                        "FAILED" -> job.error ?: "Failed"
                                        else -> "Queued"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (job.state == "DONE" && job.outputPath != null) {
                                IconButton(onClick = {
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider",
                                        File(job.outputPath!!),
                                    )
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            },
                                            "Share video",
                                        )
                                    )
                                }) {
                                    Icon(Icons.Rounded.IosShare, "Share", tint = Highlight)
                                }
                            }
                        }
                        if (job.state == "RENDERING") {
                            LinearProgressIndicator(
                                progress = { job.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
