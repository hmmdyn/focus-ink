package com.dain.focusink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Habit
import com.dain.focusink.core.HabitStatus
import com.dain.focusink.core.Habits

@Composable
fun HabitsScreen(state: AppState, now: Long, act: Actions) {
    val today = Dates.today(now)
    val habits = Habits.active(state)
    var adding by rememberSaveable { mutableStateOf(habits.isEmpty()) }

    Screen {
        T("습관", Type.title)
        T("한 번에 하나, 30일. 민망할 만큼 작게. 두 번 연속 빠지지 않기.", Type.caption)

        habits.forEach { h -> HabitCard(state, h, today, act) }

        if (habits.size >= 2) {
            Gap(10.dp)
            T("습관이 ${habits.size}개입니다. 새 습관은 지금 것이 자동이 된 뒤에 추가하세요.", Type.caption)
        }

        if (adding) {
            HabitForm(today, act) { adding = false }
        } else {
            Gap(16.dp)
            InkButton("+ 습관 추가", { adding = true }, Modifier.fillMaxWidth())
        }
        Gap(24.dp)
    }
}

@Composable
private fun HabitCard(state: AppState, h: Habit, today: String, act: Actions) {
    val st = Habits.status(state, h, today)
    val yesterday = Dates.plusDays(today, -1)
    var menu by rememberSaveable { mutableStateOf(false) }
    Section(h.name + if (h.baseline) " · 기준선" else "", trailing = { InkLink(if (menu) "닫기" else "⋯", { menu = !menu }, style = Type.caption) }) {
        if (h.anchor.isNotBlank() || h.tiny.isNotBlank()) {
            T(listOf(h.anchor.takeIf { it.isNotBlank() }?.let { "$it →" }, h.tiny.ifBlank { null }).filterNotNull().joinToString(" "), Type.body)
        }
        Gap(8.dp)
        // 21일 격자: 3줄 × 7칸, 오늘은 굵은 테두리
        Habits.grid(state, h, today, 21).chunked(7).forEach { week ->
            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { (d, v) -> Square(v, size = 30.dp, today = d == today) }
            }
        }
        Gap(8.dp)
        T(
            "${Habits.streak(state, h.id, today)}일 연속 · 30일 ${Habits.rate(state, h, today)}% · 두 번 연속 놓침 ${Habits.doubleMisses(state, h, today)}회",
            Type.caption,
        )
        if (st == HabitStatus.MUST_TODAY) {
            Gap(6.dp)
            Boxed(inverted = true) {
                T("어제 놓쳤어요. 오늘은 꼭 — 작은 버전이라도.", Type.bodyBold, color = Paper)
            }
        }
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkButton(
                if (st == HabitStatus.DONE) "오늘 완료 ✓" else "오늘 했다",
                { act.update { Habits.toggle(it, h.id, today) } },
                Modifier.weight(2f), filled = st != HabitStatus.DONE,
            )
            if (h.createdDate <= yesterday) {
                InkButton(
                    if (Habits.isChecked(state, h.id, yesterday)) "어제 ✓" else "어제",
                    { act.update { Habits.toggle(it, h.id, yesterday) } },
                    Modifier.weight(1f), textSize = 15.sp,
                )
            }
        }
        if (menu) {
            Gap(8.dp)
            ConfirmButton("보관하기 (기록은 남음)", "한 번 더 누르면 보관", { act.update { Habits.archive(it, h.id) } }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HabitForm(today: String, act: Actions, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var tiny by rememberSaveable { mutableStateOf("") }
    var anchor by rememberSaveable { mutableStateOf("") }
    var baseline by rememberSaveable { mutableStateOf(false) }
    Section("새 습관") {
        InkField(name, { name = it }, "습관 (예: 매일 독서)")
        InkField(tiny, { tiny = it }, "민망할 만큼 작은 버전 (예: 책 펴기)")
        InkField(anchor, { anchor = it }, "언제? ~한 후에 (예: 아침 커피 내린 후)")
        Gap(6.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkRow({ baseline = !baseline }, Modifier.weight(1f)) { dark ->
                Square(baseline)
                Spacer(Modifier.width(12.dp))
                Column {
                    T("최소 기준선", Type.bodyBold, color = if (dark) Paper else Ink)
                    T("나쁜 날에도 지킬 바닥", Type.caption, color = if (dark) Paper else Muted)
                }
            }
        }
        Gap(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkButton("추가", {
                if (name.isNotBlank()) {
                    act.update { Habits.add(it, name, tiny, anchor, baseline, today) }
                    name = ""; tiny = ""; anchor = ""; baseline = false
                    onClose()
                }
            }, Modifier.weight(1f), filled = name.isNotBlank(), enabled = name.isNotBlank())
            InkButton("취소", onClose, Modifier.weight(1f))
        }
    }
}
