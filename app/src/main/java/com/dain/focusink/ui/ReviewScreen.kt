package com.dain.focusink.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.dain.focusink.core.Reviews
import com.dain.focusink.core.Stats

/** 저녁 리뷰: 결과 → 미완료 → 습관 → 한 줄 회고 → 내일 Top 3 */
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

    if (done) {
        Screen(scroll = false) {
            T("오늘 끝", Type.title)
            Gap()
            T(Stats.summaryLine(stat), Type.heading)
            Gap(20.dp)
            Boxed(inverted = true) {
                T("디지털 선셋", Type.label, color = Paper)
                T("폰은 침대 밖 주차장에.", Type.heading, color = Paper)
                T("미완료는 적어 뒀으니 머릿속에서 내려놓아도 됩니다. ${state.settings.sunsetTime} 이후엔 화면 대신 책.", Type.body, color = Paper)
            }
            Gap(24.dp)
            InkButton("닫기", onClose, Modifier.fillMaxWidth(), filled = true)
        }
        return
    }

    Screen {
        TopBar("저녁 리뷰", onBack = onClose)
        T("3분이면 됩니다. 정리된 머리가 잠을 깊게 합니다.", Type.caption)

        Section("1. 오늘 결과") {
            T(Stats.summaryLine(stat), Type.bodyBold)
            Journal.topThree(state, today).forEachIndexed { i, e ->
                EntryLine(e, number = i + 1) { act.update { s -> Journal.toggleDone(s, e.id, act.now()) } }
            }
        }

        val open = Journal.forDate(state, today).filter { it.kind == EntryKind.TASK && it.status == EntryStatus.OPEN }
        Section("2. 못 끝낸 일 ${open.size}개") {
            if (open.isEmpty()) T("없음. 깔끔합니다.", Type.caption)
            else {
                open.take(6).forEach { e -> EntryLine(e) { act.update { s -> Journal.toggleDone(s, e.id, act.now()) } } }
                if (open.size > 6) T("외 ${open.size - 6}개", Type.caption)
                T("남은 일은 내일 아침 '옮겨 적기'에서 하나씩 다시 결정합니다. 지금은 내려놓으세요.", Type.caption)
            }
        }

        val habits = Habits.active(state).filter { Habits.status(state, it, today) != HabitStatus.DONE }
        if (habits.isNotEmpty()) {
            Section("3. 아직 안 한 습관") {
                habits.forEach { h ->
                    InkRow({ act.update { Habits.toggle(it, h.id, today) } }) { dark ->
                        Square(false)
                        Spacer(Modifier.width(12.dp))
                        T("${h.name} — ${h.tiny.ifBlank { "지금 2분만" }}", Type.body, color = if (dark) Paper else Ink)
                    }
                }
            }
        }

        Section("4. 한 줄 회고") {
            InkField(note, { note = it }, "잘한 것 하나 / 배운 것 하나", singleLine = false)
        }

        Section("5. 내일 Top 3") {
            if (tomorrowTop.isNotEmpty()) {
                tomorrowTop.forEachIndexed { i, e -> EntryLine(e, number = i + 1, onClick = null) }
            }
            if (tomorrowTop.size < 3) {
                InkField(t1, { t1 = it }, "1. 가장 중요한 것")
                if (tomorrowTop.size < 2) InkField(t2, { t2 = it }, "2.")
                if (tomorrowTop.size < 1) InkField(t3, { t3 = it }, "3.")
            }
        }

        Gap(20.dp)
        InkButton("리뷰 완료", {
            val n = act.now()
            act.update {
                var s = Journal.setTopThree(it, tomorrow, listOf(t1, t2, t3).take(3 - tomorrowTop.size), n)
                s = Reviews.complete(s, today, note, n)
                s
            }
            act.publish("오늘 · ${Stats.summaryLine(stat)}")
            act.flash()
            done = true
        }, Modifier.fillMaxWidth(), filled = true, height = 60.dp)
        Gap(24.dp)
    }
}
