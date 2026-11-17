package be.digitalia.mediasession2mqtt.s0undtv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class S0undTvLogParserTest {

    private fun line(tag: String, message: String, level: Char = 'D') =
        "11-17 22:23:25.135 12929 12929 $level $tag: $message"

    @Test
    fun liveLaunch() {
        assertEquals(
            S0undTvEvent.Launch(S0undTvMediaKind.LIVE),
            S0undTvLogParser.parse(
                line("ActivityTaskManager", "START u0 {flg=0x20000 cmp=com.s0und.s0undtv/.activities.PlayerActivity (has extras)} from uid 10129", 'I')
            )
        )
    }

    @Test
    fun vodLaunch() {
        assertEquals(
            S0undTvEvent.Launch(S0undTvMediaKind.VOD),
            S0undTvLogParser.parse(
                line("ActivityTaskManager", "START u0 {flg=0x20000 cmp=com.s0und.s0undtv/.activities.VODPlayerActivity (has extras)} from uid 10129", 'I')
            )
        )
    }

    @Test
    fun otherAppLaunchIsIgnored() {
        assertNull(
            S0undTvLogParser.parse(
                line("ActivityTaskManager", "START u0 {flg=0x10000000 cmp=com.netflix.ninja/.MainActivity} from uid 1000", 'I')
            )
        )
    }

    @Test
    fun streamStartKeepsSpacesInDisplayName() {
        assertEquals(
            S0undTvEvent.StreamStart("Joueur du Grenier"),
            S0undTvLogParser.parse(line("PlayerActivity", "Start Stream for Joueur du Grenier"))
        )
    }

    // Relevé le 17/11/2026 : une VOD ouverte par s0undtv://vod/<id> logge sa chaîne comme « null ».
    @Test
    fun literalNullChannelIsUnknown() {
        assertEquals(S0undTvEvent.StreamStart(""), S0undTvLogParser.parse(line("PlayerActivity", "Start Stream for null")))
        assertNull(S0undTvLogParser.parse(line("ChatManager", "Trying to start chat #null for user viewer")))
    }

    @Test
    fun chatJoinGivesLogin() {
        assertEquals(
            S0undTvEvent.ChatJoin("joueur_du_grenier"),
            S0undTvLogParser.parse(line("ChatManager", "Trying to start chat #joueur_du_grenier for user viewer"))
        )
    }

    @Test
    fun playWhenReady() {
        assertEquals(
            S0undTvEvent.PlayWhenReady(false),
            S0undTvLogParser.parse(line("VODPlayerActivity2", "onPlayWhenReadyChanged: false"))
        )
        assertEquals(
            S0undTvEvent.PlayWhenReady(true),
            S0undTvLogParser.parse(line("VODPlayerActivity2", "onPlayWhenReadyChanged: true"))
        )
    }

    @Test
    fun stopLines() {
        assertEquals(S0undTvEvent.PlayerStopped, S0undTvLogParser.parse(line("PlayerActivity", "onStop() was called")))
        assertEquals(S0undTvEvent.ChatStop("laink"), S0undTvLogParser.parse(line("ChatManager", "stopThread: laink")))
    }

    @Test
    fun cursorGivesBroadcastId() {
        val cursor = "eyJpZCI6ImVjZWJjMTE2LTgwMzMtNGRkMC04NjQxLTRlYWI0YzNkMmU5OSIsImhrIjoiYnJvYWRjYXN0OjMxODE3NTQ0MjY2MCIsInNrIjoiQUFBQmRiU2RLVUFZNHpfc1RKRWVRQSJ9"
        assertEquals(
            S0undTvEvent.BroadcastId("318175442660"),
            S0undTvLogParser.parse(line("ChatManager", "fetchNewChatMessage: next_cursor: $cursor"))
        )
    }

    @Test
    fun chatTrafficAndUnknownTagsAreIgnored() {
        assertNull(S0undTvLogParser.parse(line("ChatManager", "<:viewer!viewer@viewer.tmi.twitch.tv JOIN #laink")))
        assertNull(S0undTvLogParser.parse(line("ChatManager", "BTTV Done")))
        assertNull(S0undTvLogParser.parse(line("PlayerActivity", "onResume() was called ")))
        assertNull(S0undTvLogParser.parse(line("SomethingElse", "Start Stream for Laink")))
        assertNull(S0undTvLogParser.parse("--------- beginning of main"))
    }

    @Test
    fun base64AcceptsBothAlphabetsWithOrWithoutPadding() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte(), 0x01)
        assertArrayEquals(bytes, S0undTvLogParser.decodeBase64("+/+/AQ=="))
        assertArrayEquals(bytes, S0undTvLogParser.decodeBase64("-_-_AQ"))
        assertNull(S0undTvLogParser.decodeBase64("not base64!"))
    }
}
