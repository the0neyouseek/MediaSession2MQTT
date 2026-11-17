package be.digitalia.mediasession2mqtt.s0undtv

/**
 * Turns a `logcat -v threadtime` line into a [S0undTvEvent], or null when the line says nothing about playback.
 * Pure Kotlin, without Android dependencies, so it can be unit-tested against real captured logs.
 *
 * These log lines are not an interface: a future S0undTV version may change them. They were read from version 1.5.10.
 */
object S0undTvLogParser {
    const val PACKAGE_NAME = "com.s0und.s0undtv"

    /** Tags to pass to logcat, with their minimum priority. */
    val LOGCAT_FILTER_SPECS = listOf(
        "ActivityTaskManager:I",
        "PlayerActivity:D",
        "VODPlayerActivity2:D",
        "ChatManager:D",
    )

    /** Fragments of the messages this parser understands, to drop the chat traffic cheaply before parsing. */
    val MESSAGE_MARKERS = listOf(
        "cmp=$PACKAGE_NAME/",
        "Start Stream for ",
        "Trying to start chat #",
        "onPlayWhenReadyChanged: ",
        "next_cursor: ",
        "onStop() was called",
        "stopThread: ",
    )

    private val THREADTIME_LINE = Regex("""^\S+\s+\S+\s+\d+\s+\d+\s+[VDIWEF]\s+(.+?)\s*: (.*)$""")
    private val STREAM_START = Regex("""^Start Stream for (.+)$""")
    private val CHAT_JOIN = Regex("""^Trying to start chat #(\S+)""")
    private val PLAY_WHEN_READY = Regex("""^onPlayWhenReadyChanged: (true|false)$""")
    private val NEXT_CURSOR = Regex("""^fetchNewChatMessage: next_cursor: ([A-Za-z0-9_=+/-]+)""")
    private val CHAT_STOP = Regex("""^stopThread: (\S+)$""")
    private val BROADCAST_KEY = Regex(""""hk"\s*:\s*"broadcast:(\d+)"""")

    fun parse(line: String): S0undTvEvent? {
        val match = THREADTIME_LINE.find(line) ?: return null
        val tag = match.groupValues[1]
        val message = match.groupValues[2].trimEnd()
        return when (tag) {
            "ActivityTaskManager" -> parseActivityStart(message)
            "PlayerActivity" -> when {
                message.startsWith("onStop() was called") -> S0undTvEvent.PlayerStopped
                else -> STREAM_START.find(message)?.let { S0undTvEvent.StreamStart(known(it.groupValues[1]).orEmpty()) }
            }

            "VODPlayerActivity2" -> PLAY_WHEN_READY.find(message)?.let {
                S0undTvEvent.PlayWhenReady(it.groupValues[1] == "true")
            }

            "ChatManager" -> CHAT_JOIN.find(message)?.let { join -> known(join.groupValues[1])?.let { S0undTvEvent.ChatJoin(it) } }
                ?: CHAT_STOP.find(message)?.let { S0undTvEvent.ChatStop(it.groupValues[1]) }
                ?: NEXT_CURSOR.find(message)?.let { cursor ->
                    decodeBroadcastId(cursor.groupValues[1])?.let { S0undTvEvent.BroadcastId(it) }
                }

            else -> null
        }
    }

    /**
     * A VOD opened by link (`s0undtv://vod/<id>`) skips the channel screen: S0undTV then logs its channel as the
     * literal "null" ("Start Stream for null", "chat #null"), which is a real Twitch account. Treat it as unknown.
     */
    private fun known(name: String): String? = name.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }

    private fun parseActivityStart(message: String): S0undTvEvent? {
        if (!message.startsWith("START ")) {
            return null
        }
        return when {
            "cmp=$PACKAGE_NAME/.activities.VODPlayerActivity" in message -> S0undTvEvent.Launch(S0undTvMediaKind.VOD)
            "cmp=$PACKAGE_NAME/.activities.PlayerActivity" in message -> S0undTvEvent.Launch(S0undTvMediaKind.LIVE)
            else -> null
        }
    }

    /** The chat replay cursor is base64-encoded JSON whose `hk` key reads `broadcast:<id>`. */
    internal fun decodeBroadcastId(cursor: String): String? {
        val json = decodeBase64(cursor)?.toString(Charsets.UTF_8) ?: return null
        return BROADCAST_KEY.find(json)?.groupValues?.get(1)
    }

    /**
     * Minimal base64 decoder accepting both the standard and the URL-safe alphabets, with or without padding.
     * java.util.Base64 needs API 26 and this app supports API 22.
     */
    internal fun decodeBase64(input: String): ByteArray? {
        val output = java.io.ByteArrayOutputStream(input.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (char in input) {
            val value = when (char) {
                in 'A'..'Z' -> char - 'A'
                in 'a'..'z' -> char - 'a' + 26
                in '0'..'9' -> char - '0' + 52
                '+', '-' -> 62
                '/', '_' -> 63
                '=' -> break
                else -> return null
            }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xFF)
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return output.toByteArray()
    }
}
