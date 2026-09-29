package com.medqb.app.shared.di

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("No AppGraph provided")
}
