package be.digitalia.mediasession2mqtt.logcat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Streams new lines of the system log. Without READ_LOGS, logcat only returns this app's own lines,
 * so callers must check [hasReadLogsPermission] first. The permission is granted once over ADB:
 * `adb shell pm grant be.digitalia.mediasession2mqtt android.permission.READ_LOGS`,
 * and only applies to processes started after the grant.
 */
@Inject
class LogcatSource(private val context: Context) {

    fun hasReadLogsPermission(): Boolean =
        context.checkPermission(Manifest.permission.READ_LOGS, Process.myPid(), Process.myUid()) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Lines from now on (`-T 1`), restricted to [filterSpecs] and to lines containing one of [markers].
     * logcat is restarted with a growing delay if it exits, and killed when the collector is cancelled.
     */
    fun lines(filterSpecs: List<String>, markers: List<String>): Flow<String> = channelFlow {
        val command = listOf("logcat", "-v", "threadtime", "-T", "1", "-b", "main,system") + filterSpecs + "*:S"
        var retryDelay = MIN_RETRY_DELAY_MILLIS
        while (isActive) {
            val process = try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (_: IOException) {
                null
            }
            if (process != null) {
                // A blocked read ignores cancellation: killing logcat is what ends it.
                val killer = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        process.destroy()
                    }
                }
                launch(Dispatchers.IO) {
                    try {
                        process.inputStream.bufferedReader().useLines { sequence ->
                            for (line in sequence) {
                                if (markers.any { it in line }) {
                                    send(line)
                                    retryDelay = MIN_RETRY_DELAY_MILLIS
                                }
                            }
                        }
                    } catch (_: IOException) {
                        // logcat died or was killed: restart it below
                    }
                }.join()
                killer.cancel()
            }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        }
    }

    private companion object {
        const val MIN_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
    }
}
