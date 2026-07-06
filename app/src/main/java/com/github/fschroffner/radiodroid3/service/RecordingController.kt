package com.github.fschroffner.radiodroid3.service

import android.content.Context
import com.github.fschroffner.radiodroid3.recording.Recordable
import com.github.fschroffner.radiodroid3.recording.RecordingsManager

/**
 * Thin binding between [PlayerService] and the app-wide [RecordingsManager] for the currently
 * playing stream. Extracted so the service no longer reaches into the application object for every
 * record action and instead talks to a single, testable collaborator that already knows which
 * [Recordable] (the active player) it operates on.
 */
class RecordingController(
    private val context: Context,
    private val recordingsManager: RecordingsManager,
    private val recordable: Recordable
) {

    fun start() = recordingsManager.record(context, recordable)

    fun stop() = recordingsManager.stopRecording(recordable)

    fun isRecording(): Boolean = recordable.isRecording()

    fun currentFileName(): String? = recordingsManager.getRecordingInfo(recordable)?.fileName
}
