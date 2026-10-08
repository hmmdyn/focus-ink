package com.dain.focusink.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** ntfy 의 JSON 스트림 한 줄(이벤트) */
data class NtfyMessage(val id: String, val time: Long, val message: String, val tags: List<String>)

object SyncCodec {
    /** 앱이 보낸 메시지에 붙이는 태그. 이 태그가 있으면 명령으로 읽지 않는다(되먹임 방지). */
    const val OWN_TAG = "focusink"

    fun parseLine(line: String): NtfyMessage? {
        if (line.isBlank()) return null
        val obj = runCatching { Store.json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: return null
        val event = (obj["event"] as? JsonPrimitive)?.content
        if (event != "message") return null
        val id = (obj["id"] as? JsonPrimitive)?.content ?: return null
        val time = (obj["time"] as? JsonPrimitive)?.longOrNull ?: 0L
        val message = (obj["message"] as? JsonPrimitive)?.content.orEmpty()
        val tags = (obj["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        return NtfyMessage(id, time, message, tags)
    }

    fun parseStream(body: String): List<NtfyMessage> = body.lineSequence().mapNotNull(::parseLine).toList()

    /**
     * 받아 온 메시지들을 상태에 반영. 자기 메시지는 건너뛰고, 마지막 메시지 ID 를 기억한다.
     * @return 새 상태와 반영된 명령 수
     */
    fun applyAll(state: AppState, messages: List<NtfyMessage>, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Pair<AppState, Int> {
        if (messages.isEmpty()) return state to 0
        var s = state
        var applied = 0
        for (m in messages) {
            if (OWN_TAG in m.tags) continue
            val cmd = Commands.parse(m.message) ?: continue
            val at = if (m.time > 0) m.time * 1000 else now
            s = Commands.apply(s, cmd, at, now, zone)
            applied++
        }
        return s.copy(settings = s.settings.copy(lastSyncId = messages.last().id)) to applied
    }
}
