package com.dain.focusink.core

/**
 * 타이머 모드. 참고 앱들의 기본값을 따른다.
 * - 포모도로 25/5, 네 번째마다 긴 휴식 (Cirillo)
 * - 딥워크 50/10, 90분 몰입 (기존 Focus Ink 기본값)
 * - 스프린트 15: 시작이 어려울 때 처음 15분만 (도래기책방의 "처음 15분")
 * - 플로우: 스톱워치. 멈출 때까지 잰다 (열품타 스톱워치)
 */
enum class Preset(val id: String, val label: String, val minutes: Int, val detail: String) {
    POMODORO("pomo25", "포모도로", 25, "25분 집중하고 5분 쉬어요. 네 번째마다 15분"),
    DEEP50("deep50", "딥워크", 50, "50분 집중하고 10분 쉬어요"),
    DEEP90("deep90", "몰입", 90, "90분 집중하고 15분 쉬어요"),
    SPRINT("sprint15", "스프린트", 15, "시작이 어려울 때 15분만"),
    FLOW("flow", "플로우", 0, "멈출 때까지 재요"),
    CUSTOM("custom", "직접 정하기", 0, "5분에서 180분까지");

    val isFlow: Boolean get() = this == FLOW

    companion object {
        fun of(id: String?): Preset = entries.firstOrNull { it.id == id } ?: DEEP90

        /** 플로우는 상한 180분으로 잡고, 남은 시간 대신 지난 시간을 보여 준다 */
        const val FLOW_CAP = 180

        fun plannedMinutes(p: Preset, custom: Int): Int = when (p) {
            FLOW -> FLOW_CAP
            CUSTOM -> custom.coerceIn(5, 180)
            else -> p.minutes
        }

        /** 처음 멈출 수 없는 시간. 짧은 모드는 전체의 절반까지만 */
        fun anchorMinutes(planned: Int, base: Int): Int = minOf(base, planned / 2).coerceAtLeast(0)
    }
}

object Breaks {
    /**
     * 모드별 쉬는 시간. 포모도로는 오늘 끝까지 마친 포모도로가 4의 배수면 긴 휴식.
     * 나머지는 집중 시간의 1/6, 설정한 최소값보다 짧지 않게 (Albulescu 외 2022).
     */
    fun minutes(state: AppState, session: FocusSession, base: Int, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int {
        val p = Preset.of(session.preset)
        if (p == Preset.POMODORO) {
            val day = Dates.dateOf(session.startedAt, zone)
            val done = state.sessions.count {
                it.preset == Preset.POMODORO.id && it.outcome == Outcome.COMPLETED && Dates.dateOf(it.startedAt, zone) == day && it.startedAt <= session.startedAt
            }
            return if (session.outcome == Outcome.COMPLETED && done > 0 && done % 4 == 0) 15 else 5
        }
        val planned = if (p.isFlow) session.actualMinutes else session.plannedMinutes
        return Focus.breakMinutes(planned, base)
    }
}

/**
 * 오늘의 판화: 하루 목표의 25%마다 한 판(줄기 → 잎 → 꽃잎 → 꽃술)이 찍힌다.
 * Forest 의 나무 대신, 화면이 흑백인 e-ink 에 맞춘 시각 보상이다.
 */
object Print {
    const val PASSES = 4
    val PASS_NAMES = listOf("줄기", "잎", "꽃잎", "꽃술")

    fun progress(minutes: Int, goal: Int): Float = if (goal <= 0) 0f else (minutes.toFloat() / goal).coerceIn(0f, 1f)

    /** 다 찍은 판 수 0~4 */
    fun passes(minutes: Int, goal: Int): Int = (progress(minutes, goal) * PASSES + 1e-4f).toInt().coerceIn(0, PASSES)

    /** 다음 판까지 남은 분. 다 찍었으면 0 */
    fun minutesToNextPass(minutes: Int, goal: Int): Int {
        if (minutes >= goal) return 0
        val step = goal / PASSES.toDouble()
        val next = kotlin.math.ceil((minutes + 1) / step) * step
        return kotlin.math.ceil(next - minutes).toInt().coerceAtLeast(1)
    }

    /** 날짜마다 다른 꽃이 나오도록 하는 씨앗 */
    fun seed(date: String): Long = date.replace("-", "").toLongOrNull() ?: 0L
}

object Score {
    /**
     * 오늘 점수 0~100 (Opal 의 Focus Score 를 단순화).
     * 분량 60 · min(분/목표, 1) + 질 25 · (평균 집중도 − 1)/2 + 방해 관리 15 · max(0, 1 − 판당 딴짓/3)
     * 목표를 넘겨도 분량 점수는 60에서 멈춘다. 기록이 없으면 0.
     */
    fun day(state: AppState, date: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int {
        val ss = state.sessions.filter { Dates.dateOf(it.startedAt, zone) == date && it.outcome != Outcome.ABANDONED }
        if (ss.isEmpty()) return 0
        val minutes = ss.sumOf { it.actualMinutes }
        val q = ss.filter { it.quality > 0 }.map { it.quality }
        val avgQ = if (q.isEmpty()) 2.0 else q.average()
        val perBlock = ss.sumOf { it.distractions }.toDouble() / ss.size
        val goal = state.settings.dailyGoalMinutes.coerceAtLeast(1)
        val v = 60.0 * minOf(minutes.toDouble() / goal, 1.0) + 25.0 * (avgQ - 1) / 2 + 15.0 * maxOf(0.0, 1 - perBlock / 3)
        return kotlin.math.round(v).toInt().coerceIn(0, 100)
    }
}

/** 기간 챌린지 (챌린저스). 하루 목표 분을 채운 날을 자동으로 인증한다. */
object Challenge {
    enum class Day { PASSED, MISSED, TODAY, TODAY_PASSED, FUTURE }

    fun start(state: AppState, today: String): AppState = state.copy(settings = state.settings.copy(challengeStart = today))

    fun stop(state: AppState): AppState = state.copy(settings = state.settings.copy(challengeStart = null))

    fun days(state: AppState, today: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Pair<String, Day>> {
        val st = state.settings
        val start = st.challengeStart ?: return emptyList()
        return (0 until st.challengeDays).map { i ->
            val d = Dates.plusDays(start, i.toLong())
            val passed = Stats.day(state, d, zone).deepMinutes >= st.challengeMinutes
            d to when {
                d < today -> if (passed) Day.PASSED else Day.MISSED
                d == today -> if (passed) Day.TODAY_PASSED else Day.TODAY
                else -> Day.FUTURE
            }
        }
    }

    fun passedCount(days: List<Pair<String, Day>>): Int = days.count { it.second == Day.PASSED || it.second == Day.TODAY_PASSED }

    fun finished(state: AppState, today: String): Boolean {
        val start = state.settings.challengeStart ?: return false
        return today >= Dates.plusDays(start, state.settings.challengeDays.toLong())
    }
}
