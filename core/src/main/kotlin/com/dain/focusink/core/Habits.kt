package com.dain.focusink.core

enum class HabitStatus {
    /** 오늘 했다 */
    DONE,
    /** 아직 안 했다(어제는 했거나 새 습관) */
    PENDING,
    /** 어제 놓쳤다 → 오늘 놓치면 두 번 연속. 꼭 해야 함 */
    MUST_TODAY,
}

/** 카이젠 규칙: 하나, 작게, 사슬을 잇고, 두 번 연속 빠지지 않는다. */
object Habits {

    fun active(state: AppState): List<Habit> = state.habits.filterNot { it.archived }

    fun add(state: AppState, name: String, tiny: String, anchor: String, baseline: Boolean, today: String): AppState {
        val n = name.trim()
        if (n.isEmpty()) return state
        val h = Habit(Ids.new(), n, tiny.trim(), anchor.trim(), baseline, today)
        return state.copy(habits = state.habits + h)
    }

    fun archive(state: AppState, id: String): AppState =
        state.copy(habits = state.habits.map { if (it.id == id) it.copy(archived = true) else it })

    fun isChecked(state: AppState, id: String, date: String): Boolean =
        state.habitChecks[id]?.contains(date) == true

    fun toggle(state: AppState, id: String, date: String): AppState {
        val cur = state.habitChecks[id].orEmpty()
        val next = if (date in cur) cur - date else (cur + date).sorted()
        return state.copy(habitChecks = state.habitChecks + (id to next))
    }

    /** 연속 일수. 오늘 아직 안 했으면 어제까지의 사슬을 센다(오늘이 끝나기 전엔 끊긴 게 아님). */
    fun streak(state: AppState, id: String, today: String): Int {
        val days = state.habitChecks[id].orEmpty().toSet()
        var d = if (today in days) today else Dates.plusDays(today, -1)
        var n = 0
        while (d in days) {
            n++
            d = Dates.plusDays(d, -1)
        }
        return n
    }

    fun status(state: AppState, habit: Habit, today: String): HabitStatus {
        if (isChecked(state, habit.id, today)) return HabitStatus.DONE
        val yesterday = Dates.plusDays(today, -1)
        val existedYesterday = habit.createdDate <= yesterday
        return if (existedYesterday && !isChecked(state, habit.id, yesterday)) HabitStatus.MUST_TODAY else HabitStatus.PENDING
    }

    /** 최근 days일 체크 여부(오래된 것부터). 습관 생성 전 날짜는 null. */
    fun grid(state: AppState, habit: Habit, today: String, days: Int = 21): List<Pair<String, Boolean?>> =
        Dates.lastDays(today, days).map { d ->
            d to (if (d < habit.createdDate) null else isChecked(state, habit.id, d))
        }

    /** 생성 이후 오늘 전날까지 "두 번 연속 놓침"이 몇 번 있었는지 */
    fun doubleMisses(state: AppState, habit: Habit, today: String): Int {
        var count = 0
        var run = 0
        var d = habit.createdDate
        while (d < today) {
            if (isChecked(state, habit.id, d)) run = 0 else {
                run++
                if (run == 2) count++
            }
            d = Dates.plusDays(d, 1)
        }
        return count
    }

    /** 최근 n일(생성 이후만) 완료율 0~100 */
    fun rate(state: AppState, habit: Habit, today: String, n: Int = 30): Int {
        val days = Dates.lastDays(today, n).filter { it >= habit.createdDate }
        if (days.isEmpty()) return 0
        return days.count { isChecked(state, habit.id, it) } * 100 / days.size
    }
}
