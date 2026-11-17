package be.digitalia.mediasession2mqtt.s0undtv

import be.digitalia.mediasession2mqtt.mqttmediaplayer.MQTTMediaMetadata
import be.digitalia.mediasession2mqtt.mqttmediaplayer.MQTTPlaybackState

/** Values of the S0undTV-only topics; all empty when no S0undTV session is active. */
data class S0undTvMetadata(
    val kind: String = "",
    val channel: String = "",
    val broadcastId: String = "",
)

/** The position isn't logged: -1 publishes an empty position, as an unknown one. */
fun S0undTvSession.toMQTTPlaybackState(): MQTTPlaybackState =
    if (isPlaying) MQTTPlaybackState.Playing(-1) else MQTTPlaybackState.Paused(-1)

/** The channel's display name stands for the title; no duration is logged, even for a VOD. */
fun S0undTvSession.toMQTTMediaMetadata(): MQTTMediaMetadata = MQTTMediaMetadata(title = displayName)

fun S0undTvSession?.toS0undTvMetadata(): S0undTvMetadata = when (this) {
    null -> S0undTvMetadata()
    else -> S0undTvMetadata(
        kind = kind.serializedName,
        channel = channel.orEmpty(),
        broadcastId = broadcastId.orEmpty()
    )
}
