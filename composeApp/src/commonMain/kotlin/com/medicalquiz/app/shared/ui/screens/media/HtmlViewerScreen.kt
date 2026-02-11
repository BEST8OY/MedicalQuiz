package com.medicalquiz.app.shared.ui.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medicalquiz.app.shared.platform.FileSystemHelper
import com.medicalquiz.app.shared.platform.StorageProvider
import com.medicalquiz.app.shared.ui.richtext.RichText
import com.medicalquiz.app.shared.utils.HtmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HtmlViewerScreen(
    fileName: String,
    onBack: () -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
) {
    // PlatformBackHandler removed - let NavDisplay handle predictive back gesture

    val filePath = remember(fileName) {
        "${StorageProvider.getAppStorageDirectory()}/media/$fileName"
    }

    val htmlContent by produceState<String?>(initialValue = null, filePath) {
        value = withContext(Dispatchers.IO) {
            val raw = FileSystemHelper.readText(filePath)
            raw?.let(HtmlUtils::sanitizeForRichText)
        }
    }

    val fileExists by produceState(initialValue = true, filePath) {
        value = withContext(Dispatchers.IO) { FileSystemHelper.exists(filePath) }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )

            when {
                !fileExists -> {
                    EmptyHtmlState(
                        title = "File not found",
                        supportingText = fileName,
                    )
                }

                htmlContent == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                                Text(
                                    text = "Loading HTML content…",
                                    style = MaterialTheme.typography.bodyLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                htmlContent.isNullOrBlank() -> {
                    EmptyHtmlState(
                        title = "Empty file",
                        supportingText = "This HTML document has no content.",
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp,
                            shadowElevation = 1.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
                            ) {
                                Text(
                                    text = "Interactive HTML document",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                RichText(
                                    html = htmlContent.orEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                    onLinkClick = onLinkClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyHtmlState(title: String, supportingText: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
