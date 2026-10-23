package com.music.aalap.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.spotify.SpotifyAuth
import com.music.aalap.R
import com.music.aalap.data.api.SpotifySession
import com.music.aalap.data.api.SpotifyTokenProvider
import com.music.aalap.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val USER_AGENT_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val SPOTIFY_GREEN = 0xFFFF8A3D

/**
 * Spotify-style native login. The user types their email + password into a
 * custom form; under the hood a hidden WebView loads Spotify's real login page
 * and we inject the credentials via JavaScript, then capture the `sp_dc` cookie
 * exactly as before. If Spotify throws a captcha / challenge that we can't drive
 * headlessly, the WebView is revealed so the user can finish in-page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var showWebFallback by remember { mutableStateOf(false) }

    val pageReady = remember { AtomicBoolean(false) }
    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // The off-screen full-size playback WebView is attached above Compose. Remove it
    // while login is visible or it can intercept/draw over this WebView.
    LaunchedEffect(Unit) {
        com.music.aalap.di.SpotifyWebPlayer.detach()
    }

    val navigateToHome: () -> Unit = {
        com.music.aalap.di.SpotifyWebPlayer.attach(context as Activity)
        // Back to "Connect your music", where Spotify now shows a tick.
        if (!navController.popBackStack()) {
            navController.navigate(Routes.MusicSource.route) { popUpTo(0) { inclusive = true } }
        }
    }

    // Poll for the sp_dc cookie — it's set on .spotify.com the moment login
    // succeeds, regardless of which page the (hidden) WebView ends up on.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (tokenFetchStarted.get()) continue
            val spDc = extractCookie("sp_dc")
            if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
                finishLogin(
                    webViewRef, context as Activity, scope,
                    setProcessing = { isProcessing = it },
                    setStatus = { statusMessage = it },
                    setError = { hasError = it },
                    tokenFetchStarted = tokenFetchStarted,
                    onSuccess = navigateToHome,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(com.music.aalap.ui.theme.AppBackground)) {
        // Login is 100% Spotify's own web page in a full-screen WebView — no custom
        // form or credential injection. We just watch for the sp_dc cookie (polled
        // above) to know when the user has signed in, then exchange it for a token.
        AndroidView(
            // Above the keyboard and the gesture bar, so Next / Log in stay reachable.
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 40.dp).navigationBarsPadding().imePadding(),
            factory = { ctx ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.removeAllCookies(null)
                cookieManager.flush()

                WebView(ctx).apply {
                    webViewRef = this
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    // Keep the WebView's REAL (mobile Chrome) User-Agent for login —
                    // a spoofed desktop UA on a phone trips Spotify's reCAPTCHA bot
                    // check and it wrongly returns "Incorrect email or password".

                    setUpForSignIn(
                        onPageStarted = { pageReady.set(false) },
                        onPageFinished = { view, _ -> pageReady.set(true); fixSpotifyLoginLayout(view) },
                    )
                    loadUrl(SpotifyAuth.LOGIN_URL)
                }
            },
        )

        // Slim top bar: title, or the "Signing in…" status once the cookie lands.
        Box(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(40.dp).background(com.music.aalap.ui.theme.AppBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isProcessing) statusMessage.ifBlank { "Connecting…" } else "Connecting to Spotify",
                color = if (hasError) Color(0xFFE22134) else com.music.aalap.ui.theme.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

/** Spotify sometimes computes its login <main> at ~48px tall inside WebView. */
private fun fixSpotifyLoginLayout(webView: WebView) {
    webView.post {
        val viewportHeight = webView.height.coerceAtLeast(1)
        webView.evaluateJavascript(
            """
            (function(){
              var style = document.getElementById('aalap-login-layout-fix');
              if (!style) {
                style = document.createElement('style');
                style.id = 'aalap-login-layout-fix';
                document.head.appendChild(style);
              }
              style.textContent = 'html,body,#__next{height:${viewportHeight}px!important;min-height:${viewportHeight}px!important;}' +
                'main{height:${viewportHeight}px!important;min-height:${viewportHeight}px!important;max-height:none!important;position:relative!important;overflow:auto!important;}';
            })();
            """.trimIndent(),
            null,
        )
    }
}

@Composable
private fun LoginForm(
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    isProcessing: Boolean,
    statusMessage: String,
    hasError: Boolean,
    onSubmit: () -> Unit,
    onUseWeb: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Aalap",
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "Connect Spotify",
            color = com.music.aalap.ui.theme.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
        )
        Spacer(Modifier.height(32.dp))

        Text(
            "Email or username",
            color = com.music.aalap.ui.theme.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmail,
            singleLine = true,
            placeholder = { Text("Email or username", color = Color(0xFF8A8A8A)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            colors = spotifyFieldColors(),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Password",
            color = com.music.aalap.ui.theme.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            singleLine = true,
            placeholder = { Text("Password", color = Color(0xFF8A8A8A)) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        painter = painterResource(
                            id = if (showPassword) R.drawable.visibility else R.drawable.visibility_off
                        ),
                        contentDescription = "Toggle password",
                        tint = com.music.aalap.ui.theme.TextMuted,
                    )
                }
            },
            colors = spotifyFieldColors(),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                statusMessage,
                color = if (hasError) Color(0xFFFF5252) else com.music.aalap.ui.theme.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(SPOTIFY_GREEN),
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF12863B),
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text("Log In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onUseWeb, enabled = !isProcessing) {
            Text(
                "Trouble connecting? Use the sign-in page",
                color = com.music.aalap.ui.theme.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        // Full web signup: reveals the real Spotify page (its "Sign up" link works
        // there since the WebView navigation is unrestricted).
        TextButton(onClick = onUseWeb, enabled = !isProcessing) {
            Text(
                "Don't have an account? Sign up",
                color = com.music.aalap.ui.theme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun spotifyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = com.music.aalap.ui.theme.TextPrimary,
    unfocusedTextColor = com.music.aalap.ui.theme.TextPrimary,
    cursorColor = com.music.aalap.ui.theme.TextPrimary,
    focusedBorderColor = com.music.aalap.ui.theme.TextPrimary,
    unfocusedBorderColor = Color(0xFF727272),
    focusedContainerColor = com.music.aalap.ui.theme.Surface,
    unfocusedContainerColor = com.music.aalap.ui.theme.Surface,
)

/**
 * Fill Spotify's login form with [email] / [password] and submit it. React tracks
 * the input value internally, so we set it through the native value setter and
 * dispatch input/change events for it to register. Returns the script's verdict:
 * "SUBMIT" (clicked login), "NOFORM" (page not ready), or "NOBTN".
 */
private suspend fun injectCredentials(webView: WebView?, email: String, password: String): String {
    webView ?: return "NOFORM"
    val u = JSONObject.quote(email)
    val p = JSONObject.quote(password)
    val js = """
        (function(){
          function setVal(el, val){
            try {
              var proto = window.HTMLInputElement.prototype;
              var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
              setter.call(el, val);
            } catch(e) { el.value = val; }
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
          var u = document.querySelector('#login-username, input[data-testid=login-username], input[autocomplete=username], input[name=username], input[type=email]');
          var p = document.querySelector('#login-password, input[data-testid=login-password], input[type=password]');
          if(!u || !p){ return 'NOFORM'; }
          setVal(u, $u);
          setVal(p, $p);
          var b = document.querySelector('#login-button, button[data-testid=login-button], button[type=submit]');
          if(b){ b.click(); return 'SUBMIT'; }
          if(p.form){ p.form.submit(); return 'SUBMIT'; }
          return 'NOBTN';
        })();
    """.trimIndent()
    return withContext(Dispatchers.Main) {
        val done = kotlinx.coroutines.CompletableDeferred<String>()
        webView.evaluateJavascript(js) { raw ->
            done.complete(raw?.trim('"') ?: "NOFORM")
        }
        done.await()
    }
}

private fun extractCookie(name: String): String? {
    val allCookies = CookieManager.getInstance().getCookie("https://open.spotify.com") ?: return null
    return allCookies.split(";")
        .mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .firstOrNull { it.first == name && it.second.isNotBlank() }
        ?.second
}

private fun finishLogin(
    view: WebView?,
    activity: Activity,
    scope: kotlinx.coroutines.CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    tokenFetchStarted: AtomicBoolean,
    onSuccess: () -> Unit,
) {
    val spDc = extractCookie("sp_dc")
    if (spDc.isNullOrBlank()) {
        setProcessing(true)
        setError(true)
        setStatus("Couldn't finish connecting. Complete the sign-in, then try again.")
        tokenFetchStarted.set(false)
        return
    }

    setProcessing(true)
    setError(false)
    setStatus("Connecting…")
    view?.stopLoading()
    view?.loadUrl("about:blank")

    scope.launch(Dispatchers.IO) {
        SpotifySession.setSpDc(activity, spDc)
        // The community TOTP/gist fetch is occasionally flaky — retry a couple times.
        repeat(3) { attempt ->
            // Through the provider so the token's expiry is recorded and reused.
            if (SpotifyTokenProvider.ensureToken(activity, force = true)) {
                withContext(Dispatchers.Main) { setStatus("Success!") }
                delay(300)
                withContext(Dispatchers.Main) { onSuccess() }
                return@launch
            }
            Timber.e("Spotify token fetch failed (attempt ${attempt + 1})")
            if (attempt < 2) delay(800)
        }
        withContext(Dispatchers.Main) {
            setStatus("Login failed. Check your connection and try again.")
            setError(true)
        }
        tokenFetchStarted.set(false)
    }
}
