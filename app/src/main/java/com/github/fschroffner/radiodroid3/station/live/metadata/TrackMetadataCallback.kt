package com.github.fschroffner.radiodroid3.station.live.metadata

interface TrackMetadataCallback {
    enum class FailureType { RECOVERABLE, UNRECOVERABLE }

    fun onFailure(failureType: FailureType)
    fun onSuccess(trackMetadata: TrackMetadata)
}
