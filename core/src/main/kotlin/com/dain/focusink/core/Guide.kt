package com.dain.focusink.core

import java.time.ZoneId

enum class NextKind { SETUP, IN_FOCUS, MIGRATE, PLAN_TOP3, FIRST_BLOCK, NEXT_BLOCK, REVIEW, SUNSET, DONE_FOR_TODAY }

data class NextAction(val kind: NextKind, val title: String, val detail: String)

/** "지금 무엇을 해야 하나"를 하나만 고른다. 오늘 화면 맨 위 한 줄. */
object Guide {

    fun next(state: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): NextAction {
        val s = state.settings
        val today = Dates.today(now, zone)
        val time = Dates.timeOf(now, zone)
        val a = state.active
        if (!s.onboarded) return NextAction(NextKind.SETUP, "시작 설정", "습관 하나와 리뷰 시간을 정합니다")
        if (a != null) {
            val left = Focus.remainingMinutes(a, now)
            return NextAction(
                NextKind.IN_FOCUS, "집중 중 · ${a.label}",
                if (left > 0) "${left}분 남음" else "계획 시간 완료 · 마무리하세요",
            )
        }
        val reviewed = Stats.hasReview(state, today)
        val sessionsToday = state.sessions.count { Dates.dateOf(it.startedAt, zone) == today }
        val afterSunset = !time.isBefore(Dates.parseTime(s.sunsetTime))
        val afterReview = !time.isBefore(Dates.parseTime(s.reviewTime))

        if (reviewed && afterSunset) return NextAction(NextKind.SUNSET, "디지털 선셋", "폰은 주차장에, 화면은 끄고 잠자리로")
        if (reviewed) return NextAction(NextKind.DONE_FOR_TODAY, "오늘 정리 끝", "내일 Top 3가 준비됐습니다")
        if (afterReview) return NextAction(NextKind.REVIEW, "저녁 리뷰", "결과 확인 → 미완료 정리 → 내일 Top 3")

        val pending = Journal.pendingMigration(state, today).size
        if (pending > 0 && sessionsToday == 0) {
            return NextAction(NextKind.MIGRATE, "지난 미완료 ${pending}개 정리", "옮길지 지울지 하나씩 결정")
        }
        if (Journal.topThree(state, today).isEmpty()) {
            return NextAction(NextKind.PLAN_TOP3, "오늘 Top 3 정하기", "가장 중요한 세 가지만")
        }
        if (sessionsToday == 0) {
            val first = Journal.topThree(state, today).firstOrNull { it.status == EntryStatus.OPEN }
            return NextAction(NextKind.FIRST_BLOCK, "첫 딥워크 시작", first?.text ?: "가장 어려운 일부터")
        }
        return NextAction(NextKind.NEXT_BLOCK, "다음 블록", "${s.breakMinutes}분 쉬었다면 다음 블록을 시작하세요")
    }

    fun isManagerDay(state: AppState, date: String): Boolean = Dates.dayOfWeek(date) in state.settings.managerDays
}
