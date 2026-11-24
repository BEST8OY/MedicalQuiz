package com.medqb.app.shared.ui.state

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Reusable [Saver] for `Set<Long>` state persisted across configuration changes.
 */
val LongSetSaver: Saver<Set<Long>, Any> = listSaver<Set<Long>, Long>(
    save = { it.toList() },
    restore = { it.toSet() },
)

/**
 * Reusable [Saver] for `Set<E>` of enum values persisted by enum entry name.
 */
inline fun <reified E : Enum<E>> enumSetSaver(): Saver<Set<E>, Any> = listSaver<Set<E>, String>(
    save = { set -> set.map { it.name } },
    restore = { names ->
        names.mapNotNull { name -> runCatching { enumValueOf<E>(name) }.getOrNull() }.toSet()
    },
)
