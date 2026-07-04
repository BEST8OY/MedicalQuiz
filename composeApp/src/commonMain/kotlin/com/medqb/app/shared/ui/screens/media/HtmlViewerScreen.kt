package com.medqb.app.shared.ui.screens.media

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.richtext.RichText
import com.medqb.app.shared.ui.theme.ElementSize
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HtmlViewerScreen(
    fileName: String,
    htmlContent: String?,
    fileExists: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current

    val contentBoundsModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && !isLoading && !htmlContent.isNullOrBlank() && fileExists) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "html_$fileName"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

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
                        style = MaterialTheme.typography.titleMediumEmphasized,
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

                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Inset.Lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = Inset.Lg, vertical = Spacing.MdLg),
                                verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(ElementSize.IconXl))
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
                            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(contentBoundsModifier),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shadowElevation = 1.dp, // M3 Level 1 elevation
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = Spacing.Md, vertical = Spacing.MdLg),
                            ) {
                                Text(
                                    text = "Interactive HTML document",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(Spacing.Sm))
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
            .padding(horizontal = Inset.Lg),
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
                    .padding(horizontal = Spacing.MdLg, vertical = Spacing.Sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(Spacing.Xs))
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
