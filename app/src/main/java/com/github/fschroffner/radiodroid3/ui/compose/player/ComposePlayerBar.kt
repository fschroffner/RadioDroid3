package com.github.fschroffner.radiodroid3.ui.compose.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.player.PlayerUiState
import com.github.fschroffner.radiodroid3.players.PlayState

/**
 * Modern Jetpack Compose Mini-Player Bar.
 *
 * Implements a reactive, fluid Material 3 component consuming [PlayerUiState]
 * and rendering album art via Coil [AsyncImage].
 */
@Composable
fun ComposePlayerBar(
    uiState: PlayerUiState,
    onTogglePlayer: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable(onClick = onTogglePlayer),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Station / Album Art with Coil
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val artUrl = uiState.albumArtUrl ?: uiState.station?.IconUrl
                if (!artUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = stringResource(R.string.station_icon),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Buffering spinner overlay
                if (uiState.playState == PlayState.PrePlaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata: Station Name & Live Track Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                val title = uiState.station?.Name ?: stringResource(R.string.app_name)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val streamTitle = uiState.streamLiveInfo.title
                val streamArtist = uiState.streamLiveInfo.artist
                val subtitle = when {
                    !streamArtist.isNullOrBlank() && !streamTitle.isNullOrBlank() -> "$streamArtist - $streamTitle"
                    !streamTitle.isNullOrBlank() -> streamTitle
                    !uiState.station?.TagsAll.isNullOrBlank() -> uiState.station.TagsAll
                    else -> stringResource(R.string.player_info_status_stopped)
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Play / Pause Button with Crossfade Animation
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(44.dp)
            ) {
                Crossfade(targetState = uiState.isPlaying, label = "PlayPauseAnimation") { isPlaying ->
                    if (isPlaying) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pause_24dp),
                            contentDescription = stringResource(R.string.detail_pause),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_play_arrow_24dp),
                            contentDescription = stringResource(R.string.detail_play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // More / Options Button
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert_black_24dp),
                    contentDescription = stringResource(R.string.image_button_more),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
