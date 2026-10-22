package com.music.spotui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.Surface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Aalap's pull-to-refresh, the same on every screen: the page follows your finger
 * (elastic, half the distance), springs straight back the moment you let go, and only
 * the small spinner stays while [onRefresh] runs (at most [maxWaitMs], so it never
 * hangs). [trigger] starts a refresh from elsewhere (e.g. double-tapping Home).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AalapRefresh(
    modifier: Modifier = Modifier,
    trigger: Flow<Unit>? = null,
    maxWaitMs: Long = 5_000,
    onRefresh: suspend () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    if (state.isRefreshing) {
        LaunchedEffect(Unit) {
            runCatching { withTimeoutOrNull(maxWaitMs) { onRefresh() } }
            state.endRefresh()
        }
    }
    if (trigger != null) LaunchedEffect(trigger) { trigger.collect { state.startRefresh() } }

    val stretch = remember { Animatable(0f) }
    LaunchedEffect(state) {
        snapshotFlow { state.verticalOffset to state.isRefreshing }.collect { (offset, busy) ->
            if (offset > 0f && !busy) stretch.snapTo(offset * 0.5f)
            else stretch.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
    }
    // A short pull refreshes; once the refresh is fully pulled, any further pull goes to
    // the page underneath, so detail pages can still be pulled down to close.
    val connection = remember(state) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            val inner = state.nestedScrollConnection
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) =
                inner.onPreScroll(available, source)
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y > 0f && state.verticalOffset >= state.positionalThreshold * 1.15f) return androidx.compose.ui.geometry.Offset.Zero
                return inner.onPostScroll(consumed, available, source)
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity) = inner.onPreFling(available)
            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) = inner.onPostFling(consumed, available)
        }
    }
    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = stretch.value }) { content() }
        PullToRefreshContainer(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = Surface,
            contentColor = Saffron,
        )
    }
}
