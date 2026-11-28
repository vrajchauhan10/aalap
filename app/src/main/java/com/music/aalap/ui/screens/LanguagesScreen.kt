package com.music.aalap.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.aalap.data.preferences.RecoPrefs
import com.music.aalap.ui.theme.AppBackground
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.Surface
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary

/**
 * "Which languages do you listen to?": Off / Low / Medium / High per language. Asked once
 * before entering the app (after this update, or a fresh install), and from Settings any time.
 * Every recommendation, radio and mix plays only these languages, in roughly these amounts.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LanguagesScreen(navController: NavController, firstTime: Boolean) {
    val context = LocalContext.current
    var levels by remember {
        mutableStateOf(RecoPrefs.languageLevels(context).ifEmpty { mapOf("hindi" to 3) })
    }
    val anyOn = levels.values.any { it > 0 }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding()) {
        if (!firstTime) Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary,
            modifier = Modifier.padding(16.dp).size(26.dp).clickable { navController.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(if (firstTime) 28.dp else 4.dp))
            Text("Your languages", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Aalap plays only these, everywhere: next songs, radio and every mix. " +
                    "Languages you don't pick never play.",
                color = TextMuted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(22.dp))
            // Step 1: which languages (tap to pick; everything not picked is Off).
            Text("1. Pick your languages", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RecoPrefs.LANGUAGES.forEach { (code, label) ->
                    val on = (levels[code] ?: 0) > 0
                    Text(
                        (if (on) "✓  " else "") + label,
                        color = if (on) OnSaffron else TextPrimary, fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) Saffron else Surface)
                            .clickable { levels = levels + (code to if (on) 0 else 2) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    )
                }
            }
            // Step 2: how much of each picked language.
            val picked = RecoPrefs.LANGUAGES.filter { (levels[it.first] ?: 0) > 0 }
            if (picked.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Text("2. How much of each?", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                picked.forEach { (code, label) ->
                    val level = levels[code] ?: 0
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Surface).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            (1..3).forEach { i ->
                                val on = i == level
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                        .background(if (on) Saffron else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickable { levels = levels + (code to i) }
                                        .padding(vertical = 9.dp),
                                ) {
                                    Text(RecoPrefs.LEVELS[i], color = if (on) OnSaffron else TextMuted,
                                        fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = if (firstTime) 20.dp else 190.dp)
                .fillMaxWidth().clip(RoundedCornerShape(50))
                .background(if (anyOn) Saffron else SurfaceHigh)
                .clickable(enabled = anyOn) {
                    RecoPrefs.setLanguageLevels(context, levels)
                    RecoPrefs.setLanguagesChosen(context)
                    // Today's mixes and stations were built for the old languages: build them again.
                    com.music.aalap.data.recommendation.LanguageChange.applied(context)
                    if (firstTime) navController.navigate(com.music.aalap.ui.navigation.Routes.Home.route) { popUpTo(0) { inclusive = true } }
                    else navController.popBackStack()
                }
                .padding(vertical = 16.dp),
        ) {
            Text(if (anyOn) (if (firstTime) "Continue" else "Save") else "Turn on at least one language",
                color = if (anyOn) OnSaffron else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
