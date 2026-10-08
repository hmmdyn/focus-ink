package com.dain.focusink.core

enum class Phase {
    /** 첫 N분: 무슨 일이 있어도 자리를 지키는 구간 */
    ANCHOR,
    DEEP,
    /** 계획 시간을 넘김 */
    OVERTIME,
}

object Focus {
    private const val MIN = 60_000L

    fun start(
        state: AppState,
        label: String,
        minutes: Int,
        now: Long,
        intention: String = "",
        entryId: String? = null,
        anchorMinutes: Int = state.settings.anchorMinutes,
    ): AppState {
        if (state.active != null) return state
        val planned = minutes.coerceIn(5, 240)
        val active = ActiveSession(
            id = Ids.new(),
            label = label.trim().ifEmpty { "집중" },
            intention = intention.trim(),
            entryId = entryId,
            startedAt = now,
            plannedMinutes = planned,
            anchorMinutes = anchorMinutes.coerceIn(0, planned),
        )
        return state.copy(active = active)
    }

    fun elapsedMinutes(a: ActiveSession, now: Long): Int = ((now - a.startedAt).coerceAtLeast(0) / MIN).toInt()

    fun phase(a: ActiveSession, now: Long): Phase {
        val e = now - a.startedAt
        return when {
            e < a.anchorMinutes * MIN -> Phase.ANCHOR
            e < a.plannedMinutes * MIN -> Phase.DEEP
            else -> Phase.OVERTIME
        }
    }

    /** 남은 분(올림). 0 이하면 0. 화면에 "47분 남음"으로 쓴다. */
    fun remainingMinutes(a: ActiveSession, now: Long): Int {
        val left = a.startedAt + a.plannedMinutes * MIN - maxOf(now, a.startedAt)
        return if (left <= 0) 0 else ((left + MIN - 1) / MIN).toInt()
    }

    fun anchorRemainingMinutes(a: ActiveSession, now: Long): Int {
        val left = a.startedAt + a.anchorMinutes * MIN - maxOf(now, a.startedAt)
        return if (left <= 0) 0 else ((left + MIN - 1) / MIN).toInt()
    }

    fun endsAt(a: ActiveSession): Long = a.startedAt + a.plannedMinutes * MIN

    /** 진행률 0.0~1.0 */
    fun progress(a: ActiveSession, now: Long): Float =
        ((now - a.startedAt).toFloat() / (a.plannedMinutes * MIN)).coerceIn(0f, 1f)

    fun sessionDistractions(state: AppState, sessionId: String): List<Distraction> =
        state.distractions.filter { it.sessionId == sessionId }

    /**
     * 세션을 끝낸다. outcome 이 null 이면 경과 시간으로 판단:
     * 계획 시간을 채웠으면 COMPLETED, 닻 구간을 넘겼으면 ENDED_EARLY, 아니면 ABANDONED.
     */
    fun finish(
        state: AppState,
        now: Long,
        outcome: Outcome? = null,
        quality: Int = 0,
        reflection: String = "",
    ): AppState {
        val a = state.active ?: return state
        val resolved = outcome ?: when (phase(a, now)) {
            Phase.OVERTIME -> Outcome.COMPLETED
            Phase.DEEP -> Outcome.ENDED_EARLY
            Phase.ANCHOR -> Outcome.ABANDONED
        }
        val session = FocusSession(
            id = a.id,
            label = a.label,
            intention = a.intention,
            entryId = a.entryId,
            startedAt = a.startedAt,
            endedAt = now,
            plannedMinutes = a.plannedMinutes,
            outcome = resolved,
            quality = quality.coerceIn(0, 3),
            reflection = reflection.trim(),
            distractions = sessionDistractions(state, a.id).count { !it.resisted },
        )
        return state.copy(active = null, sessions = state.sessions + session)
    }

    /** 끝난 세션의 회고(집중도·메모)를 나중에 채운다. */
    fun reflect(state: AppState, sessionId: String, quality: Int, reflection: String): AppState =
        state.copy(
            sessions = state.sessions.map {
                if (it.id == sessionId) it.copy(quality = quality.coerceIn(0, 3), reflection = reflection.trim()) else it
            },
        )

    // ---- 충동 10분 버티기 ----

    fun startUrge(state: AppState, now: Long): AppState {
        val a = state.active ?: return state
        if (a.urgeStartedAt != null) return state
        return state.copy(active = a.copy(urgeStartedAt = now))
    }

    fun urgeRemainingMinutes(a: ActiveSession, now: Long, urgeMinutes: Int): Int {
        val s = a.urgeStartedAt ?: return 0
        val left = s + urgeMinutes * MIN - maxOf(now, s)
        return if (left <= 0) 0 else ((left + MIN - 1) / MIN).toInt()
    }

    /** passed = 충동이 지나갔다(버팀). 아니면 방해 1건으로 센다. */
    fun resolveUrge(state: AppState, now: Long, passed: Boolean, category: DistractionCategory, note: String = ""): AppState {
        val a = state.active ?: return state
        val d = Distraction(
            id = Ids.new(), at = a.urgeStartedAt ?: now, category = category, note = note.trim(),
            sessionId = a.id, resisted = passed,
        )
        return state.copy(active = a.copy(urgeStartedAt = null), distractions = state.distractions + d)
    }

    fun cancelUrge(state: AppState): AppState {
        val a = state.active ?: return state
        return state.copy(active = a.copy(urgeStartedAt = null))
    }
}

object Distractions {
    fun log(
        state: AppState,
        category: DistractionCategory,
        at: Long,
        note: String = "",
        source: Source = Source.APP,
        resisted: Boolean = false,
    ): AppState {
        val a = state.active
        val inSession = a != null && at >= a.startedAt
        val d = Distraction(
            id = Ids.new(), at = at, category = category, note = note.trim(), source = source,
            sessionId = if (inSession) a!!.id else null, resisted = resisted,
        )
        return state.copy(distractions = state.distractions + d)
    }

    fun delete(state: AppState, id: String): AppState = state.copy(distractions = state.distractions.filterNot { it.id == id })

    fun setWeeklyTarget(state: AppState, weekStart: String, category: DistractionCategory, plan: String): AppState {
        val rest = state.weeklyTargets.filterNot { it.weekStart == weekStart }
        return state.copy(weeklyTargets = rest + WeeklyTarget(weekStart, category, plan.trim()))
    }

    fun weeklyTarget(state: AppState, today: String): WeeklyTarget? {
        val ws = Dates.weekStart(today)
        return state.weeklyTargets.firstOrNull { it.weekStart == ws }
    }
}
