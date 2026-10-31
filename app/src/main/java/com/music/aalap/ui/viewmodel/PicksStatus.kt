package com.music.aalap.ui.viewmodel

/**
 * What the Quick picks section shows. A refresh that fails or finds nothing keeps the picks
 * already on screen (they're still good songs); only with nothing to show does the section say
 * so and offer to try again, instead of disappearing. Pure (PicksStatusTest).
 */
enum class PicksStatus {
    LOADING, READY, EMPTY, FAILED;

    companion object {
        /** After a load: [shown] picks were on screen, [result] is what the load gave. */
        fun after(shown: Int, result: Result<List<*>>): PicksStatus = when {
            result.getOrNull()?.isNotEmpty() == true -> READY
            shown > 0 -> READY
            result.isFailure -> FAILED
            else -> EMPTY
        }

        /** Whether the load's result replaces what's on screen. */
        fun replaces(result: Result<List<*>>): Boolean = result.getOrNull()?.isNotEmpty() == true
    }
}
