package com.dain.focusink.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class EntryKind { TASK, NOTE }

@Serializable
enum class EntryStatus { OPEN, DONE, CANCELLED }

@Serializable
enum class Source { APP, IPHONE }

/** 불렛저널 한 줄. date = 이 항목이 속한 날(yyyy-MM-dd). 옮겨 적으면 date가 바뀌고 migrations가 1 늘어난다. */
@Serializable
data class Entry(
    val id: String,
    val text: String,
    val date: String,
    val kind: EntryKind = EntryKind.TASK,
    val status: EntryStatus = EntryStatus.OPEN,
    val priority: Boolean = false,
    val migrations: Int = 0,
    val createdAt: Long = 0L,
    val doneAt: Long? = null,
    val source: Source = Source.APP,
)

@Serializable
enum class Outcome { COMPLETED, ENDED_EARLY, ABANDONED }

@Serializable
data class ActiveSession(
    val id: String,
    val label: String,
    val intention: String = "",
    val entryId: String? = null,
    val startedAt: Long,
    val plannedMinutes: Int,
    val anchorMinutes: Int,
    /** "충동이 왔다"를 누른 시각. 10분 버티기 진행 중이면 non-null. */
    val urgeStartedAt: Long? = null,
)

@Serializable
data class FocusSession(
    val id: String,
    val label: String,
    val intention: String = "",
    val entryId: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val outcome: Outcome,
    /** 집중도 1~3, 0 = 기록 안 함 */
    val quality: Int = 0,
    val reflection: String = "",
    val distractions: Int = 0,
) {
    val actualMinutes: Int get() = ((endedAt - startedAt) / 60_000L).toInt().coerceAtLeast(0)
}

@Serializable
enum class DistractionCategory(val label: String) {
    PHONE("휴대폰"),
    SNS("SNS·영상"),
    MESSAGE("메시지"),
    THOUGHT("딴생각"),
    PERSON("사람"),
    ENVIRONMENT("소음·환경"),
    OTHER("기타"),
}

/** resisted = true 이면 "충동이 왔지만 버팀"(실패가 아니라 승리로 센다). */
@Serializable
data class Distraction(
    val id: String,
    val at: Long,
    val category: DistractionCategory,
    val note: String = "",
    val source: Source = Source.APP,
    val sessionId: String? = null,
    val resisted: Boolean = false,
)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    /** 민망할 만큼 작은 버전. 예: "운동화 신기" */
    val tiny: String = "",
    /** 습관 쌓기 앵커. 예: "커피 내린 후" */
    val anchor: String = "",
    /** 최소 기준선: 나쁜 날에도 지킬 것 */
    val baseline: Boolean = false,
    val createdDate: String,
    val archived: Boolean = false,
)

@Serializable
data class DayReview(
    val date: String,
    val note: String = "",
    val completedAt: Long,
)

@Serializable
data class WeeklyTarget(
    /** 그 주 월요일 yyyy-MM-dd */
    val weekStart: String,
    val category: DistractionCategory,
    val plan: String = "",
)

@Serializable
data class Settings(
    val deepMinutes: Int = 90,
    val anchorMinutes: Int = 15,
    val breakMinutes: Int = 5,
    val urgeMinutes: Int = 10,
    /** HH:mm */
    val reviewTime: String = "21:30",
    val sunsetTime: String = "22:30",
    /** java.time.DayOfWeek 값(1=월 … 7=일). 회의·통화를 몰아 두는 매니저 데이 */
    val managerDays: List<Int> = listOf(3, 5),
    val syncEnabled: Boolean = false,
    val syncServer: String = "https://ntfy.sh",
    val syncTopic: String = "",
    val lastSyncId: String? = null,
    val remindersEnabled: Boolean = true,
    val onboarded: Boolean = false,
)

@Serializable
data class AppState(
    val version: Int = 1,
    val entries: List<Entry> = emptyList(),
    val sessions: List<FocusSession> = emptyList(),
    val active: ActiveSession? = null,
    val distractions: List<Distraction> = emptyList(),
    val habits: List<Habit> = emptyList(),
    /** habitId → 체크한 날짜들 */
    val habitChecks: Map<String, List<String>> = emptyMap(),
    val reviews: List<DayReview> = emptyList(),
    val weeklyTargets: List<WeeklyTarget> = emptyList(),
    val settings: Settings = Settings(),
)

object Ids {
    fun new(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

    fun topic(random: java.util.Random = java.security.SecureRandom()): String =
        "focusink-" + (1..14).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
}
