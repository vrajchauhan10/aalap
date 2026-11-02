package com.metrolist.music.utils

/** How to read YouTube's player answers. Pure (no Android), so it's tested. */
object YouTubeAnswers {
    /**
     * YouTube's "prove you're not a bot" answer: LOGIN_REQUIRED whose reason doesn't mention
     * age, mature or inappropriate content ("Sign in to confirm you're not a bot",
     * "Please sign in"). A real age limit says so ("Sign in to confirm your age").
     */
    fun isBotCheck(status: String?, reason: String?): Boolean {
        if (status != "LOGIN_REQUIRED") return false
        val r = reason.orEmpty().lowercase()
        if (r.contains("bot")) return true
        return r.isNotBlank() && !r.contains("age") && !r.contains("inappropriate") && !r.contains("mature")
    }
}

/**
 * Which YouTube clients refused this phone lately. On one network a client can refuse every
 * song (an OPPO on mobile data: ANDROID_VR links were 403 each time, costing 1–4 s per song
 * before iOS worked). A client that refused in the last [REST_MS] is skipped, unless it's the
 * last one left to try. In memory only: a new app start (or a new network an hour later)
 * tries everything again. Pure, so it's tested.
 */
object ClientMemory {
    const val REST_MS = 15 * 60_000L
    private val refusedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun refused(client: String, now: Long = System.currentTimeMillis()) { refusedAt[client] = now }
    fun worked(client: String) { refusedAt.remove(client) }
    fun resting(client: String, now: Long = System.currentTimeMillis()): Boolean =
        refusedAt[client]?.let { now - it < REST_MS } == true
    fun clear() = refusedAt.clear()

    /** Answers about the client on this network, not about one video (UNPLAYABLE can be the video). */
    fun isRefusal(status: String?, httpCode: Int? = null): Boolean =
        status == "LOGIN_REQUIRED" || httpCode == 403
}
