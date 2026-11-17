package be.digitalia.mediasession2mqtt.s0undtv

/**
 * A S0undTV playback reconstructed from its log lines. Neither the position nor the duration is logged,
 * and a VOD's own title isn't either: the title is the channel's display name.
 */
data class S0undTvSession(
    val kind: S0undTvMediaKind,
    val displayName: String,
    val channel: String? = null,
    val isPlaying: Boolean = true,
    val broadcastId: String? = null,
)

data class S0undTvState(
    val pendingKind: S0undTvMediaKind? = null,
    val session: S0undTvSession? = null,
    /** The player was closed; the session ends unless another stream starts within the grace delay. */
    val isEnding: Boolean = false,
)

/**
 * Pure state machine from [S0undTvEvent]s to the current [S0undTvSession]. The tracker owns the grace delay
 * and feeds back [S0undTvEvent.EndTimeout] when it expires.
 */
object S0undTvSessionReducer {

    fun reduce(state: S0undTvState, event: S0undTvEvent): S0undTvState {
        val session = state.session
        return when (event) {
            is S0undTvEvent.Launch -> state.copy(pendingKind = event.kind)

            is S0undTvEvent.StreamStart -> {
                val kind = state.pendingKind ?: session?.kind ?: S0undTvMediaKind.LIVE
                // S0undTV sometimes reopens the player on the same channel: keep what is already known about it.
                val restarted = session?.takeIf { it.displayName == event.displayName && it.kind == kind }
                S0undTvState(
                    session = restarted?.copy(isPlaying = true)
                        ?: S0undTvSession(kind = kind, displayName = event.displayName)
                )
            }

            is S0undTvEvent.ChatJoin -> when {
                session == null || session.channel != null -> state
                else -> state.copy(session = session.copy(channel = event.login))
            }

            is S0undTvEvent.PlayWhenReady -> when (session?.kind) {
                S0undTvMediaKind.VOD -> state.copy(session = session.copy(isPlaying = event.playWhenReady))
                else -> state
            }

            // The chat replay keeps prefetching after the VOD is closed: only the first id of a VOD session counts.
            is S0undTvEvent.BroadcastId -> when {
                session == null || session.kind != S0undTvMediaKind.VOD -> state
                session.broadcastId != null || state.isEnding -> state
                else -> state.copy(session = session.copy(broadcastId = event.id))
            }

            S0undTvEvent.PlayerStopped -> if (session == null) state else state.copy(isEnding = true)

            // Leaving another channel's chat (a late stopThread after switching channels) must not end this session.
            is S0undTvEvent.ChatStop -> when {
                session == null -> state
                session.channel != null && !session.channel.equals(event.login, ignoreCase = true) -> state
                else -> state.copy(isEnding = true)
            }

            S0undTvEvent.EndTimeout -> if (state.isEnding) S0undTvState() else state
        }
    }
}
