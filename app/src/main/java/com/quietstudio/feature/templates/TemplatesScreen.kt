package com.quietstudio.feature.templates

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietstudio.ui.components.QuietCard
import com.quietstudio.ui.components.QuietTopBar

/**
 * One tap on a template starts a new recording that inherits the template's
 * entire look — visual, subtitle style, music, export settings.
 */
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onUseTemplate: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        QuietTopBar("Templates", onBack)

        if (templates.isEmpty()) {
            Text(
                "Save any project's style as a template from the editor's Export tab. " +
                    "One tap here then recreates that exact look around a fresh recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }

        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            items(templates, key = { it.first.id }) { (entity, content) ->
                QuietCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Style, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(entity.name, style = MaterialTheme.typography.titleMedium)
                            content?.let {
                                Text(
                                    "${it.visual.kind.lowercase()} · ${it.subtitleStyle.animation.lowercase()} captions",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = {
                            viewModel.useTemplate(entity.id)
                            onUseTemplate()
                        }) {
                            Icon(Icons.Rounded.Mic, null, Modifier.size(16.dp))
                            Text(" Use")
                        }
                        IconButton(onClick = { viewModel.delete(entity.id) }) {
                            Icon(
                                Icons.Rounded.Delete, "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
