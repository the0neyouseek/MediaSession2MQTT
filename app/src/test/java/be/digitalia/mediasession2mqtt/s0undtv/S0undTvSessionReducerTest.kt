package be.digitalia.mediasession2mqtt.s0undtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class S0undTvSessionReducerTest {

    private fun reduceAll(events: List<S0undTvEvent>, from: S0undTvState = S0undTvState()): S0undTvState =
        events.fold(from, S0undTvSessionReducer::reduce)

    /** Every state reached while replaying the anonymised capture of a live stream, then a VOD (S0undTV 1.5.10). */
    private fun replayCapture(): List<S0undTvState> {
        val lines = javaClass.getResource("/s0undtv-live-then-vod.log")!!.readText().lines()
        return lines.mapNotNull(S0undTvLogParser::parse).runningFold(S0undTvState(), S0undTvSessionReducer::reduce)
    }

    @Test
    fun captureLiveSession() {
        val live = replayCapture().mapNotNull { it.session }.first { it.displayName == "Laink" }
        assertEquals(S0undTvMediaKind.LIVE, live.kind)
        assertTrue(live.isPlaying)
    }

    @Test
    fun captureLiveSessionGetsItsLogin() {
        val live = replayCapture().mapNotNull { it.session }.last { it.displayName == "Laink" }
        assertEquals("laink", live.channel)
    }

    @Test
    fun captureVodPausesResumesAndGetsBroadcastId() {
        val vodStates = replayCapture().mapNotNull { it.session }.filter { it.displayName == "Mynthos" }
        assertTrue(vodStates.all { it.kind == S0undTvMediaKind.VOD })
        assertEquals("mynthos", vodStates.last().channel)
        val playingTransitions = vodStates.map { it.isPlaying }
            .fold(emptyList<Boolean>()) { acc, isPlaying -> if (acc.lastOrNull() == isPlaying) acc else acc + isPlaying }
        assertEquals(listOf(true, false, true), playingTransitions)
        assertEquals("318175442660", vodStates.last().broadcastId)
    }

    @Test
    fun captureEndsInEndingStateAfterVodIsClosed() {
        val states = replayCapture()
        val closed = states.first { it.session?.displayName == "Mynthos" && it.isEnding }
        assertEquals("318175442660", closed.session?.broadcastId)
    }

    @Test
    fun stopWaitsForTimeout() {
        val playing = reduceAll(
            listOf(
                S0undTvEvent.Launch(S0undTvMediaKind.LIVE),
                S0undTvEvent.StreamStart("Laink"),
                S0undTvEvent.ChatJoin("laink"),
            )
        )
        val ending = reduceAll(listOf(S0undTvEvent.PlayerStopped, S0undTvEvent.ChatStop("laink")), playing)
        assertTrue(ending.isEnding)
        assertEquals("Laink", ending.session?.displayName)
        assertNull(S0undTvSessionReducer.reduce(ending, S0undTvEvent.EndTimeout).session)
    }

    @Test
    fun channelSwitchWithinGraceDelayKeepsPlaying() {
        val switched = reduceAll(
            listOf(
                S0undTvEvent.StreamStart("Laink"),
                S0undTvEvent.ChatJoin("laink"),
                S0undTvEvent.PlayerStopped,
                S0undTvEvent.ChatStop("laink"),
                S0undTvEvent.Launch(S0undTvMediaKind.LIVE),
                S0undTvEvent.StreamStart("Joueur du Grenier"),
                S0undTvEvent.ChatJoin("joueur_du_grenier"),
            )
        )
        assertFalse(switched.isEnding)
        val afterLateTimeout = S0undTvSessionReducer.reduce(switched, S0undTvEvent.EndTimeout)
        assertEquals("joueur_du_grenier", afterLateTimeout.session?.channel)
    }

    @Test
    fun playerReopenedOnSameChannelKeepsLogin() {
        val reopened = reduceAll(
            listOf(
                S0undTvEvent.Launch(S0undTvMediaKind.LIVE),
                S0undTvEvent.StreamStart("AntoineDaniel"),
                S0undTvEvent.ChatJoin("antoinedaniel"),
                S0undTvEvent.Launch(S0undTvMediaKind.LIVE),
                S0undTvEvent.StreamStart("AntoineDaniel"),
            )
        )
        assertEquals("antoinedaniel", reopened.session?.channel)
    }

    @Test
    fun otherChannelStartsWithoutPreviousLogin() {
        val switched = reduceAll(
            listOf(
                S0undTvEvent.StreamStart("AntoineDaniel"),
                S0undTvEvent.ChatJoin("antoinedaniel"),
                S0undTvEvent.StreamStart("Etoiles"),
            )
        )
        assertNull(switched.session?.channel)
    }

    @Test
    fun lateStopOfPreviousChannelIsIgnored() {
        val state = reduceAll(
            listOf(
                S0undTvEvent.StreamStart("Joueur du Grenier"),
                S0undTvEvent.ChatJoin("joueur_du_grenier"),
                S0undTvEvent.ChatStop("laink"),
            )
        )
        assertFalse(state.isEnding)
    }

    @Test
    fun liveIgnoresVodOnlyEvents() {
        val live = reduceAll(
            listOf(
                S0undTvEvent.StreamStart("Laink"),
                S0undTvEvent.PlayWhenReady(false),
                S0undTvEvent.BroadcastId("1"),
            )
        )
        val session = live.session!!
        assertTrue(session.isPlaying)
        assertNull(session.broadcastId)
    }

    @Test
    fun streamStartWithoutLaunchDefaultsToLive() {
        assertEquals(S0undTvMediaKind.LIVE, reduceAll(listOf(S0undTvEvent.StreamStart("Laink"))).session?.kind)
    }

    @Test
    fun eventsWithoutSessionChangeNothing() {
        val idle = reduceAll(
            listOf(
                S0undTvEvent.ChatStop("antoinedaniel"),
                S0undTvEvent.PlayerStopped,
                S0undTvEvent.PlayWhenReady(true),
                S0undTvEvent.EndTimeout,
            )
        )
        assertEquals(S0undTvState(), idle)
    }
}
