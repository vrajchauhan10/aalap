package com.music.spotui.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * What every sign-in page (Spotify, Google, YouTube Music) needs inside Aalap:
 * - Links that open a new window (Google's "Forgot password?", "Create account",
 *   "Continue with Google" pop-ups) open here instead of doing nothing.
 * - Account recovery, sign-up and help pages open in the phone's browser, where
 *   codes, SMS and saved passwords all work.
 * - Each site gets its own layout: Spotify's page needs the wide view, but Google's
 *   sign-in shown that way was zoomed out with Next far below.
 */
internal fun WebView.setUpForSignIn(onPageStarted: (String) -> Unit = {}, onPageFinished: (WebView, String) -> Unit = { _, _ -> }) {
    settings.setSupportMultipleWindows(true)
    settings.javaScriptCanOpenWindowsAutomatically = true

    fun opensInBrowser(url: String): Boolean {
        val u = url.lowercase()
        return listOf(
            "/signin/recovery", "/usernamerecovery", "accounts.google.com/lifecycle", "/signup/",
            "accounts.google.com/signup", "/password-reset", "support.google.com", "support.spotify.com",
        ).any { it in u }
    }

    fun openInBrowser(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (!url.startsWith("http")) { openInBrowser(url); return true }
            if (opensInBrowser(url)) { openInBrowser(url); return true }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            val spotify = Uri.parse(url).host.orEmpty().contains("spotify.com")
            view.settings.useWideViewPort = spotify
            view.settings.loadWithOverviewMode = spotify
            onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) = onPageFinished(view, url)
    }

    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // Catch the new window's address and open it in this page instead.
            val catcher = WebView(view.context)
            catcher.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(w: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (opensInBrowser(url)) openInBrowser(url) else view.loadUrl(url)
                    w.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = catcher
            resultMsg.sendToTarget()
            return true
        }
    }
}
