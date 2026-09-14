package com.medqb.app.shared.platform

import androidx.compose.runtime.Composable

/**
 * Platform-aware back gesture handler for in-screen states (e.g., zoomed media, selection modes).
 *
 * On Android, integrates unconditionally with Navigation 3's [androidx.navigationevent.compose.NavigationBackHandler].
 * On Desktop, operates as a no-op since desktop platforms do not have a system back gesture.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
