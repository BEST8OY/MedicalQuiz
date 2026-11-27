package com.medqb.app.shared.ui.screens.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.ui.dialogs.components.MedQBModalBottomSheet
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.richtext.RichTextScaleProvider
import net.engawapg.lib.zoomable.ExperimentalZoomableApi
import net.engawapg.lib.zoomable.SnapBackZoomableOverlayHost

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalZoomableApi::class)
@Composable
internal fun ExplanationBottomSheet(
    description: MediaDescription,
    richTextScale: Float,
    onDismiss: () -> Unit,
    onLinkClick: ((String) -> Unit)?,
) {
    MedQBModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
    ) {
        // Scope a dedicated overlay host inside the modal sheet window so zoomed thumbnails
        // render above sheet content rather than behind the modal window.
        SnapBackZoomableOverlayHost(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = description.title.ifBlank { "Explanation" },
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                RichTextScaleProvider(proseScale = richTextScale) {
                    RichText(
                        html = description.description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        onLinkClick = onLinkClick,
                    )
                }
            }
        }
    }
}
