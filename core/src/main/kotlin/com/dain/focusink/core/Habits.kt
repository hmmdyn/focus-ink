package com.dain.focusink.core

import kotlin.math.pow
import kotlin.math.roundToInt

enum class HabitStatus {
    /** 오늘 했다 */
    DONE,
    /** 오늘은 쉬어 가기로 했다 */
    RESTING,
    /** 아직 안 했다(어제는 했거나 새 습관) */
    PENDING,
    /** 어제 놓쳤다 → 오늘 놓치면 두 번 연속. 꼭 해야 함 */
    MUST_TODAY,
    /** 이틀 넘게 놓쳤다. 탓하지 않고 다시 시작하도록 안내한다 */
    LAPSED,
}

/**
 * 습관 규칙
 * - 하나, 작게, 정해 둔 때에 한다 (Gardner 외 2012).
 * - 한 번 빠지는 것은 괜찮다. 두 번 연속 빠지지 않는다 (Lally 외 2010: 한 번 놓친 것은 자동화에 거의 영향이 없다).
 * - 주 1회 "쉬어 가기"를 쓸 수 있다. 쉬는 날은 놓친 날로 세지 않는다 (Sharif & Shu 의 비상 예비분).
 * - 연속 일수와 함께 "익숙한 정도"를 보여 준다. 하루 빠져도 조금만 내려간다 (Loop Habit Tracker 의 지수 이동평균).
 */
object Habits {

    /** 한 주에 쓸 수 있는 쉬어 가기 횟수 */
    const val RESTS_PER_WEEK = 1

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

    fun isResting(state: AppState, id: String, date: String): Boolean =
        state.habitRests[id]?.contains(date) == true

    /** 했음 ↔ 안 함. 했다고 체크하면 그날의 쉬어 가기는 지운다. */
    fun toggle(state: AppState, id: String, date: String): AppState {
        val cur = state.habitChecks[id].orEmpty()
        val checking = date !in cur
        val next = if (checking) (cur + date).sorted() else cur - date
        var s = state.copy(habitChecks = state.habitChecks + (id to next))
        if (checking && isResting(s, id, date)) s = s.copy(habitRests = s.habitRests + (id to (s.habitRests[id].orEmpty() - date)))
        return s
    }

    /** 그 주(월~일)에 이미 쓴 쉬어 가기 횟수 */
    fun restsUsed(state: AppState, id: String, date: String): Int {
        val ws = Dates.weekStart(date)
        return state.habitRests[id].orEmpty().count { Dates.weekStart(it) == ws }
    }

    fun canRest(state: AppState, id: String, date: String): Boolean =
        !isChecked(state, id, date) && !isResting(state, id, date) && restsUsed(state, id, date) < RESTS_PER_WEEK

    /** 쉬어 가기 켜기/끄기. 이번 주에 이미 썼으면 켜지 않는다. */
    fun toggleRest(state: AppState, id: String, date: String): AppState {
        val cur = state.habitRests[id].orEmpty()
        return when {
            date in cur -> state.copy(habitRests = state.habitRests + (id to cur - date))
            canRest(state, id, date) -> state.copy(habitRests = state.habitRests + (id to (cur + date).sorted()))
            else -> state
        }
    }

    /** 놓친 날 = 습관이 있던 날인데 하지도 쉬지도 않은 날 */
    private fun missed(state: AppState, h: Habit, date: String): Boolean =
        date >= h.createdDate && !isChecked(state, h.id, date) && !isResting(state, h.id, date)

    /** 연속 일수. 쉬는 날은 건너뛰고(세지도 끊지도 않음), 오늘 아직 안 했으면 어제까지의 사슬을 센다. */
    fun streak(state: AppState, id: String, today: String): Int {
        val days = state.habitChecks[id].orEmpty().toSet()
        val rests = state.habitRests[id].orEmpty().toSet()
        var d = if (today in days || today in rests) today else Dates.plusDays(today, -1)
        var n = 0
        while (d in days || d in rests) {
            if (d in days) n++
            d = Dates.plusDays(d, -1)
        }
        return n
    }

    /** 오늘 직전까지 연속으로 놓친 날 수 */
    fun missedRun(state: AppState, h: Habit, today: String): Int {
        var n = 0
        var d = Dates.plusDays(today, -1)
        while (missed(state, h, d)) {
            n++
            d = Dates.plusDays(d, -1)
        }
        return n
    }

    fun status(state: AppState, habit: Habit, today: String): HabitStatus {
        if (isChecked(state, habit.id, today)) return HabitStatus.DONE
        if (isResting(state, habit.id, today)) return HabitStatus.RESTING
        return when (missedRun(state, habit, today)) {
            0 -> HabitStatus.PENDING
            1 -> HabitStatus.MUST_TODAY
            else -> HabitStatus.LAPSED
        }
    }

    /**
     * 놓친 뒤 오늘 다시 했는가. 돌아온 것을 알아봐 주는 것이 다음 날 복귀에 가장 큰 효과를 냈다
     * (Milkman 외 2021, 헬스장 6만 명 메가스터디).
     */
    fun cameBack(state: AppState, habit: Habit, today: String): Boolean =
        isChecked(state, habit.id, today) && missedRun(state, habit, today) >= 1

    /**
     * 새로 시작하기 좋은 날: 월요일이나 매달 1일 (Dai, Milkman & Riis 2014 의 새 출발 효과).
     */
    fun isFreshStart(date: String): Boolean = Dates.dayOfWeek(date) == 1 || date.endsWith("-01")

    /** 최근 days일 체크 여부(오래된 것부터). 습관 생성 전 날짜는 null. */
    fun grid(state: AppState, habit: Habit, today: String, days: Int = 21): List<Pair<String, Boolean?>> =
        Dates.lastDays(today, days).map { d ->
            d to (if (d < habit.createdDate) null else isChecked(state, habit.id, d))
        }

    /** 생성 이후 오늘 전날까지 "두 번 연속 놓침"이 몇 번 있었는지. 쉬는 날은 놓친 날이 아니다. */
    fun doubleMisses(state: AppState, habit: Habit, today: String): Int {
        var count = 0
        var run = 0
        var d = habit.createdDate
        while (d < today) {
            when {
                isChecked(state, habit.id, d) -> run = 0
                isResting(state, habit.id, d) -> Unit
                else -> {
                    run++
                    if (run == 2) count++
                }
            }
            d = Dates.plusDays(d, 1)
        }
        return count
    }

    /** 최근 n일(생성 이후, 쉬는 날 제외) 완료율 0~100 */
    fun rate(state: AppState, habit: Habit, today: String, n: Int = 30): Int {
        val days = Dates.lastDays(today, n).filter { it >= habit.createdDate && !isResting(state, habit.id, it) }
        if (days.isEmpty()) return 0
        return days.count { isChecked(state, habit.id, it) } * 100 / days.size
    }

    /** 매일 습관의 하루 감쇠율. Loop 의 0.5^(sqrt(빈도)/13), 빈도 = 1 이면 약 0.948 (반감기 13일) */
    val DAILY_MULTIPLIER: Double = 0.5.pow(1.0 / 13.0)

    /**
     * 익숙한 정도 0~100. 만든 날부터 하루씩 s = m·s + (1-m)·c 로 쌓는다(c = 했으면 1).
     * 쉬는 날은 그대로 두고, 오늘은 했을 때만 반영한다(오늘이 끝나기 전엔 놓친 게 아님).
     * 매일 하면 약 66일 뒤 97% 에 이른다. 습관이 자리 잡는 데 중앙값 66일 걸린 Lally 외 2010 과 맞춘 값이다.
     */
    fun strength(state: AppState, habit: Habit, today: String): Int {
        var s = 0.0
        var d = habit.createdDate
        while (d <= today) {
            val checked = isChecked(state, habit.id, d)
            when {
                isResting(state, habit.id, d) -> Unit
                d == today && !checked -> Unit
                else -> s = s * DAILY_MULTIPLIER + (if (checked) 1.0 else 0.0) * (1 - DAILY_MULTIPLIER)
            }
            d = Dates.plusDays(d, 1)
        }
        return (s * 100).roundToInt().coerceIn(0, 100)
    }
}
