package com.dain.focusink.core

import java.time.ZoneId

data class DayStat(
    val date: String,
    val deepMinutes: Int,
    val blocks: Int,
    val distractions: Int,
    val urgesResisted: Int,
    val tasksDone: Int,
)

data class CategoryCount(val category: DistractionCategory, val count: Int)

object Stats {

    private fun countsAsDeep(s: FocusSession) = s.outcome != Outcome.ABANDONED

    fun day(state: AppState, date: String, zone: ZoneId = ZoneId.systemDefault()): DayStat {
        val sessions = state.sessions.filter { Dates.dateOf(it.startedAt, zone) == date }
        val ds = state.distractions.filter { Dates.dateOf(it.at, zone) == date }
        return DayStat(
            date = date,
            deepMinutes = sessions.filter(::countsAsDeep).sumOf { it.actualMinutes },
            blocks = sessions.count { it.outcome == Outcome.COMPLETED },
            distractions = ds.count { !it.resisted },
            urgesResisted = ds.count { it.resisted },
            tasksDone = state.entries.count { it.status == EntryStatus.DONE && it.doneAt != null && Dates.dateOf(it.doneAt, zone) == date },
        )
    }

    fun lastDays(state: AppState, today: String, n: Int = 7, zone: ZoneId = ZoneId.systemDefault()): List<DayStat> =
        Dates.lastDays(today, n).map { day(state, it, zone) }

    /** from~to(포함) 기간의 방해 원인 순위. 버틴 충동은 제외. */
    fun topCategories(state: AppState, from: String, to: String, zone: ZoneId = ZoneId.systemDefault()): List<CategoryCount> =
        state.distractions
            .filter { !it.resisted && Dates.dateOf(it.at, zone) in from..to }
            .groupingBy { it.category }.eachCount()
            .map { CategoryCount(it.key, it.value) }
            .sortedByDescending { it.count }

    /** 시작한 세션 중 계획을 채운 비율(0~100). 세션이 없으면 null */
    fun completionRate(state: AppState, from: String, to: String, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val s = state.sessions.filter { Dates.dateOf(it.startedAt, zone) in from..to }
        if (s.isEmpty()) return null
        return s.count { it.outcome == Outcome.COMPLETED } * 100 / s.size
    }

    fun averageQuality(state: AppState, from: String, to: String, zone: ZoneId = ZoneId.systemDefault()): Double? {
        val q = state.sessions.filter { Dates.dateOf(it.startedAt, zone) in from..to && it.quality > 0 }.map { it.quality }
        return if (q.isEmpty()) null else q.average()
    }

    fun hasReview(state: AppState, date: String): Boolean = state.reviews.any { it.date == date }

    fun summaryLine(stat: DayStat): String =
        "딥워크 ${stat.deepMinutes}분 · 블록 ${stat.blocks} · 방해 ${stat.distractions}" +
            (if (stat.urgesResisted > 0) " · 버틴 충동 ${stat.urgesResisted}" else "")
}

object Reviews {
    fun complete(state: AppState, date: String, note: String, now: Long): AppState {
        val rest = state.reviews.filterNot { it.date == date }
        return state.copy(reviews = rest + DayReview(date, note.trim(), now))
    }
}
