package com.medqb.app.shared

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import com.medqb.app.shared.domain.SnackbarEvent
import com.medqb.app.shared.domain.SnackbarMessage
import com.medqb.app.shared.navigation.MedQBRoutes
import com.medqb.app.shared.navigation.AppNavigator
import com.medqb.app.shared.navigation.MedQBNavConfig
import com.medqb.app.shared.navigation.PredictivePopFloatSpec
import com.medqb.app.shared.navigation.PredictivePopIntOffsetSpec
import com.medqb.app.shared.navigation.rememberMedQBNavEntries
import com.medqb.app.shared.orchestration.rememberAppWorkflow
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import com.medqb.app.shared.ui.theme.AppTheme
import com.medqb.app.shared.ui.theme.exitSpatialSpec
import com.medqb.app.shared.ui.LocalSharedTransitionScope
import com.medqb.app.shared.ui.LocalSnackbarBottomPadding
import com.medqb.app.shared.ui.SnackbarClearanceRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
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

        val navigateToMediaViewer: (List<String>, Int) -> Unit = remember(
            scope,
            graph.mediaNavigationCoordinator,
            navigator,
        ) {
            var mediaNavJob: Job? = null
            { files, index ->
                val originRoute = navigator.currentRoute
                mediaNavJob?.cancel()
                mediaNavJob = scope.launch {
                    val route = graph.mediaNavigationCoordinator.resolveMediaViewerRoute(files, index)
                    if (route != null && navigator.currentRoute == originRoute) {
                        navigator.navigateOrPopTo(route)
                    }
                }
            }
        }

        val openHtmlViewer: (String) -> Unit = remember(navigator) {
            { fileName ->
                navigator.navigateOrPopTo(MedQBRoutes.HtmlViewer(fileName = fileName))
            }
        }

        LaunchedEffect(
            graph.appIntentDispatcher,
            graph.snackbarDispatcher,
            navigator,
            workflow,
        ) {
            launch {
                graph.appIntentDispatcher.intents.collect { intent ->
                    when (intent) {
                        is AppIntent.NavigateToDatabaseSelection -> {
                            navigator.popToDatabaseSelection()
                            workflow.onDatabaseSelectionRequested()
                        }
                    }
                }
            }
            launch {
                graph.snackbarDispatcher.events.collectLatest { event ->
                    snackbarHostState.currentSnackbarData?.dismiss()
                    when (event) {
                        is SnackbarEvent.Dismiss -> Unit
                        is SnackbarEvent.Show -> when (val snackbarMessage = event.message) {
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
            onOpenMedia = navigateToMediaViewer,
            onOpenHtml = openHtmlViewer,
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
                        val layoutDirection = LocalLayoutDirection.current
                        val ltrSign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1

                        val onBack: () -> Unit = remember(navigator) {
                            { navigator.navigateBack() }
                        }

                        val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = remember(motionScheme, ltrSign) {
                            {
                                (slideInHorizontally(
                                    animationSpec = motionScheme.defaultSpatialSpec(),
                                    initialOffsetX = { it * ltrSign }
                                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = exitSpatialSpec(),
                                        targetOffsetX = { (-it / 4) * ltrSign }
                                    ) + scaleOut(
                                        animationSpec = exitSpatialSpec(),
                                        targetScale = 0.96f
                                    ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                            }
                        }

                        val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = remember(motionScheme, ltrSign) {
                            {
                                (slideInHorizontally(
                                    animationSpec = motionScheme.fastSpatialSpec(),
                                    initialOffsetX = { (-it / 4) * ltrSign }
                                ) + scaleIn(
                                    animationSpec = motionScheme.fastSpatialSpec(),
                                    initialScale = 0.96f
                                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = exitSpatialSpec(stiffness = Spring.StiffnessMedium),
                                        targetOffsetX = { it * ltrSign }
                                    ) + scaleOut(
                                        animationSpec = exitSpatialSpec(stiffness = Spring.StiffnessMedium),
                                        targetScale = 0.96f
                                    ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec()))
                            }
                        }

                        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(swipeEdge: Int) -> ContentTransform = remember {
                            { swipeEdge ->
                                val isRightEdge = swipeEdge == NavigationEvent.EDGE_RIGHT
                                (slideInHorizontally(
                                    animationSpec = PredictivePopIntOffsetSpec,
                                    initialOffsetX = if (isRightEdge) ({ it / 4 }) else ({ -it / 4 })
                                ) + scaleIn(
                                    animationSpec = PredictivePopFloatSpec,
                                    initialScale = 0.96f
                                ) + fadeIn(animationSpec = PredictivePopFloatSpec)) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = PredictivePopIntOffsetSpec,
                                        targetOffsetX = if (isRightEdge) ({ -it }) else ({ it })
                                    ) + scaleOut(
                                        animationSpec = PredictivePopFloatSpec,
                                        targetScale = 0.96f
                                    ) + fadeOut(animationSpec = PredictivePopFloatSpec))
                            }
                        }

                        NavDisplay(
                            backStack = backStack,
                            onBack = onBack,
                            entryProvider = entryProvider,
                            entryDecorators = entryDecorators,
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
