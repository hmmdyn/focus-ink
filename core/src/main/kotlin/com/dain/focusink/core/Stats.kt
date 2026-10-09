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

    /**
     * 오늘을 빼고 지난 n일 동안 하루 평균 집중한 분. 기록이 있는 날이 하나도 없으면 null.
     * 계획을 세울 때 지난 기록을 함께 보여 주면 지나치게 낙관적인 계획이 줄어든다 (Buehler, Griffin & Ross 1994).
     */
    fun dailyAverageMinutes(state: AppState, today: String, n: Int = 7, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val days = (1..n).map { day(state, Dates.plusDays(today, -it.toLong()), zone) }
        if (days.none { it.deepMinutes > 0 }) return null
        return days.sumOf { it.deepMinutes } / n
    }

    fun hasReview(state: AppState, date: String): Boolean = state.reviews.any { it.date == date }

    /** 90 → "1시간 30분", 45 → "45분", 120 → "2시간" */
    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}분"
            m == 0 -> "${h}시간"
            else -> "${h}시간 ${m}분"
        }
    }

    fun summaryLine(stat: DayStat): String =
        "집중 ${duration(stat.deepMinutes)} · 딴짓 ${stat.distractions}번" +
            (if (stat.urgesResisted > 0) " · 참음 ${stat.urgesResisted}번" else "")
}

object Plans {
    /** 그날 첫 집중 시각을 정한다. 빈 값이나 잘못된 시각이면 지운다. */
    fun setFirstBlock(state: AppState, date: String, hhmm: String): AppState {
        val rest = state.plans.filterNot { it.date == date }
        val t = hhmm.trim()
        val ok = runCatching { java.time.LocalTime.parse(t) }.isSuccess
        return state.copy(plans = if (ok) rest + DayPlan(date, t) else rest)
    }

    fun firstBlock(state: AppState, date: String): String? = state.plans.firstOrNull { it.date == date }?.firstBlockAt
}

object Reviews {
    fun complete(state: AppState, date: String, note: String, now: Long): AppState {
        val rest = state.reviews.filterNot { it.date == date }
        return state.copy(reviews = rest + DayReview(date, note.trim(), now))
    }
}
