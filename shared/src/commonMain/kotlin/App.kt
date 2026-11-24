package com.medqb.app.shared

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import coil3.compose.setSingletonImageLoaderFactory
import com.medqb.app.shared.di.LocalAppGraph
import com.medqb.app.shared.domain.AppIntent
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBNavConfig
import com.medqb.app.shared.navigation.rememberMedQBNavEntries
import com.medqb.app.shared.orchestration.rememberAppWorkflow
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import com.medqb.app.shared.ui.theme.AppTheme
import com.medqb.app.shared.ui.theme.exitSpatialSpec
import com.medqb.app.shared.ui.media.MediaHandler
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.LocalSnackbarBottomPadding
import com.medqb.app.shared.ui.SnackbarClearanceRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val START_DESTINATION: MedQBRoutes = MedQBRoutes.DatabaseSelection

@Composable
fun App() {
    val graph = LocalAppGraph.current

    setSingletonImageLoaderFactory { context ->
        generateImageLoader(context)
    }

    AppTheme {
        val scope = rememberCoroutineScope()
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        val motionScheme = MaterialTheme.motionScheme

        val backStack = rememberNavBackStack(MedQBNavConfig, START_DESTINATION)
        val navigator = remember(backStack) { AppNavigator(backStack) }
        val workflow = rememberAppWorkflow(
            workflowCoordinator = graph.workflowCoordinator,
            filterStateHolder = graph.filterStateHolder,
            userDataManager = graph.userDataManager,
        )

        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarClearanceRegistry = remember { SnackbarClearanceRegistry() }

        LaunchedEffect(navigator) {
            snapshotFlow { navigator.currentRoute }
                .distinctUntilChanged()
                .drop(1)
                .collect { snackbarHostState.currentSnackbarData?.dismiss() }
        }

        val navigateToMediaViewer: (List<String>, Int) -> Unit = remember(scope, graph.mediaNavigationCoordinator, navigator) {
            var mediaNavJob: Job? = null
            { files, index ->
                val originRoute = navigator.currentRoute
                mediaNavJob?.cancel()
                mediaNavJob = scope.launch {
                    val route = graph.mediaNavigationCoordinator.resolveMediaViewerRoute(files, index)
                    if (route != null && navigator.currentRoute == originRoute) {
                        navigator.navigateTo(route)
                    }
                }
            }
        }

        val mediaHandler = remember(navigateToMediaViewer, navigator) {
            MediaHandler(
                onOpenMedia = navigateToMediaViewer,
                onOpenHtml = { fileName ->
                    navigator.navigateTo(MedQBRoutes.HtmlViewer(fileName = fileName))
                }
            )
        }

        val currentNavigator by rememberUpdatedState(navigator)
        val currentWorkflow by rememberUpdatedState(workflow)
        val currentNavigateToMediaViewer by rememberUpdatedState(navigateToMediaViewer)

        LaunchedEffect(graph.appIntentDispatcher, graph.snackbarDispatcher) {
            launch {
                graph.appIntentDispatcher.intents.collect { intent ->
                    when (intent) {
                        is AppIntent.OpenHtmlFile -> {
                            currentNavigator.navigateTo(MedQBRoutes.HtmlViewer(fileName = intent.fileName))
                        }
                        is AppIntent.OpenMedia -> {
                            currentNavigateToMediaViewer(intent.urls, intent.startIndex)
                        }
                        is AppIntent.NavigateToDatabaseSelection -> {
                            currentNavigator.popToDatabaseSelection()
                            graph.activeDatabaseHolder.closeDatabase()
                            currentWorkflow.onDatabaseSelectionRequested()
                        }
                    }
                }
            }
            launch {
                var currentSnackbarJob: Job? = null
                graph.snackbarDispatcher.messages.collect { snackbarMessage ->
                    currentSnackbarJob?.cancel()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    currentSnackbarJob = launch {
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
        }

        val entryProvider = rememberMedQBNavEntries(
            graph = graph,
            workflow = workflow,
            navigator = navigator,
            mediaHandler = mediaHandler,
            snackbarHostState = snackbarHostState,
        )

        val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
        val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
        val entryDecorators = remember(saveableDecorator, viewModelStoreDecorator) {
            listOf(saveableDecorator, viewModelStoreDecorator)
        }

        ProvideAdaptiveLayoutInfo(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                @OptIn(ExperimentalSharedTransitionApi::class)
                SharedTransitionLayout {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalSnackbarBottomPadding provides snackbarClearanceRegistry,
                    ) {
                        val popTransition: AnimatedContentTransitionScope<Scene<NavKey>>.(swipeEdge: Int) -> ContentTransform = remember(motionScheme) {
                            { swipeEdge ->
                                val isRightEdge = swipeEdge == NavigationEvent.EDGE_RIGHT
                                (slideInHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec(),
                                    initialOffsetX = if (isRightEdge) ({ it / 4 }) else ({ -it / 4 })
                                ) + scaleIn(
                                    animationSpec = motionScheme.defaultSpatialSpec(),
                                    initialScale = 0.96f
                                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec(), initialAlpha = 0f)) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = exitSpatialSpec(),
                                        targetOffsetX = if (isRightEdge) ({ -it }) else ({ it })
                                    ) + scaleOut(
                                        animationSpec = exitSpatialSpec(),
                                        targetScale = 0.96f
                                    ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec(), targetAlpha = 0f))
                            }
                        }

                        val onBack: () -> Unit = remember(navigator) {
                            { navigator.navigateBack() }
                        }

                        val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = remember(motionScheme) {
                            {
                                (slideInHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec(),
                                    initialOffsetX = { it }
                                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec(), initialAlpha = 0f)) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = exitSpatialSpec(),
                                        targetOffsetX = { -it / 4 }
                                    ) + scaleOut(
                                        animationSpec = exitSpatialSpec(),
                                        targetScale = 0.96f
                                    ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec(), targetAlpha = 0f))
                            }
                        }

                        val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = remember(popTransition) {
                            { popTransition(NavigationEvent.EDGE_LEFT) }
                        }

                        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(swipeEdge: Int) -> ContentTransform = remember(popTransition) {
                            { swipeEdge -> popTransition(swipeEdge) }
                        }

                        NavDisplay(
                            backStack = backStack,
                            onBack = onBack,
                            entryProvider = entryProvider,
                            entryDecorators = entryDecorators,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            transitionSpec = transitionSpec,
                            popTransitionSpec = popTransitionSpec,
                            predictivePopTransitionSpec = predictivePopTransitionSpec,
                        )
                    }
                }

                AppSnackbarHost(
                    snackbarHostState = snackbarHostState,
                    clearanceRegistry = snackbarClearanceRegistry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun AppSnackbarHost(
    snackbarHostState: SnackbarHostState,
    clearanceRegistry: SnackbarClearanceRegistry,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.padding(
            start = 16.dp,
            end = 16.dp,
            bottom = clearanceRegistry.bottomPadding,
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
