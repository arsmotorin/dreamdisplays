package com.dreamdisplays.api.media.sink


interface AudioSink : AutoCloseable {
    fun onAudioData(pcmData: ByteArray, timestampUs: Long)
    fun setVolume(volume: Float)
    fun flush()
    val isAvailable: Boolean
}
