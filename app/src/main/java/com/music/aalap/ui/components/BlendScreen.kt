package com.music.aalap.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.aalap.R
import com.music.aalap.data.recommendation.Blend
import com.music.aalap.di.CurrentSongState
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SaffronGold
import com.music.aalap.ui.theme.Surface
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Opens the Blend screen from anywhere (Home, Settings, a code shared to Aalap). */
object BlendUi {
    val shown = MutableStateFlow(false)
    /** The Blend open in detail (its pair id), or null. */
    val detail = MutableStateFlow<String?>(null)
    /** A friend's message handed to Aalap (share sheet), to fill in. */
    val incoming = MutableStateFlow<String?>(null)

    fun open(code: String? = null) {
        if (code != null) incoming.value = code
        shown.value = true
    }
}

private fun clipboardText(context: Context): String = runCatching {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull().orEmpty()

/** The Blend screen, over the app when [BlendUi.shown]. */
@Composable
fun BlendHost(state: CurrentSongState) {
    val shown by BlendUi.shown.collectAsState()
    val detail by BlendUi.detail.collectAsState()
    if (shown) Dialog(
        onDismissRequest = { BlendUi.shown.value = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) { BlendScreen(state) { BlendUi.shown.value = false } }
    detail?.let { id -> BlendDetail(id, state) { BlendUi.detail.value = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlendScreen(state: CurrentSongState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Your code carries your shared Blend id, so wait for the sign-in (a few seconds at most).
    val sync by com.music.aalap.data.recommendation.BlendSync.status.collectAsState()
    val me by produceState<Blend.TasteCard?>(null, sync) {
        if (sync == "connecting") { kotlinx.coroutines.delay(6_000) }
        value = withContext(Dispatchers.IO) { runCatching { Blend.myCard(context) }.getOrNull() }
    }
    val incoming by BlendUi.incoming.collectAsState()
    var code by remember { mutableStateOf("") }
    // A friend's code from the share sheet, or already copied: filled in for you.
    LaunchedEffect(incoming) {
        // A code shared to Aalap wins; the clipboard only fills an empty field.
        val text = incoming ?: clipboardText(context).takeIf { code.isBlank() && it.contains(Blend.PREFIX) }
        val card = text?.let { Blend.decode(it) }
        if (card != null && card.id != Blend.myId(context)) code = text
        if (incoming != null) BlendUi.incoming.value = null
    }
    val friend = remember(code) { Blend.decode(code)?.takeIf { it.id.isBlank() || it.id != Blend.myId(context) } }
    val ownCode = remember(code) { Blend.decode(code)?.let { it.id.isNotBlank() && it.id == Blend.myId(context) } == true }
    var making by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Blend.Made?>(null) }
    val saved by com.music.aalap.data.recommendation.BlendSync.blends.collectAsState()

    PullToClose(onClose, Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3A1A3E), AppBackground, AppBackground)))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Blend", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Close", color = TextMuted, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClose() }.padding(12.dp))
            }
            Text("One playlist from two tastes, with a match score. No account, nothing uploaded: you swap taste codes.",
                color = TextMuted, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { MergingOrbs(Modifier.size(150.dp)) }

            // 1 · your code
            Card {
                Step("1", "Send your taste")
                Text(me?.let { "${it.name} · ${it.artists.take(3).joinToString(", ").ifBlank { "your favourites" }}" } ?: "Reading your taste…",
                    color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Copy code", primary = false, modifier = Modifier.weight(1f)) {
                        val card = me ?: return@Pill
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Aalap Blend", Blend.shareText(context, card)))
                        android.widget.Toast.makeText(context, "Copied: paste it in a chat to your friend", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    Pill("Share", primary = true, modifier = Modifier.weight(1f)) {
                        val card = me ?: return@Pill
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Blend.shareText(context, card)), "Send your Aalap taste"))
                    }
                }
                Text("Your friend copies your message and opens Aalap → Blend, or taps Share → Aalap. It fills in by itself.",
                    color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }

            // 2 · their code
            Card {
                Step("2", "Blend with a friend's code")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = it }, maxLines = 3,
                    placeholder = { Text("Paste their message here", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedBorderColor = Saffron, unfocusedBorderColor = SurfaceHigh),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        friend != null -> "✓ ${friend.name}'s taste: ${friend.artists.take(3).joinToString(", ")}"
                        ownCode -> "That's your own code: send it to a friend instead"
                        code.isNotBlank() -> "No Blend code found in that text"
                        else -> "Copy your friend's whole message; it's picked up when you open this screen."
                    },
                    color = if (friend != null) SaffronGold else TextMuted, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Paste", primary = false, modifier = Modifier.weight(1f)) {
                        val clip = clipboardText(context)
                        if (clip.isBlank()) android.widget.Toast.makeText(context, "Nothing copied yet", android.widget.Toast.LENGTH_SHORT).show() else code = clip
                    }
                    Pill(if (making) "Blending…" else "Make our Blend", primary = true, enabled = friend != null && !making, modifier = Modifier.weight(1.4f)) {
                        val f = friend ?: return@Pill
                        making = true
                        scope.launch {
                            val made = withContext(Dispatchers.IO) { runCatching { Blend.make(context, f) }.getOrNull() }
                            making = false
                            if (made != null && made.songs.isNotEmpty()) result = made
                            else android.widget.Toast.makeText(context, "Couldn't make the Blend, check the internet and try again", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // Saved Blends
            if (saved.isNotEmpty()) {
                Text("Your Blends", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp))
                Text("Also in Library → Blends. A fresh 30 every day, the same on both phones; listening to them raises your score.",
                    color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                saved.forEach { s -> BlendCardRow(s) { BlendUi.detail.value = s.pairId } }
            }
            Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp), contentAlignment = Alignment.CenterEnd) { Signature(size = 12f) }
        }

        if (making) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MergingOrbs(Modifier.size(180.dp))
                Text("Blending your tastes…", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    result?.let { made -> ScoreReveal(made, me?.name ?: "You", state) { result = null } }
}

/**
 * The score, counted up, over an animation for its band: two orbs circling far apart
 * (opposites attract), circles that drift together and overlap (common ground), a
 * beating heart with ripples (great match), two sound waves falling into step (music
 * twins), and a burst of rays and confetti (soulmates). Then save, play or share it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreReveal(made: Blend.Made, myName: String, state: CurrentSongState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val score = made.match.score
    val (band, blurb) = Blend.band(score)
    val count = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    var savedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        count.animateTo(score.toFloat(), tween(1800, easing = FastOutSlowInEasing))
        done = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val accent = Color(com.music.aalap.ui.share.ShareCards.blendColor(score))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(18.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(accent, Surface))).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("YOU + ${made.friend.name.uppercase()}", color = SaffronGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                BandAnimation(score, Modifier.fillMaxSize(), progress = count.value / score.coerceAtLeast(1))
                Text("${count.value.toInt()}%", color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Bold)
            }
            AnimatedVisibility(done, enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(band, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(blurb, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    if (made.match.sharedArtists.isNotEmpty()) {
                        Text("YOU BOTH LOVE", color = Saffron, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            made.match.sharedArtists.take(5).forEach {
                                Text(it, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                    Text("${made.songs.size} songs: yours, theirs and new ones for you both", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                    Spacer(Modifier.height(18.dp))
                    val scope = rememberCoroutineScope()
                    var saving by remember { mutableStateOf(false) }
                    Pill(when { savedId != null -> "Saved to Library → Blends ✓"; saving -> "Saving…"; else -> "Save Blend" },
                        primary = true, enabled = savedId == null && !saving, modifier = Modifier.fillMaxWidth()) {
                        saving = true
                        scope.launch {
                            savedId = withContext(Dispatchers.IO) { runCatching { com.music.aalap.data.recommendation.BlendSync.create(context, made) }.onFailure { android.util.Log.w("Blend", "save: $it") }.getOrNull() }
                            saving = false
                            if (savedId == null) android.widget.Toast.makeText(context, "Couldn't save the Blend, check the internet and try again", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Play", primary = false, modifier = Modifier.weight(1f)) {
                            com.music.aalap.di.SmartPlay.playAll(context, state, made.songs, "Blend · You + ${made.friend.name}")
                            onDismiss(); BlendUi.shown.value = false
                        }
                        Pill("Share score", primary = false, modifier = Modifier.weight(1f)) {
                            val bmp = com.music.aalap.ui.share.ShareCards.blendCard(context, myName, made.friend.name, score, band, blurb,
                                made.match.sharedArtists, made.songs.take(3).map { "${it.title} — ${it.singer.substringBefore(",")}" })
                            com.music.aalap.ui.share.ShareCards.share(context, bmp, "aalap-blend",
                                "Our Aalap Blend: $score% · $band! Blend with me too:\n\n" + Blend.shareText(context))
                        }
                    }
                }
            }
        }
    }
}

/** The animation for a score's band; [progress] 0..1 follows the count. */
@Composable
private fun BandAnimation(score: Int, modifier: Modifier, progress: Float) {
    val t by rememberInfiniteTransition(label = "band").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t")
    val gold = SaffronGold; val saffron = Saffron; val teal = Color(0xFF4FC3D9); val pink = Color(0xFFFF6F91)
    Canvas(modifier) {
        val c = center; val r = size.minDimension / 2
        val a = 2 * PI * t
        when {
            score < 30 -> { // Opposites attract: two orbs circling far apart, with trails.
                for (k in 0 until 8) {
                    val ang = a - k * 0.12
                    val alpha = (1f - k / 8f) * 0.8f
                    drawCircle(teal.copy(alpha = alpha * 0.5f), 16f - k, Offset(c.x + cos(ang).toFloat() * r * 0.78f, c.y + sin(ang).toFloat() * r * 0.78f))
                    drawCircle(saffron.copy(alpha = alpha * 0.5f), 16f - k, Offset(c.x - cos(ang).toFloat() * r * 0.78f, c.y - sin(ang).toFloat() * r * 0.78f))
                }
                drawCircle(Color.White.copy(alpha = 0.08f), r * 0.78f, c, style = Stroke(2f))
            }
            score < 50 -> { // Common ground: two circles drift together and overlap.
                val d = r * (0.55f - 0.25f * ((sin(a) + 1) / 2).toFloat()) * (2f - progress)
                drawCircle(teal.copy(alpha = 0.35f), r * 0.55f, Offset(c.x - d / 2, c.y))
                drawCircle(saffron.copy(alpha = 0.35f), r * 0.55f, Offset(c.x + d / 2, c.y))
            }
            score < 70 -> { // Great match: a beating heart and ripples.
                for (k in 0 until 3) {
                    val p = (t * 2 + k / 3f) % 1f
                    drawCircle(pink.copy(alpha = (1 - p) * 0.4f), r * (0.4f + 0.6f * p), c, style = Stroke(3f))
                }
                val beat = 1f + 0.08f * sin(a * 4).toFloat().coerceAtLeast(0f)
                drawHeart(c, r * 0.62f * beat * (0.5f + 0.5f * progress), pink.copy(alpha = 0.55f))
            }
            score < 85 -> { // Music twins: two waves falling into step.
                val lag = (1f - progress) * PI.toFloat() + 0.25f * sin(a).toFloat() * (1f - progress)
                listOf(teal to lag, gold to 0f).forEach { (col, ph) ->
                    val path = Path()
                    for (i in 0..60) {
                        val x = size.width * i / 60f
                        val y = c.y + sin(i / 60f * 4 * PI + a * 2 + ph).toFloat() * r * 0.32f
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, col.copy(alpha = 0.75f), style = Stroke(6f))
                }
                sparkles(this, t, gold, 10)
            }
            else -> { // Soulmates: turning rays and falling confetti.
                rotate(t * 360f, c) {
                    for (k in 0 until 16) {
                        val ang = k * 2 * PI / 16
                        drawLine(gold.copy(alpha = 0.35f), c, Offset(c.x + cos(ang).toFloat() * r, c.y + sin(ang).toFloat() * r), strokeWidth = 10f)
                    }
                }
                val colors = listOf(gold, saffron, pink, teal, Color.White)
                for (k in 0 until 36) {
                    val seed = k * 97 % 101 / 101f
                    val x = size.width * ((k * 0.137f + seed) % 1f)
                    val y = size.height * ((t * (0.6f + seed) + k * 0.083f) % 1f)
                    rotate(t * 720f * (if (k % 2 == 0) 1 else -1), Offset(x, y)) {
                        drawRect(colors[k % colors.size], Offset(x - 6, y - 3), androidx.compose.ui.geometry.Size(12f, 6f))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.75f)
        cubicTo(c.x - s * 1.1f, c.y + s * 0.05f, c.x - s * 0.6f, c.y - s * 0.85f, c.x, c.y - s * 0.3f)
        cubicTo(c.x + s * 0.6f, c.y - s * 0.85f, c.x + s * 1.1f, c.y + s * 0.05f, c.x, c.y + s * 0.75f)
        close()
    }
    drawPath(p, color)
}

private fun sparkles(scope: DrawScope, t: Float, color: Color, n: Int) = with(scope) {
    for (k in 0 until n) {
        val tw = ((sin((t * 2 * PI * 3) + k * 1.7) + 1) / 2).toFloat()
        val x = size.width * ((k * 0.31f + 0.07f) % 1f); val y = size.height * ((k * 0.53f + 0.11f) % 1f)
        drawCircle(color.copy(alpha = tw * 0.9f), 3f + tw * 4f, Offset(x, y))
    }
}

/** Two tastes (circles) circling and merging, the Blend mark. */
@Composable
private fun MergingOrbs(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "orbs").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t")
    val a = Saffron; val b = Color(0xFF4FC3D9)
    Canvas(modifier) {
        val r = size.minDimension * 0.26f
        val d = size.minDimension * (0.36f - 0.22f * t)
        drawCircle(b.copy(alpha = 0.55f), r, Offset(center.x - d / 2, center.y))
        drawCircle(a.copy(alpha = 0.55f), r, Offset(center.x + d / 2, center.y))
        drawCircle(SaffronGold.copy(alpha = 0.25f * t), r * 0.5f, center)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(22.dp)).background(Surface).padding(18.dp)) { content() }
}

@Composable
private fun Step(n: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Saffron), contentAlignment = Alignment.Center) {
            Text(n, color = OnSaffron, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(if (primary) (if (enabled) Saffron else Saffron.copy(alpha = 0.35f)) else SurfaceHigh)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) OnSaffron else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
