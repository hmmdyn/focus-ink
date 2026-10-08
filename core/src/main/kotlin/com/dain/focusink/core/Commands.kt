package com.dain.focusink.core

import java.time.ZoneId

/** 아이폰 단축어가 ntfy 로 보내는 한 줄 명령. 형식은 docs/SYNC.md */
sealed interface Command {
    data class Distract(val note: String) : Command
    data class Urge(val note: String) : Command
    data class Task(val text: String) : Command
    data class Note(val text: String) : Command
    data class Start(val minutes: Int?, val label: String) : Command
    data object Stop : Command
}

object Commands {

    private val aliases: Map<String, String> = mapOf(
        "DISTRACT" to "DISTRACT", "방해" to "DISTRACT",
        "URGE" to "URGE", "충동" to "URGE",
        "TASK" to "TASK", "할일" to "TASK", "할 일" to "TASK",
        "NOTE" to "NOTE", "메모" to "NOTE",
        "START" to "START", "시작" to "START",
        "STOP" to "STOP", "종료" to "STOP", "끝" to "STOP",
    )

    fun parse(raw: String): Command? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val head = text.substringBefore(' ').trim()
        val rest = text.substringAfter(' ', "").trim()
        return when (aliases[head.uppercase()] ?: aliases[head]) {
            "DISTRACT" -> Command.Distract(rest)
            "URGE" -> Command.Urge(rest)
            "TASK" -> if (rest.isEmpty()) null else Command.Task(rest)
            "NOTE" -> if (rest.isEmpty()) null else Command.Note(rest)
            "START" -> {
                val first = rest.substringBefore(' ')
                val minutes = first.toIntOrNull()
                val label = if (minutes != null) rest.substringAfter(' ', "").trim() else rest
                Command.Start(minutes, label)
            }
            "STOP" -> Command.Stop
            else -> null
        }
    }

    /** 앱 이름으로 방해 분류를 추정 */
    fun categoryFor(note: String): DistractionCategory {
        val n = note.lowercase()
        val sns = listOf("instagram", "인스타", "youtube", "유튜브", "tiktok", "틱톡", "x", "twitter", "트위터", "threads", "스레드", "reddit", "netflix", "넷플릭스", "웹툰", "shorts", "쇼츠")
        val msg = listOf("kakao", "카톡", "카카오", "message", "메시지", "문자", "slack", "슬랙", "discord", "디스코드", "mail", "메일", "telegram")
        return when {
            sns.any { n == it || n.contains(it) && it.length > 1 } -> DistractionCategory.SNS
            msg.any { n.contains(it) } -> DistractionCategory.MESSAGE
            else -> DistractionCategory.PHONE
        }
    }

    /** 명령을 상태에 반영. at = 아이폰에서 보낸 시각 */
    fun apply(state: AppState, cmd: Command, at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): AppState {
        val today = Dates.today(now, zone)
        return when (cmd) {
            is Command.Distract -> Distractions.log(state, categoryFor(cmd.note), at, cmd.note.ifEmpty { "아이폰" }, Source.IPHONE)
            is Command.Urge -> Distractions.log(state, categoryFor(cmd.note), at, cmd.note.ifEmpty { "아이폰" }, Source.IPHONE, resisted = true)
            is Command.Task -> Journal.add(state, cmd.text, today, at, Source.IPHONE)
            is Command.Note -> Journal.add(state, "- " + cmd.text, today, at, Source.IPHONE)
            is Command.Start -> Focus.start(state, cmd.label, cmd.minutes ?: state.settings.deepMinutes, now)
            Command.Stop -> Focus.finish(state, now)
        }
    }
}
