package com.dain.focusink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Distractions
import com.dain.focusink.core.Entry
import com.dain.focusink.core.EntryKind
import com.dain.focusink.core.EntryStatus
import com.dain.focusink.core.Guide
import com.dain.focusink.core.HabitStatus
import com.dain.focusink.core.Habits
import com.dain.focusink.core.Journal
import com.dain.focusink.core.NextKind
import com.dain.focusink.core.Stats

@Composable
fun TodayScreen(state: AppState, now: Long, act: Actions, open: (Overlay) -> Unit, goTab: (Tab) -> Unit) {
    val today = Dates.today(now)
    val next = Guide.next(state, now)
    val manager = Guide.isManagerDay(state, today)

    Screen {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(Dates.pretty(today), Type.title)
                T(
                    if (manager) "매니저 데이 · 통화와 회의는 오후에 몰아서" else "메이커 데이 · 오전 3시간은 딥워크",
                    Type.caption,
                )
            }
            InkLink("설정", { open(Overlay.SETTINGS) })
        }
        Gap(14.dp)

        // 지금 할 일: 하나만
        Boxed(inverted = true) {
            T("지금", Type.label, color = Paper)
            T(next.title, Type.heading, color = Paper)
            T(next.detail, Type.body, color = Paper)
            val (label, action) = when (next.kind) {
                NextKind.MIGRATE -> "정리하기" to { open(Overlay.MIGRATE) }
                NextKind.PLAN_TOP3 -> "Top 3 정하기" to { open(Overlay.TOP3) }
                NextKind.FIRST_BLOCK, NextKind.NEXT_BLOCK -> "집중 준비" to { goTab(Tab.FOCUS) }
                NextKind.REVIEW -> "리뷰 시작" to { open(Overlay.REVIEW) }
                else -> null to {}
            }
            if (label != null) {
                Gap(10.dp)
                InkButton(label, action, Modifier.fillMaxWidth(), filled = false)
            }
        }

        Section("오늘 TOP 3", trailing = { InkLink("편집", { open(Overlay.TOP3) }, style = Type.caption) }) {
            val top = Journal.topThree(state, today)
            if (top.isEmpty()) {
                T("아직 없음 · 가장 중요한 세 가지만 고르세요", Type.caption)
            }
            top.forEachIndexed { i, e ->
                EntryLine(e, number = i + 1) { act.update { s -> Journal.toggleDone(s, e.id, act.now()) } }
            }
        }

        val habits = Habits.active(state)
        if (habits.isNotEmpty()) {
            Section("습관") {
                habits.forEach { h ->
                    val st = Habits.status(state, h, today)
                    val streak = Habits.streak(state, h.id, today)
                    InkRow({ act.update { s -> Habits.toggle(s, h.id, today) } }) { dark ->
                        Square(st == HabitStatus.DONE)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(h.name + if (h.baseline) "  · 기준선" else "", Type.bodyBold, color = if (dark) Paper else Ink)
                            val sub = when (st) {
                                HabitStatus.MUST_TODAY -> "어제 놓침 — 오늘은 꼭. 두 번 연속은 안 됩니다"
                                HabitStatus.DONE -> "완료 · ${streak}일 연속"
                                HabitStatus.PENDING -> listOf(h.anchor.takeIf { it.isNotBlank() }?.let { "$it →" }, h.tiny.ifBlank { null })
                                    .filterNotNull().joinToString(" ").ifBlank { "${streak}일 연속" }
                            }
                            T(sub, Type.caption, color = if (dark) Paper else if (st == HabitStatus.MUST_TODAY) Ink else Muted)
                        }
                    }
                }
            }
        }

        Section("오늘 기록") {
            val d = Stats.day(state, today)
            T(Stats.summaryLine(d), Type.body)
            val pending = Journal.pendingMigration(state, today).size
            if (pending > 0) {
                InkLink("옮겨 적기 대기 ${pending}개", { open(Overlay.MIGRATE) })
            }
            Distractions.weeklyTarget(state, today)?.let { w ->
                Gap(6.dp)
                T("이번 주 제거: ${w.category.label}", Type.bodyBold)
                if (w.plan.isNotBlank()) T(w.plan, Type.caption)
            }
        }

        Section("빠른 기록") {
            QuickCapture(act, today)
            T("앞에 - 를 붙이면 메모, * 를 붙이면 중요", Type.caption, modifier = Modifier.padding(top = 6.dp))
        }
        Gap(24.dp)
    }
}

@Composable
fun QuickCapture(act: Actions, date: String, placeholder: String = "떠오른 일을 바로 적기") {
    var text by rememberSaveable(date) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkField(text, { text = it }, placeholder, Modifier.weight(1f), onDone = {
            if (text.isNotBlank()) {
                act.update { Journal.add(it, text, date, act.now()) }
                text = ""
            }
        })
        Spacer(Modifier.width(10.dp))
        InkButton("적기", {
            if (text.isNotBlank()) {
                act.update { Journal.add(it, text, date, act.now()) }
                text = ""
            }
        }, height = 48.dp)
    }
}

/** 불렛저널 한 줄: • 할 일, × 완료, ~ 취소, – 메모 */
@Composable
fun EntryLine(e: Entry, number: Int? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    InkRow(onClick) { dark ->
        val c = if (dark) Paper else Ink
        val glyph = when {
            e.kind == EntryKind.NOTE -> "–"
            e.status == EntryStatus.DONE -> "×"
            e.status == EntryStatus.CANCELLED -> "~"
            else -> "•"
        }
        T(number?.let { "$it" } ?: glyph, Type.heading, color = c, modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            T(
                (if (e.priority && number == null) "* " else "") + e.text,
                if (e.priority) Type.bodyBold else Type.body,
                color = c,
                strike = e.status != EntryStatus.OPEN && e.kind == EntryKind.TASK,
            )
            val meta = listOfNotNull(
                if (e.migrations > 0) "${e.migrations}번 옮김" else null,
                if (e.source == com.dain.focusink.core.Source.IPHONE) "아이폰" else null,
                if (number != null && e.status == EntryStatus.DONE) "완료" else null,
            )
            if (meta.isNotEmpty()) T(meta.joinToString(" · "), Type.caption, color = if (dark) Paper else Muted)
        }
        trailing?.invoke()
    }
}

/** 오늘 Top 3 편집 */
@Composable
fun Top3Screen(state: AppState, act: Actions, onClose: () -> Unit) {
    val today = act.today()
    val existing = Journal.topThree(state, today)
    val tasks = Journal.forDate(state, today).filter { it.kind == EntryKind.TASK && it.status == EntryStatus.OPEN && !it.priority }
    var newText by rememberSaveable { mutableStateOf("") }

    Screen {
        TopBar("오늘 Top 3", onBack = onClose)
        T("오늘 반드시 앞으로 나아가게 할 세 가지. 나머지는 저널에.", Type.caption)
        Section("현재 ${existing.size}/3") {
            existing.forEachIndexed { i, e ->
                EntryLine(e, number = i + 1, trailing = {
                    InkLink("빼기", { act.update { s -> Journal.togglePriority(s, e.id) } })
                }, onClick = null)
            }
            if (existing.size < 3) {
                Gap()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkField(newText, { newText = it }, "새로 적기", Modifier.weight(1f), onDone = {
                        if (newText.isNotBlank()) {
                            act.update { Journal.setTopThree(it, today, listOf(newText), act.now()) }
                            newText = ""
                        }
                    })
                    Spacer(Modifier.width(10.dp))
                    InkButton("추가", {
                        if (newText.isNotBlank()) {
                            act.update { Journal.setTopThree(it, today, listOf(newText), act.now()) }
                            newText = ""
                        }
                    }, height = 48.dp)
                }
            }
        }
        if (existing.size < 3 && tasks.isNotEmpty()) {
            Section("오늘 할 일에서 고르기") {
                tasks.take(8).forEach { e ->
                    EntryLine(e) { act.update { s -> Journal.togglePriority(s, e.id) } }
                }
            }
        }
        Gap(20.dp)
        InkButton("완료", onClose, Modifier.fillMaxWidth(), filled = true)
    }
}
