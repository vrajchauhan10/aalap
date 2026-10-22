package com.music.spotui.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.music.spotui.ui.components.MiniPlayer
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.Hairline
import com.music.spotui.ui.theme.OnSaffron
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.TextMuted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class NoRippleInteractionSource : MutableInteractionSource {

    override val interactions: Flow<Interaction> = emptyFlow()

    override suspend fun emit(interaction: Interaction) {}

    override fun tryEmit(interaction: Interaction) = true
}

@Composable
fun MainBottomNavigation(navController: NavHostController, bottomBarState: MutableState<Boolean>, bottomBarPlayerState : MutableState<Boolean>) {

    val navItems = listOf(
        Routes.Home,
        Routes.Search,
        Routes.Library
    )
    AnimatedVisibility(
        visible = bottomBarState.value,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        content = {
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, AppBackground)))
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {

                    AnimatedVisibility(
                        visible = bottomBarPlayerState.value,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it }),
                        content = {
                            MiniPlayer(navController)
                        }
                    )

                    val navStack by navController.currentBackStackEntryAsState()
                    val currentRoute = navStack?.destination?.route
                    val barShape = RoundedCornerShape(32.dp)

                    // Aalap's floating tab bar: a rounded capsule above the gesture area,
                    // where the active tab grows into a saffron pill with its label.
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 10.dp)
                            .fillMaxWidth()
                            .height(62.dp)
                            .shadow(16.dp, barShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(barShape)
                            .background(Surface)
                            .border(1.dp, Hairline, barShape),
                    ) {
                        navItems.forEach { item ->
                            NavTab(
                                item = item,
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (item == Routes.Home && currentRoute == Routes.Home.route) {
                                        val now = System.currentTimeMillis()
                                        if (now - HomeTabEvents.lastHomeTap < 450) {
                                            HomeTabEvents.refresh.tryEmit(Unit)
                                            HomeTabEvents.lastHomeTap = 0L
                                        } else HomeTabEvents.lastHomeTap = now
                                        return@NavTab
                                    }
                                    navController.navigate(item.route) {
                                        navController.graph.startDestinationRoute?.let {
                                            popUpTo(item.route)
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    )
}

/** Double-tapping Home while on Home: jump to the top and refresh the page. */
object HomeTabEvents {
    val refresh = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal var lastHomeTap = 0L
}

@Composable
private fun NavTab(item: Routes, selected: Boolean, onClick: () -> Unit) {
    val pill by animateColorAsState(if (selected) Saffron else Color.Transparent, label = "tabPill")
    val content by animateColorAsState(if (selected) OnSaffron else TextMuted, label = "tabContent")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .animateContentSize(),
    ) {
        Icon(
            painter = painterResource(id = item.icon),
            contentDescription = item.label,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(item.label, color = content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
