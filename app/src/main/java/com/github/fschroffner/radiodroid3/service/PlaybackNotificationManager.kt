package com.github.fschroffner.radiodroid3.service

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.github.fschroffner.radiodroid3.R
import com.github.fschroffner.radiodroid3.Utils
import com.github.fschroffner.radiodroid3.players.PlayState
import com.github.fschroffner.radiodroid3.station.DataRadioStation
import java.io.ByteArrayOutputStream

/**
 * Handles media notification state updates, artwork generation,
 * status text localization, and interaction with Media3's [RadioMediaPlayer].
 * Uses Coil for image loading.
 */
class PlaybackNotificationManager(
    private val context: Context,
    private val radioMediaPlayer: RadioMediaPlayer,
    private val onIconUpdated: () -> Unit
) {
    companion object {
        private const val TAG = "PlaybackNotification"
    }

    var radioIcon: BitmapDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_launcher, null) as BitmapDrawable
        private set

    var isNotificationActive: Boolean = false
        private set

    fun updatePlayerState(
        playState: PlayState,
        currentStation: DataRadioStation?,
        pauseReason: PauseReason,
        lastErrorFromPlayer: Int,
        liveTitle: String?,
        currentPlayerState: PlayState
    ) {
        if (playState == PlayState.Idle || currentStation == null) {
            isNotificationActive = false
            radioMediaPlayer.update(Player.STATE_IDLE, false, null, MediaMetadata.EMPTY)
            return
        }

        isNotificationActive = true

        val statusText = notificationStatusText(playState, currentPlayerState, pauseReason, lastErrorFromPlayer, liveTitle)
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(currentStation.Name)
            .setStation(currentStation.Name)
            .setArtist(statusText)
        bitmapToPng(radioIcon.bitmap)?.let {
            metadataBuilder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val metadata = metadataBuilder.build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(currentStation.StationUuid)
            .setMediaMetadata(metadata)
            .build()

        val (state, playWhenReady) = when (playState) {
            PlayState.PrePlaying -> Player.STATE_BUFFERING to true
            PlayState.Playing -> Player.STATE_READY to true
            PlayState.Paused -> Player.STATE_READY to false
            PlayState.Idle -> Player.STATE_IDLE to false
        }

        radioMediaPlayer.update(state, playWhenReady, mediaItem, metadata)
    }

    fun loadStationIcon(station: DataRadioStation?) {
        if (station == null || !station.hasIcon() || !Utils.shouldLoadIcons(context)) {
            radioIcon = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_launcher, null) as BitmapDrawable
            onIconUpdated()
            return
        }

        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 70f, context.resources.displayMetrics)

        val request = ImageRequest.Builder(context)
            .data(station.IconUrl)
            .size(px.toInt())
            .allowHardware(false)
            .apply {
                if (Utils.useCircularIcons(context)) {
                    transformations(CircleCropTransformation())
                }
            }
            .target(
                onSuccess = { drawable ->
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap()
                    radioIcon = BitmapDrawable(context.resources, bitmap)
                    onIconUpdated()
                }
            )
            .build()

        context.imageLoader.enqueue(request)
    }

    fun notificationStatusText(
        playState: PlayState,
        currentPlayerState: PlayState,
        pauseReason: PauseReason,
        lastErrorFromPlayer: Int,
        liveTitle: String?
    ): String {
        val res = context.resources

        if ((currentPlayerState == PlayState.Paused || currentPlayerState == PlayState.Idle)
            && pauseReason == PauseReason.METERED_CONNECTION
        ) {
            return res.getString(R.string.notify_metered_connection)
        }

        if (lastErrorFromPlayer != -1) {
            try {
                return res.getString(lastErrorFromPlayer)
            } catch (ex: Resources.NotFoundException) {
                Log.e(TAG, "Unknown play error: $lastErrorFromPlayer", ex)
            }
        }

        return when (playState) {
            PlayState.PrePlaying -> res.getString(R.string.notify_pre_play)
            PlayState.Playing ->
                if (!TextUtils.isEmpty(liveTitle)) liveTitle!! else res.getString(R.string.notify_play)
            PlayState.Paused -> res.getString(R.string.notify_paused)
            PlayState.Idle -> ""
        }
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray? = try {
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not encode notification artwork", e)
        null
    }
}
