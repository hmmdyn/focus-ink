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
        if (!s.onboarded) return NextAction(NextKind.SETUP, "처음 설정부터 해 볼까요?", "")
        if (a != null) {
            val left = Focus.remainingMinutes(a, now)
            return NextAction(
                NextKind.IN_FOCUS, "${a.label}에 집중하고 있어요",
                if (left > 0) "${left}분 남았어요" else "계획한 시간이 끝났어요",
            )
        }
        val reviewed = Stats.hasReview(state, today)
        val sessionsToday = state.sessions.count { Dates.dateOf(it.startedAt, zone) == today }
        val afterSunset = !time.isBefore(Dates.parseTime(s.sunsetTime))
        val afterReview = !time.isBefore(Dates.parseTime(s.reviewTime))

        if (reviewed && afterSunset) return NextAction(NextKind.SUNSET, "휴대폰을 내려놓을 시간이에요", "화면을 끄고 책이나 일기로 하루를 마무리해요.")
        if (reviewed) return NextAction(NextKind.DONE_FOR_TODAY, "오늘 정리를 마쳤어요", "내일 할 일도 정해 두었어요. 이제 푹 쉬어요.")
        if (afterReview) return NextAction(NextKind.REVIEW, "하루를 마무리할 시간이에요", "3분이면 충분해요.")

        val pending = Journal.pendingMigration(state, today).size
        if (pending > 0 && sessionsToday == 0) {
            return NextAction(NextKind.MIGRATE, "지난 할 일 ${pending}개가 남아 있어요", "오늘도 할지 하나씩 정해 봐요.")
        }
        if (Journal.topThree(state, today).isEmpty()) {
            return NextAction(NextKind.PLAN_TOP3, "오늘 가장 중요한 일부터 정해 볼까요?", "세 가지까지 고를 수 있어요.")
        }
        if (sessionsToday == 0) {
            val first = Journal.topThree(state, today).firstOrNull { it.status == EntryStatus.OPEN }
            val planned = Plans.firstBlock(state, today)
            if (planned != null) {
                val late = time.isAfter(Dates.parseTime(planned))
                return NextAction(
                    NextKind.FIRST_BLOCK,
                    if (late) "${planned}에 시작하기로 했어요" else "${planned}에 첫 집중을 시작해요",
                    first?.text ?: "가장 어려운 일부터 시작해요.",
                )
            }
            return NextAction(NextKind.FIRST_BLOCK, "첫 집중을 시작해 볼까요?", first?.text ?: "가장 어려운 일부터 시작해요.")
        }
        return NextAction(NextKind.NEXT_BLOCK, "다음 집중을 시작해 볼까요?", "충분히 쉬었다면 이어서 시작해요.")
    }

    fun isManagerDay(state: AppState, date: String): Boolean = Dates.dayOfWeek(date) in state.settings.managerDays
}
