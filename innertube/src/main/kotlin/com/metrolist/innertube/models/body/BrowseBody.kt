package com.metrolist.innertube.models.body

import com.metrolist.innertube.models.Context
import kotlinx.serialization.Serializable

/** Body for youtubei/v1/browse: a library page by [browseId], or its next page by [continuation]. */
@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String? = null,
    val continuation: String? = null,
)
