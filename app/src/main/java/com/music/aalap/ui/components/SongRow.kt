package com.music.aalap.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.aalap.R
import com.music.aalap.data.entity.SongsModel

/**
 * One song row for every list in Aalap (the Quick picks look): rounded cover, title, a short
 * artist line, then anything the list adds (a tag, a heart) and the ⋮ menu. Presses in softly
 * when touched; long press opens the same menu as ⋮.
 */
@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongsModel,
    playing: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    subtitle: String = shortArtists(song.singer),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .pressSquish(0.97f)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onLongClick = onMenu, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (number != null) Text("$number", color = com.music.aalap.ui.theme.TextMuted, fontSize = 13.sp, modifier = Modifier.width(26.dp))
        GlideImage(
            model = song.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                song.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) com.music.aalap.ui.theme.Saffron else com.music.aalap.ui.theme.TextPrimary,
            )
            Text(subtitle, color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
        IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options for ${song.title}", tint = com.music.aalap.ui.theme.TextMuted)
        }
    }
}
