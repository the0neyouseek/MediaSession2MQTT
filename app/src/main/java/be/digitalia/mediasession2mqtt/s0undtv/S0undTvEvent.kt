package be.digitalia.mediasession2mqtt.s0undtv

/**
 * What S0undTV (com.s0und.s0undtv), a Twitch client without a MediaSession, reveals about
 * playback through its log lines.
 */
sealed interface S0undTvEvent {
    /** A player screen is being opened; [kind] tells a live stream from a VOD. */
    data class Launch(val kind: S0undTvMediaKind) : S0undTvEvent

    /** Playback of a channel starts, identified by its display name. */
    data class StreamStart(val displayName: String) : S0undTvEvent

    /** The chat of the channel is joined, giving its login. */
    data class ChatJoin(val login: String) : S0undTvEvent

    /** VOD only: playback is resumed (true) or paused (false). */
    data class PlayWhenReady(val playWhenReady: Boolean) : S0undTvEvent

    /** VOD only: id of the live broadcast the VOD was recorded from, read from the chat replay cursor. */
    data class BroadcastId(val id: String) : S0undTvEvent

    /** The player screen is stopped. */
    data object PlayerStopped : S0undTvEvent

    /** The chat of a channel is left, which happens when its player is closed. */
    data class ChatStop(val login: String) : S0undTvEvent

    /** Emitted by the tracker once a stopped session has stayed stopped for the grace delay. */
    data object EndTimeout : S0undTvEvent
}

enum class S0undTvMediaKind(val serializedName: String) {
    LIVE("live"),
    VOD("vod")
}
