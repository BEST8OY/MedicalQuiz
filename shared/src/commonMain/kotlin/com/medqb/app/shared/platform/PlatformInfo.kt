package com.medqb.app.shared.platform

enum class PlatformKind {
    Android,
    Desktop,
}

expect fun getPlatformKind(): PlatformKind
