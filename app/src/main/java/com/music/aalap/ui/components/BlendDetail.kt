package com.music.aalap.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.aalap.data.recommendation.Blend
import com.music.aalap.data.recommendation.BlendScore
import com.music.aalap.data.recommendation.BlendSync
import com.music.aalap.di.CurrentSongState
import com.music.aalap.ui.share.ShareCards
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SaffronGold
import com.music.aalap.ui.theme.Surface
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary

private val Teal = Color(0xFF4FC3D9)

/** A Blend as a card (Library → Blends, the Blend screen): score, names, today's state. */
@Composable
fun BlendCardRow(b: BlendSync.Shared, onClick: () -> Unit) {
    val context = LocalContext.current
    val me = Blend.myId(context)
    val score = b.score(me)
    val p = b.points(me)
    val today = b.day >= BlendSync.today()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(ShareCards.blendColor(score)).copy(alpha = 0.55f), Surface)))
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            ScoreRing(score / 100f, Modifier.fillMaxSize(), stroke = 5.dp.value * 2.6f)
            Text("$score", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("You + ${b.friendName(me)}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${Blend.band(score).first} · ${b.songs.size} songs", color = TextMuted, fontSize = 12.sp)
            Text(
                (if (today) "Fresh today" else "Refreshes when Aalap opens") + " · this week: you ${p.mine}, ${b.friendName(me)} ${p.theirs}" +
                    if (b.synced) "" else " · this phone only",
                color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The ring around a score, filled to [fraction]. */
@Composable
private fun ScoreRing(fraction: Float, modifier: Modifier, stroke: Float = 18f) {
    Canvas(modifier) {
        val inset = stroke / 2
        val sz = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
        drawArc(Brush.sweepGradient(listOf(Saffron, SaffronGold, Saffron)), -90f, 360f * fraction.coerceIn(0f, 1f), false,
            Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Library → Blends → a Blend: score, how it's moved, how to raise it, and today's 30 songs. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun BlendDetail(pairId: String, state: CurrentSongState, onClose: () -> Unit) {
    val context = LocalContext.current
    val all by BlendSync.blends.collectAsState()
    val syncStatus by BlendSync.status.collectAsState()
    val b = all.firstOrNull { it.pairId == pairId }
    if (b == null) { LaunchedEffect(Unit) { onClose() }; return }
    val me = Blend.myId(context)
    val friendId = b.friend(me)
    val friend = b.friendName(me)
    val today = BlendSync.today()
    val match = b.match(me)
    val points = b.points(me, today)
    val score = BlendScore.score(match, points.total)
    val weekAgo = b.history[today - 7] ?: b.history.filterKeys { it < today }.minByOrNull { it.key }?.value ?: match
    val heardToday = b.listened[today].orEmpty()
    var confirmRemove by remember { mutableStateOf(false) }
    // Your friend's listening comes in while you look (every minute).
    LaunchedEffect(pairId) { while (true) { BlendSync.pull(context); kotlinx.coroutines.delay(60_000) } }

    // The ring fills from where it was to the score.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(score) { shown.animateTo(score.toFloat(), tween(1200, easing = FastOutSlowInEasing)) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PullToClose(onClose, Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(ShareCards.blendColor(score)), AppBackground, AppBackground)))) {
            LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("‹", color = TextPrimary, fontSize = 34.sp, modifier = Modifier.clip(CircleShape).clickable { onClose() }.padding(horizontal = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("BLEND", color = SaffronGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("You + $friend", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = "Delete Blend", tint = TextMuted,
                            modifier = Modifier.clip(CircleShape).clickable { confirmRemove = true }.padding(10.dp).size(22.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                        val live = b.synced && syncStatus == "live"
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) Color(0xFF4CD964) else TextMuted))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                live -> "Live on both phones · same 30 songs"
                                b.synced -> "Shared · reconnecting"
                                else -> "On this phone only (send your new code to share it live)"
                            }, color = TextMuted, fontSize = 12.sp,
                        )
                    }
                }

                // Score
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                            ScoreRing(shown.value / 100f, Modifier.fillMaxSize(), stroke = 34f)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${shown.value.toInt()}", color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Bold)
                                Text("BLEND SCORE", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                            }
                        }
                        Text(Blend.band(score).first, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        val delta = score - weekAgo
                        Text(
                            "Taste match $match" + (if (score > match) " + ${score - match} from listening" else "") +
                                (if (delta != 0) " · ${if (delta > 0) "▲" else "▼"} ${kotlin.math.abs(delta)} this week" else ""),
                            color = if (delta > 0) SaffronGold else TextMuted, fontSize = 13.sp,
                        )
                    }
                }

                // How it's moved
                item {
                    Section("Your score this week")
                    ScoreChart(b.history + (today to score), today, Modifier.fillMaxWidth().height(150.dp))
                }

                // This week
                item {
                    Section("This week")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat("You heard", "${points.mine}", Saffron, Modifier.weight(1f))
                        Stat("$friend heard", "${points.theirs}", Teal, Modifier.weight(1f))
                        Stat("Both heard", "${points.together}", SaffronGold, Modifier.weight(1f))
                    }
                }

                // How to raise it
                item {
                    Section("Raise your Blend score")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp)) {
                        val next = BlendScore.nextBand(score)
                        val need = next?.let { BlendScore.pointsFor(match, it) }?.minus(points.total)?.coerceAtLeast(1)
                        if (next != null && need != null) {
                            Text("${need} more to reach ${Blend.band(next).first}", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("About ${(need + 1) / 2} songs each, or fewer if you both hear the same ones", color = TextMuted, fontSize = 12.sp)
                        } else Text("Top of the scale: keep listening to stay here", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Tip("●", Saffron, "Each Blend song you hear (30 s+) adds a point, for both of you")
                        Tip("●", SaffronGold, "A song you've BOTH heard adds 2 more: play $friend's picks")
                        Tip("●", Teal, "Points count for 7 days, so a little every day keeps it up")
                        Tip("●", TextMuted, "Your taste match updates by itself as you both listen")
                    }
                }

                // Songs
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Today's ${b.songs.size}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (b.day >= today) "Made today · new ones tomorrow" else "New ones when Aalap opens", color = TextMuted, fontSize = 12.sp)
                        }
                        SmallPill("Shuffle", false) {
                            com.music.aalap.di.SmartPlay.playAll(context, state, b.songs.map { it.toModel() }.shuffled(), "Blend · You + $friend"); onClose(); BlendUi.shown.value = false
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallPill("Play", true) {
                            com.music.aalap.di.SmartPlay.playAll(context, state, b.songs.map { it.toModel() }, "Blend · You + $friend"); onClose(); BlendUi.shown.value = false
                        }
                    }
                }
                itemsIndexed(b.songs) { i, s ->
                    val heardByMe = s.key in heardToday[me].orEmpty() || b.listened.values.any { s.key in it[me].orEmpty() }
                    val heardByThem = b.listened.values.any { s.key in it[friendId].orEmpty() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            val list = b.songs.map { it.toModel() }
                            com.music.aalap.di.SmartPlay.playAll(context, state, list, "Blend · You + $friend", i)
                        }.padding(vertical = 6.dp),
                    ) {
                        GlideImage(model = s.cover, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceHigh))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artists, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val (tag, col) = when (s.by) { me -> "Yours" to Saffron; friendId -> "$friend's" to Teal; else -> "New" to SaffronGold }
                        Text(tag, color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                        Spacer(Modifier.width(8.dp))
                        // Who has heard it this week.
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (heardByMe) Saffron else Color.White.copy(alpha = 0.12f)))
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (heardByThem) Teal else Color.White.copy(alpha = 0.12f)))
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Saffron)); Text("  you heard it   ", color = TextMuted, fontSize = 11.sp)
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Teal)); Text("  $friend heard it", color = TextMuted, fontSize = 11.sp)
                    }
                    Text("Delete Blend", color = Color(0xFFE57373), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 26.dp).clip(RoundedCornerShape(50))
                            .border(1.dp, Color(0xFFE57373).copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { confirmRemove = true }.padding(horizontal = 18.dp, vertical = 10.dp))
                    Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.CenterEnd) { Signature(size = 12f) }
                }
            }
        }
        if (confirmRemove) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = com.music.aalap.ui.theme.Surface,
            title = { Text("Delete your Blend with $friend?", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text("It comes off your phone with its songs and score. $friend keeps theirs. To blend again, swap codes once more.",
                    color = TextMuted, fontSize = 14.sp)
            },
            confirmButton = {
                Text("Delete", color = Color(0xFFE57373), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmRemove = false; BlendSync.remove(context, b.pairId); onClose() }.padding(horizontal = 14.dp, vertical = 10.dp))
            },
            dismissButton = {
                Text("Keep", color = TextPrimary, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirmRemove = false }.padding(horizontal = 14.dp, vertical = 10.dp))
            },
        )
    }
}

/**
 * The score this week, a bar per day (Monday to today and on): each day's score as a bar
 * with its number, today's bar bright; days still to come are faint stubs, so the card is
 * never an empty space. Underneath: where it started, its best, and today.
 */
@Composable
private fun ScoreChart(history: Map<Long, Int>, today: Long, modifier: Modifier) {
    val monday = today - (java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1)
    val week = (monday until monday + 7).toList()
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    val known = history.filterKeys { it <= today }
    val lo = ((known.values.minOrNull() ?: 50) - 12).coerceAtLeast(0)
    val hi = ((known.values.maxOrNull() ?: 70) + 4).coerceAtMost(100)
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { k, d ->
                val v = known[d]
                val isToday = d == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (v != null) Text("$v", color = if (isToday) SaffronGold else TextMuted, fontSize = 10.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    val frac = if (v == null) 0.06f else ((v - lo).toFloat() / (hi - lo).coerceAtLeast(1)).coerceIn(0.12f, 1f)
                    Box(Modifier.padding(top = 3.dp).width(14.dp).fillMaxHeight(frac * 0.78f).clip(RoundedCornerShape(7.dp))
                        .background(when { v == null -> Color.White.copy(alpha = 0.08f); isToday -> Saffron; else -> Saffron.copy(alpha = 0.45f) }))
                    Text(names[k], color = if (isToday) TextPrimary else TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        val first = known.minByOrNull { it.key }?.value
        Text(
            listOfNotNull(first?.let { "Started at $it" }, known.values.maxOrNull()?.let { "best $it" }, known[today]?.let { "today $it" }).joinToString(" · ")
                .ifBlank { "Your score shows here day by day" },
            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Section(title: String) =
    Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Surface).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Tip(dot: String, color: Color, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(dot, color = color, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun SmallPill(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(label, color = if (primary) OnSaffron else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (primary) Saffron else SurfaceHigh).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp))
}
