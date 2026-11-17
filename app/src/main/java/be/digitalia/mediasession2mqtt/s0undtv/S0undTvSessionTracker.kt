package be.digitalia.mediasession2mqtt.s0undtv

import be.digitalia.mediasession2mqtt.logcat.LogcatSource
import be.digitalia.mediasession2mqtt.settings.SettingsProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The current S0undTV session, or null when none is playing, the option is off, or READ_LOGS isn't granted.
 */
@Inject
@SingleIn(AppScope::class)
class S0undTvSessionTracker(
    private val logcatSource: LogcatSource,
    private val settingsProvider: SettingsProvider,
) {
    // The display name and the login arrive in two lines a few ms apart: settling avoids publishing an empty channel.
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val session: Flow<S0undTvSession?> =
        settingsProvider.isS0undTvEnabled.flatMapLatest { isEnabled ->
            if (isEnabled && logcatSource.hasReadLogsPermission()) trackSession() else flowOf(null)
        }.debounce(SETTLE_DELAY_MILLIS).distinctUntilChanged()

    private fun trackSession(): Flow<S0undTvSession?> = channelFlow {
        val timeouts = Channel<S0undTvEvent>(Channel.CONFLATED)
        val events = logcatSource
            .lines(S0undTvLogParser.LOGCAT_FILTER_SPECS, S0undTvLogParser.MESSAGE_MARKERS)
            .mapNotNull(S0undTvLogParser::parse)
        var state = S0undTvState()
        var endTimer: Job? = null
        send(null)
        merge(events, timeouts.receiveAsFlow()).collect { event ->
            state = S0undTvSessionReducer.reduce(state, event)
            if (!state.isEnding) {
                endTimer?.cancel()
                endTimer = null
            } else if (endTimer == null) {
                // Started once: the chat replay keeps logging after a VOD is closed and must not postpone the end.
                endTimer = launch {
                    delay(END_GRACE_DELAY_MILLIS)
                    timeouts.send(S0undTvEvent.EndTimeout)
                }
            }
            send(state.session)
        }
    }

    private companion object {
        /** Switching channels closes the player and opens a new one: don't report idle in between. */
        const val END_GRACE_DELAY_MILLIS = 3_000L
        const val SETTLE_DELAY_MILLIS = 300L
    }
}
