package com.medqb.app.shared

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.metadata
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.modules.polymorphic
import coil3.compose.setSingletonImageLoaderFactory
import com.medqb.app.shared.data.MediaDescription
import com.medqb.app.shared.di.LocalAppGraph
import com.medqb.app.shared.domain.AppIntent
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.ui.screens.FilterPane
import com.medqb.app.shared.orchestration.RequestedFilterPane
import com.medqb.app.shared.orchestration.rememberAppWorkflow
import com.medqb.app.shared.ui.theme.AppTheme
import com.medqb.app.shared.ui.theme.Inset
import com.medqb.app.shared.ui.theme.Spacing
import com.medqb.app.shared.ui.entry.DatabaseSelectionEntry
import com.medqb.app.shared.ui.entry.FilterEntry
import com.medqb.app.shared.ui.entry.HtmlViewerEntry
import com.medqb.app.shared.ui.entry.MediaViewerEntry
import com.medqb.app.shared.ui.entry.QuizEntry
import com.medqb.app.shared.ui.entry.SettingsEntry
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val START_DESTINATION: MedQBRoutes = MedQBRoutes.DatabaseSelection

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(baseClass = NavKey::class) {
            subclass(serializer = MedQBRoutes.DatabaseSelection.serializer())
            subclass(serializer = MedQBRoutes.Filter.serializer())
            subclass(serializer = MedQBRoutes.Quiz.serializer())
            subclass(serializer = MedQBRoutes.Settings.serializer())
            subclass(serializer = MedQBRoutes.MediaViewer.serializer())
            subclass(serializer = MedQBRoutes.HtmlViewer.serializer())
        }
    }
}

@Composable
fun App() {
    var coilInstalled by remember { mutableStateOf(false) }
    if (!coilInstalled) {
        setSingletonImageLoaderFactory { context ->
            generateImageLoader(context)
        }
        SideEffect {
            coilInstalled = true
        }
    }

    AppTheme {
        val scope = rememberCoroutineScope()
        val graph = LocalAppGraph.current

        val appShutdownScope = remember {
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            )
        }
        DisposableEffect(graph.userDataManager) {
            onDispose {
                appShutdownScope.launch {
                    graph.userDataManager.close()
                    graph.activeDatabaseHolder.closeDatabase()
                }
            }
        }

        val backStack = rememberNavBackStack(navConfig, START_DESTINATION)
        val navigator = remember(backStack) { AppNavigator(backStack) }
        val workflow = rememberAppWorkflow(
            workflowCoordinator = graph.workflowCoordinator,
            filterStateHolder = graph.filterStateHolder,
            userDataManager = graph.userDataManager,
        )

        val mediaDescriptionsFlow = remember { MutableStateFlow<Map<String, MediaDescription>>(emptyMap()) }
        val snackbarHostState = remember { SnackbarHostState() }

        val navigateToMediaViewer = remember(scope, graph.mediaNavigationCoordinator, mediaDescriptionsFlow, navigator) {
            { files: List<String>, index: Int ->
                scope.launch {
                    val request = graph.mediaNavigationCoordinator.resolveMediaViewerRequest(files, index)
                    if (request != null) {
                        mediaDescriptionsFlow.value = request.mediaDescriptions
                        navigator.navigateTo(request.route)
                    }
                }
            }
        }

        val mediaHandler = remember(navigateToMediaViewer, navigator) {
            MediaHandler(
                onOpenMedia = { files, index -> navigateToMediaViewer(files, index) },
                onOpenHtml = { fileName ->
                    navigator.navigateTo(MedQBRoutes.HtmlViewer(fileName = fileName))
                }
            )
        }

        LaunchedEffect(graph.appIntentDispatcher, graph.snackbarDispatcher) {
            launch {
                graph.appIntentDispatcher.intents.collect { intent ->
                    when (intent) {
                        is AppIntent.OpenHtmlFile -> {
                            navigator.navigateTo(MedQBRoutes.HtmlViewer(fileName = intent.fileName))
                        }
                        is AppIntent.OpenMedia -> {
                            navigateToMediaViewer(intent.urls, intent.startIndex)
                        }
                        is AppIntent.NavigateToDatabaseSelection -> {
                            navigator.popToDatabaseSelection()
                            scope.launch { graph.activeDatabaseHolder.closeDatabase() }
                            workflow.onDatabaseSelectionRequested()
                        }
                    }
                }
            }
            launch {
                graph.snackbarDispatcher.messages.collect { snackbarMessage ->
                    when (snackbarMessage) {
                        is SnackbarMessage.Simple -> {
                            snackbarHostState.showSnackbar(snackbarMessage.message)
                        }
                        is SnackbarMessage.Action -> {
                            val result = snackbarHostState.showSnackbar(
                                message = snackbarMessage.message,
                                actionLabel = snackbarMessage.actionLabel,
                                duration = snackbarMessage.duration,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                snackbarMessage.onActionPerformed()
                            }
                        }
                    }
                }
            }
        }

        val returnQuizToFilter: () -> Unit = remember(workflow, navigator) {
            {
                val targetPane = workflow.onQuizReturn()
                val paneName = when (targetPane) {
                    RequestedFilterPane.Filters -> FilterPane.Filters.name
                    RequestedFilterPane.History -> FilterPane.History.name
                    null -> null
                }
                navigator.returnQuizToFilter(paneName)
            }
        }

        val entryProvider = remember(
            graph,
            workflow,
            navigator,
            mediaHandler,
            mediaDescriptionsFlow,
            returnQuizToFilter,
        ) {
            entryProvider<NavKey> {
                entry<MedQBRoutes.DatabaseSelection> {
                    DatabaseSelectionEntry(
                        graph = graph,
                        workflow = workflow,
                        navigator = navigator,
                    )
                }

                entry<MedQBRoutes.Filter> { route ->
                    FilterEntry(
                        route = route,
                        graph = graph,
                        workflow = workflow,
                        navigator = navigator,
                        snackbarHostState = snackbarHostState,
                    )
                }

                entry<MedQBRoutes.Quiz> { route ->
                    QuizEntry(
                        route = route,
                        graph = graph,
                        workflow = workflow,
                        navigator = navigator,
                        mediaHandler = mediaHandler,
                        onReturnToFilter = returnQuizToFilter,
                    )
                }

                entry<MedQBRoutes.Settings> {
                    SettingsEntry(
                        graph = graph,
                        navigator = navigator,
                    )
                }

                entry<MedQBRoutes.MediaViewer>(
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            fadeIn() togetherWith fadeOut()
                        }
                        put(NavDisplay.PopTransitionKey) {
                            fadeIn() togetherWith fadeOut()
                        }
                        put(NavDisplay.PredictivePopTransitionKey) {
                            fadeIn() togetherWith fadeOut()
                        }
                    }
                ) { key ->
                    MediaViewerEntry(
                        key = key,
                        graph = graph,
                        navigator = navigator,
                        mediaHandler = mediaHandler,
                        mediaDescriptionsFlow = mediaDescriptionsFlow,
                    )
                }

                entry<MedQBRoutes.HtmlViewer> { key ->
                    HtmlViewerEntry(
                        key = key,
                        graph = graph,
                        navigator = navigator,
                        mediaHandler = mediaHandler,
                    )
                }
            }
        }

        Box {
            @OptIn(ExperimentalSharedTransitionApi::class)
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            if (navigator.currentRoute is MedQBRoutes.Quiz) {
                                returnQuizToFilter()
                            } else {
                                navigator.navigateBack()
                            }
                        },
                        entryProvider = entryProvider,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator()
                        ),
                        sharedTransitionScope = this@SharedTransitionLayout,
                        transitionSpec = {
                            slideInHorizontally(initialOffsetX = { it }) togetherWith
                                slideOutHorizontally(targetOffsetX = { -it })
                        },
                        popTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                slideOutHorizontally(targetOffsetX = { it })
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                slideOutHorizontally(targetOffsetX = { it })
                        }
                    )
                }
            }

            val isFilterRoute by remember(backStack) {
                derivedStateOf { navigator.currentRoute is MedQBRoutes.Filter }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = Inset.Medium,
                        end = Inset.Medium,
                        bottom = if (isFilterRoute) 80.dp else Spacing.MediumSmall,
                    ),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            )
        }
    }
}
