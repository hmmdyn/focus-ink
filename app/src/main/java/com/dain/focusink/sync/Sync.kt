package com.dain.focusink.sync

import com.dain.focusink.core.Dates
import com.dain.focusink.core.NtfyMessage
import com.dain.focusink.core.SyncCodec
import com.dain.focusink.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Ntfy {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
        }

    fun poll(server: String, topic: String, since: String): List<NtfyMessage> {
        val conn = open("${server.trimEnd('/')}/${enc(topic)}/json?poll=1&since=${enc(since)}")
        try {
            val code = conn.responseCode
            if (code != 200) {
                if (since != "12h") return poll(server, topic, "12h")
                throw IOException("HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return SyncCodec.parseStream(body)
        } finally {
            conn.disconnect()
        }
    }

    fun publish(server: String, topic: String, text: String) {
        val conn = open("${server.trimEnd('/')}/${enc(topic)}")
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.setRequestProperty("Tags", SyncCodec.OWN_TAG)
            conn.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }
}

/** 아이폰 단축어 ↔ e-ink 앱. docs/SYNC.md 참고 */
class SyncManager(private val repo: Repository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _status = MutableStateFlow("아직 가져온 기록이 없어요")
    val status: StateFlow<String> = _status

    fun pullAsync() {
        scope.launch { pull() }
    }

    suspend fun pull(): Int = mutex.withLock {
        val s = repo.state.value.settings
        if (!s.syncEnabled || s.syncTopic.isBlank()) return 0
        val now = System.currentTimeMillis()
        try {
            val messages = Ntfy.poll(s.syncServer, s.syncTopic, s.lastSyncId ?: "12h")
            var applied = 0
            if (messages.isNotEmpty()) {
                repo.update { st ->
                    val (next, n) = SyncCodec.applyAll(st, messages, System.currentTimeMillis())
                    applied = n
                    next
                }
            }
            _status.value = "${Dates.hhmm(now)}에 가져왔어요 · 새 기록 ${applied}건"
            applied
        } catch (e: Exception) {
            _status.value = "${Dates.hhmm(now)}에 가져오지 못했어요 · ${e.message ?: e.javaClass.simpleName}"
            0
        }
    }

    fun publishAsync(text: String, force: Boolean = false) {
        val s = repo.state.value.settings
        if ((!s.syncEnabled && !force) || s.syncTopic.isBlank()) return
        scope.launch {
            try {
                Ntfy.publish(s.syncServer, s.syncTopic, text)
                if (force) _status.value = "${Dates.hhmm(System.currentTimeMillis())}에 시험 메시지를 보냈어요"
            } catch (e: Exception) {
                _status.value = "${Dates.hhmm(System.currentTimeMillis())}에 보내지 못했어요 · ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
}
