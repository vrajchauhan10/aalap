package com.metrolist.innertube.models.body

import com.metrolist.innertube.models.Context
import kotlinx.serialization.Serializable

/** Body for youtubei/v1/next: the watch page's "Up next" queue for a video. */
@Serializable
data class NextBody(
    val context: Context,
    val videoId: String?,
    val playlistId: String?,
    val isAudioOnly: Boolean = true,
    val enablePersistentPlaylistPanel: Boolean = true,
    val tunerSettingValue: String = "AUTOMIX_SETTING_NORMAL",
)
