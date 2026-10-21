package dev.owohunt.core.runtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(val ts: Long, val level: LogLevel, val msg: String)

/** Ring buffer 500 + stream realtime (pengganti EventEmitter). */
class RingLogger(
    private val maxEntries: Int = 500,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val buf = ArrayDeque<LogEntry>()
    private val _stream = MutableSharedFlow<LogEntry>(extraBufferCapacity = 1024)
    val stream: SharedFlow<LogEntry> = _stream.asSharedFlow()

    val entries: List<LogEntry>
        @Synchronized get() = buf.toList()

    fun log(level: LogLevel, msg: String): LogEntry {
        val entry = LogEntry(now(), level, msg)
        synchronized(this) {
            buf.addLast(entry)
            while (buf.size > maxEntries) buf.removeFirst()
        }
        _stream.tryEmit(entry)
        return entry
    }

    fun info(msg: String) = log(LogLevel.INFO, msg)
    fun warn(msg: String) = log(LogLevel.WARN, msg)
    fun error(msg: String) = log(LogLevel.ERROR, msg)

    @Synchronized
    fun clear() = buf.clear()
}
