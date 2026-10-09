package com.dain.focusink.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.EntryKind
import com.dain.focusink.core.EntryStatus
import com.dain.focusink.core.HabitStatus
import com.dain.focusink.core.Habits
import com.dain.focusink.core.Journal
import com.dain.focusink.core.Plans
import com.dain.focusink.core.Reviews
import com.dain.focusink.core.Stats

/** 하루 마무리: 오늘 한 일 → 남은 일 → 습관 → 한 줄 회고 → 내일 할 일 */
@Composable
fun ReviewScreen(state: AppState, now: Long, act: Actions, onClose: () -> Unit) {
    val today = Dates.today(now)
    val tomorrow = Dates.plusDays(today, 1)
    val stat = Stats.day(state, today)
    var note by rememberSaveable { mutableStateOf("") }
    var t1 by rememberSaveable { mutableStateOf("") }
    var t2 by rememberSaveable { mutableStateOf("") }
    var t3 by rememberSaveable { mutableStateOf("") }
    var done by rememberSaveable { mutableStateOf(false) }
    val tomorrowTop = Journal.topThree(state, tomorrow)
    var firstAt by rememberSaveable { mutableStateOf(Plans.firstBlock(state, tomorrow).orEmpty()) }
    val firstAtOk = firstAt.isBlank() || runCatching { java.time.LocalTime.parse(firstAt.trim()) }.isSuccess

    if (done) {
        Screen(scroll = false) {
            Gap(24.dp)
            T("오늘도 수고했어요", Type.display)
            Gap(8.dp)
            T(Stats.summaryLine(stat), Type.lead)
            Gap(28.dp)
            Rule()
            Gap(18.dp)
            T("이제 휴대폰은 침대 밖에서 충전하고, 화면 대신 책이나 일기로 하루를 마무리해요.", Type.body)
            Gap(8.dp)
            T(
                Plans.firstBlock(state, tomorrow)?.let { "내일은 ${it}에 첫 집중을 시작해요. 못 끝낸 일은 아침에 다시 정하면 돼요." }
                    ?: "못 끝낸 일은 적어 두었으니 내일 아침에 다시 정하면 돼요.",
                Type.body.copy(color = Muted),
            )
            Gap(36.dp)
            InkButton("닫기", onClose, Modifier.fillMaxWidth(), filled = true)
        }
        return
    }

    Screen {
        TopBar("하루 마무리", onBack = onClose)
        Gap(4.dp)
        T(Stats.summaryLine(stat), Type.lead)

        val top = Journal.topThree(state, today)
        if (top.isNotEmpty()) {
            Section("오늘 가장 중요했던 일") {
                top.forEachIndexed { i, e ->
                    EntryLine(e, number = i + 1) { act.update { s -> Journal.toggleDone(s, e.id, act.now()) } }
                }
            }
        }

        val topIds = top.map { it.id }.toSet()
        val open = Journal.forDate(state, today).filter { it.kind == EntryKind.TASK && it.status == EntryStatus.OPEN && it.id !in topIds }
        if (open.isNotEmpty()) {
            Section("남은 일") {
                open.take(6).forEach { e -> EntryLine(e) { act.update { s -> Journal.toggleDone(s, e.id, act.now()) } } }
                if (open.size > 6) T("그 밖에 ${open.size - 6}개가 더 있어요.", Type.caption)
                T("남은 일은 내일 아침에 다시 정해요. 지금은 신경 쓰지 않아도 돼요.", Type.caption, modifier = Modifier.padding(top = 6.dp))
            }
        }

        val habits = Habits.active(state).filter {
            val st = Habits.status(state, it, today)
            st != HabitStatus.DONE && st != HabitStatus.RESTING
        }
        if (habits.isNotEmpty()) {
            Section("아직 하지 않은 습관") {
                habits.forEach { h ->
                    InkRow({ act.update { Habits.toggle(it, h.id, today) } }) { dark ->
                        Square(false)
                        Spacer(Modifier.width(14.dp))
                        T(h.name + if (h.tiny.isNotBlank()) " · ${h.tiny}" else "", Type.body, color = if (dark) Paper else Ink, modifier = Modifier.weight(1f))
                    }
                    if (Habits.canRest(state, h.id, today)) {
                        InkLink("오늘은 쉬어 가기", { act.update { Habits.toggleRest(it, h.id, today) } }, modifier = Modifier.padding(start = 32.dp), style = Type.caption.copy(color = Ink))
                    }
                }
                T("지금 2분만 해도 이어져요. 할 수 없는 날은 일주일에 한 번 쉬어 가도 연속 기록이 끊기지 않아요.", Type.caption, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Section("한 줄 회고") {
            InkField(note, { note = it }, "오늘 잘한 일 하나를 적어 볼까요?", singleLine = false)
        }

        Section("내일 가장 중요한 일") {
            tomorrowTop.forEachIndexed { i, e -> EntryLine(e, number = i + 1, onClick = null) }
            if (tomorrowTop.size < 3) {
                InkField(t1, { t1 = it }, "첫 번째")
                if (tomorrowTop.size < 2) InkField(t2, { t2 = it }, "두 번째")
                if (tomorrowTop.size < 1) InkField(t3, { t3 = it }, "세 번째")
            }
        }

        Section("내일 첫 집중은 몇 시에") {
            InkField(firstAt, { firstAt = it }, "예: 09:00")
            T(
                if (firstAtOk) "정해 두면 내일 오늘 쪽 맨 위에 보여 드려요." else "09:00처럼 적어 주세요.",
                Type.caption, modifier = Modifier.padding(top = 6.dp),
            )
        }

        Gap(28.dp)
        InkButton("하루 마무리하기", {
            val n = act.now()
            act.update {
                var s = Journal.setTopThree(it, tomorrow, listOf(t1, t2, t3).take(3 - tomorrowTop.size), n)
                if (firstAtOk) s = Plans.setFirstBlock(s, tomorrow, firstAt)
                s = Reviews.complete(s, today, note, n)
                s
            }
            act.publish("오늘 하루를 마무리했어요. ${Stats.summaryLine(stat)}")
            act.flash()
            done = true
        }, Modifier.fillMaxWidth(), filled = true, height = 56.dp)
        Gap(24.dp)
    }
}
