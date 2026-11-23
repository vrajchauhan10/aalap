package com.music.aalap.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.AppPalette
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Is everything working?": checks the internet, each place songs come from, sign-in, storage
 * and the phone's background and notification permissions, and says in plain words what is
 * wrong and how to fix it. Runs only when opened (or on "Check again"), never in the background.
 */
object SelfCheck {
    enum class Status { OK, WARN, FAIL }

    /** What to do about a problem. */
    enum class Fix { NONE, APP_SETTINGS, NOTIFICATIONS, SIGN_IN, STORAGE }

    data class Result(val title: String, val status: Status, val line: String, val fix: Fix = Fix.NONE, val fixLabel: String = "")

    /** One line on top: all fine, some warnings, or what failed. */
    fun summary(results: List<Result>): String {
        val fails = results.count { it.status == Status.FAIL }
        val warns = results.count { it.status == Status.WARN }
        return when {
            results.isEmpty() -> "Checking…"
            fails == 0 && warns == 0 -> "Everything is working"
            fails == 0 -> if (warns == 1) "Working, with 1 thing to look at" else "Working, with $warns things to look at"
            // Songs still play while at least one music source answers.
            results.any { it.title.startsWith("Songs") && it.status == Status.OK } ->
                if (fails == 1) "Music plays, but 1 thing needs fixing" else "Music plays, but $fails things need fixing"
            else -> if (fails == 1) "1 thing needs fixing" else "$fails things need fixing"
        }
    }

    /** Free-space verdict, kept apart so it can be tested. */
    fun storage(freeBytes: Long): Result = when {
        freeBytes <= 0L -> Result("Storage", Status.WARN, "Couldn't read free space")
        freeBytes < 300_000_000L -> Result("Storage", Status.FAIL,
            "Only ${com.music.aalap.ui.components.formatBytes(freeBytes)} free: downloads and the song cache may fail. Free up some space.",
            Fix.STORAGE, "Open storage")
        freeBytes < 1_500_000_000L -> Result("Storage", Status.WARN,
            "${com.music.aalap.ui.components.formatBytes(freeBytes)} free: enough for now, but not for many downloads.", Fix.STORAGE, "Open storage")
        else -> Result("Storage", Status.OK, "${com.music.aalap.ui.components.formatBytes(freeBytes)} free")
    }

    // A song every source has, to see whether each one answers.
    private const val TITLE = "Tum Hi Ho"
    private const val ARTIST = "Arijit Singh"

    private suspend fun <T> timed(ms: Long, block: suspend () -> T): T? = withTimeoutOrNull(ms) { runCatching { block() }.getOrNull() }

    private fun online(c: Context): Boolean {
        val cm = c.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun run(c: Context, onResult: (Result) -> Unit) = coroutineScope {
        val ctx = c.applicationContext
        val net = online(ctx)
        onResult(
            if (net) Result("Internet", Status.OK, "Connected")
            else Result("Internet", Status.FAIL, "No internet. Downloaded songs still play; turn on Wi-Fi or mobile data for the rest.")
        )
        val local = listOf(
            async { storage(runCatching { android.os.StatFs(ctx.filesDir.path).availableBytes }.getOrDefault(0L)) },
            async { background(ctx) },
            async { notifications(ctx) },
        )
        if (net) {
            val remote = listOf(
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val ok = timed(15_000) {
                        com.music.aalap.data.api.SpotifyTokenProvider.ensureCatalogToken(ctx) &&
                            com.metrolist.spotify.Spotify.search(TITLE, listOf("track"), limit = 3).getOrNull()?.tracks?.items.orEmpty().isNotEmpty()
                    } == true
                    if (ok) Result("Search and song details", Status.OK, "Working")
                    else Result("Search and song details", Status.FAIL,
                        "Search and song pages aren't loading. Your sign-in may have expired: connect your account again.",
                        Fix.SIGN_IN, "Connect again")
                },
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val ok = timed(15_000) { com.music.aalap.saavn.JioSaavnSource.resolve(TITLE, ARTIST, 262, "$TITLE $ARTIST") } != null
                    source("Songs: main source", ok)
                },
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val ok = timed(15_000) {
                        com.metrolist.innertube.YouTube.search("$TITLE $ARTIST", com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG)
                            .getOrNull()?.items.orEmpty().isNotEmpty()
                    } == true
                    source("Songs: backup 1", ok)
                },
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val ok = timed(15_000) { com.music.aalap.gaana.GaanaSource.resolve(TITLE, ARTIST, 262, "", "$TITLE $ARTIST") } != null
                    source("Songs: backup 2", ok)
                },
            )
            remote.forEach { onResult(it.await()) }
        }
        local.forEach { onResult(it.await()) }
    }

    private fun source(title: String, ok: Boolean) =
        if (ok) Result(title, Status.OK, "Answering")
        else Result(title, Status.WARN, "Not answering right now. Aalap uses the other sources meanwhile; this usually fixes itself.")

    private fun background(c: Context): Result {
        val power = c.getSystemService(android.os.PowerManager::class.java)
        val optimised = power?.isIgnoringBatteryOptimizations(c.packageName) == false
        val restricted = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            c.getSystemService(android.app.ActivityManager::class.java)?.isBackgroundRestricted == true
        return when {
            restricted -> Result("Playing in the background", Status.FAIL,
                "Your phone stops Aalap in the background, so music can cut out with the screen off. Open Aalap's settings → Battery → Unrestricted.",
                Fix.APP_SETTINGS, "Open settings")
            optimised -> Result("Playing in the background", Status.WARN,
                "Battery saving may pause Aalap after a long time with the screen off. If that happens: Aalap's settings → Battery → Unrestricted.",
                Fix.APP_SETTINGS, "Open settings")
            else -> Result("Playing in the background", Status.OK, "Allowed")
        }
    }

    private fun notifications(c: Context): Result {
        val on = androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled()
        return if (on) Result("Notifications", Status.OK, "On: player controls show on the lock screen")
        else Result("Notifications", Status.WARN, "Off: you won't see player controls on the lock screen or download progress.",
            Fix.NOTIFICATIONS, "Turn on")
    }

    fun fix(c: Context, fix: Fix) {
        val intent = when (fix) {
            Fix.APP_SETTINGS -> Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", c.packageName, null))
            Fix.NOTIFICATIONS -> Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
            Fix.STORAGE -> Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            else -> return
        }
        runCatching { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfCheckScreen(navController: NavController) {
    val context = LocalContext.current
    val results = remember { mutableStateListOf<SelfCheck.Result>() }
    var round by remember { mutableIntStateOf(0) }
    var running by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(round) {
        results.clear(); running = true
        SelfCheck.run(context) { r -> results.add(r) }
        running = false
    }
    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Is everything working?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary,
                        modifier = Modifier.padding(start = 8.dp).size(26.dp).clickable { navController.popBackStack() },
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBackground),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                if (running) CircularProgressIndicator(color = AppPalette, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Dot(if (results.any { it.status == SelfCheck.Status.FAIL }) SelfCheck.Status.FAIL
                    else if (results.any { it.status == SelfCheck.Status.WARN }) SelfCheck.Status.WARN else SelfCheck.Status.OK)
                Spacer(Modifier.size(10.dp))
                Text(if (running) "Checking…" else SelfCheck.summary(results), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            results.forEach { r ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceHigh).padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(r.status)
                        Spacer(Modifier.size(10.dp))
                        Text(r.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    Text(r.line, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
                    if (r.fix != SelfCheck.Fix.NONE && r.status != SelfCheck.Status.OK) {
                        Text(
                            r.fixLabel, color = OnSaffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp).clip(RoundedCornerShape(50)).background(AppPalette)
                                .clickable {
                                    if (r.fix == SelfCheck.Fix.SIGN_IN) navController.navigate(com.music.aalap.ui.navigation.Routes.SpotifyConnect.route)
                                    else SelfCheck.fix(context, r.fix)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            if (!running) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Check again", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(SurfaceHigh)
                            .clickable { round++ }.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(com.music.aalap.ui.components.BottomClearance))
        }
    }
}

@Composable
private fun Dot(s: SelfCheck.Status) {
    val c = when (s) {
        SelfCheck.Status.OK -> Color(0xFF2FBF71)
        SelfCheck.Status.WARN -> Color(0xFFFFB547)
        SelfCheck.Status.FAIL -> Color(0xFFE5484D)
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}
