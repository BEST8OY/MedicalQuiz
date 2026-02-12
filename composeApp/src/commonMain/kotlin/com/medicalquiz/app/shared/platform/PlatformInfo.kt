package com.medicalquiz.app.shared.platform

enum class PlatformKind {
    Android,
    Desktop,
}

expect fun getPlatformKind(): PlatformKind
