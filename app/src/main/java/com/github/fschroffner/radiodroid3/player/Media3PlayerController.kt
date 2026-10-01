package com.github.fschroffner.radiodroid3.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// If these exist elsewhere, you might need to import them instead.
interface RadioPlayerController {
    val uiState: StateFlow<PlayerUiState>
    fun play()
    fun pause()
    fun skip()
}

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = ""
)

@Singleton
class Media3PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) : RadioPlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _uiState = MutableStateFlow(PlayerUiState()) 
    override val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var mediaController: MediaController? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        scope.launch {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, RadioPlaybackService::class.java)
            )
            
            mediaController = MediaController.Builder(context, sessionToken)
                .buildAsync()
                .await().apply {
                    addListener(playerListener)
                }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update { state -> 
                state.copy(
                    isPlaying = mediaController?.isPlaying == true
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { state -> state.copy(isPlaying = isPlaying) }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _uiState.update { state ->
                state.copy(
                    title = mediaMetadata.title?.toString() ?: "",
                    artist = mediaMetadata.artist?.toString() ?: ""
                )
            }
        }
    }

    override fun play() {
        mediaController?.play()
    }

    override fun pause() {
        mediaController?.pause()
    }

    override fun skip() {
        mediaController?.seekToNext()
    }
}
