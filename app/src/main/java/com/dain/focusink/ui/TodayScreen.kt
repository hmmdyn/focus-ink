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
import com.dain.focusink.core.Distractions
import com.dain.focusink.core.Entry
import com.dain.focusink.core.EntryKind
import com.dain.focusink.core.EntryStatus
import com.dain.focusink.core.Guide
import com.dain.focusink.core.Habit
import com.dain.focusink.core.HabitStatus
import com.dain.focusink.core.Hangul
import com.dain.focusink.core.Habits
import com.dain.focusink.core.Journal
import com.dain.focusink.core.NextKind
import com.dain.focusink.core.Source
import com.dain.focusink.core.Stats

/** 수첩의 한 쪽. 날짜를 넘기며 지난 기록도 본다. */
@Composable
fun TodayScreen(state: AppState, now: Long, act: Actions, open: (Overlay) -> Unit, goTab: (Tab) -> Unit) {
    val today = Dates.today(now)
    var date by rememberSaveable { mutableStateOf(today) }
    val isToday = date == today
    var adding by rememberSaveable { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }

    Screen {
        // 머리말: 날짜와 쪽 넘김
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PageTurn("‹") { date = Dates.plusDays(date, -1) }
            T(Dates.long(date), Type.display.copy(fontSize = 25.sp), maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
            PageTurn("›") { date = Dates.plusDays(date, 1) }
            Spacer(Modifier.weight(1f))
            InkLink("설정", { open(Overlay.SETTINGS) }, underline = false, style = Type.body.copy(color = Muted))
        }
        if (!isToday) {
            InkLink("오늘로 돌아가기", { date = today }, style = Type.caption.copy(color = Muted))
        } else if (Guide.isManagerDay(state, today)) {
            T("오늘은 회의와 통화를 오후에 몰아 두는 날이에요.", Type.caption)
        }
        Gap(10.dp)
        Rule(strong = true)

        // 지금 할 일 (오늘 쪽에만)
        if (isToday) {
            val next = Guide.next(state, now)
            Gap(22.dp)
            T(next.title, Type.title)
            if (next.detail.isNotBlank()) T(next.detail, Type.body.copy(color = Muted), modifier = Modifier.padding(top = 2.dp))
            val action: Pair<String, () -> Unit>? = when (next.kind) {
                NextKind.MIGRATE -> "정리하기" to { open(Overlay.MIGRATE) }
                NextKind.PLAN_TOP3 -> "고르기" to { open(Overlay.TOP3) }
                NextKind.FIRST_BLOCK, NextKind.NEXT_BLOCK -> "집중 준비하기" to { goTab(Tab.FOCUS) }
                NextKind.REVIEW -> "마무리하기" to { open(Overlay.REVIEW) }
                else -> null
            }
            if (action != null) {
                Gap(14.dp)
                InkButton(action.first, action.second, filled = true, height = 48.dp)
            }
        }

        // 할 일
        val entries = Journal.forDate(state, date)
        val top = Journal.topThree(state, date)
        val rest = entries.filterNot { e -> top.any { it.id == e.id } }
        val tasks = entries.filter { it.kind == EntryKind.TASK && it.status != EntryStatus.CANCELLED }
        Section(
            if (isToday) "오늘 할 일" else "이날 할 일",
            trailing = { if (tasks.isNotEmpty()) T("${tasks.count { it.status == EntryStatus.DONE }}/${tasks.size}", Type.caption) },
        ) {
            if (entries.isEmpty() && !adding) T(if (isToday) "아직 적은 일이 없어요." else "이날은 적은 일이 없어요.", Type.body.copy(color = Muted), modifier = Modifier.padding(vertical = 10.dp))
            (top + rest).forEach { e ->
                val number = top.indexOfFirst { it.id == e.id }.takeIf { it >= 0 }?.plus(1)
                EntryLine(
                    e, number = number,
                    trailing = { InkLink("⋯", { menuFor = if (menuFor == e.id) null else e.id }, underline = false, style = Type.heading.copy(color = Muted)) },
                    onClick = if (e.kind == EntryKind.TASK && e.status != EntryStatus.CANCELLED) {
                        { act.update { s -> Journal.toggleDone(s, e.id, act.now()) } }
                    } else null,
                )
                if (menuFor == e.id) EntryMenu(e, today, act) { menuFor = null }
            }
            val pending = if (isToday) Journal.pendingMigration(state, today).size else 0
            if (pending > 0 && Guide.next(state, now).kind != NextKind.MIGRATE) {
                InkLink("지난 할 일 ${pending}개 정리하기", { open(Overlay.MIGRATE) })
            }
            if (adding) {
                AddEntry(act, date) { adding = false }
            } else {
                InkLink("+ 할 일 추가", { adding = true }, underline = false, style = Type.body.copy(color = Muted))
            }
        }

        // 습관
        val habits = Habits.active(state).filter { it.createdDate <= date }
        if (habits.isNotEmpty()) {
            Section("습관") {
                habits.forEach { h ->
                    val done = Habits.isChecked(state, h.id, date)
                    val resting = Habits.isResting(state, h.id, date)
                    InkRow({ act.update { s -> Habits.toggle(s, h.id, date) } }) { dark ->
                        val c = if (dark) Paper else Ink
                        Square(done, rest = resting && !done)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            T(h.name, Type.body, color = c)
                            val hint = if (isToday) habitHint(state, h, today) else if (resting) "쉬어 간 날이에요." else null
                            if (hint != null) T(hint, Type.caption, color = if (dark) Paper else Muted)
                        }
                        val streak = Habits.streak(state, h.id, date)
                        if (streak > 0) T("${streak}일째", Type.caption, color = if (dark) Paper else Muted)
                    }
                }
            }
        }

        // 꼬리말
        Gap(28.dp)
        Rule()
        Gap(10.dp)
        T(Stats.summaryLine(Stats.day(state, date)), Type.caption)
        Distractions.weeklyTarget(state, date)?.let { w ->
            T("이번 주에 줄일 딴짓: ${w.category.label}", Type.caption)
        }
        Gap(20.dp)
    }
}

/** 오늘 쪽 습관 한 줄 아래에 붙는 안내. 놓친 날을 탓하지 않고 다음 행동 하나만 말한다. */
fun habitHint(state: AppState, h: Habit, today: String): String? {
    val small = h.tiny.ifBlank { null }
    val how = listOf(h.anchor, h.tiny).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    return when (Habits.status(state, h, today)) {
        HabitStatus.DONE -> if (Habits.cameBack(state, h, today)) "다시 돌아왔어요. 이게 제일 중요해요." else null
        HabitStatus.RESTING -> "오늘은 쉬어 가는 날이에요. 연속 기록은 그대로예요."
        HabitStatus.MUST_TODAY -> "어제 못 했어요. 오늘은 " + (small?.let { Hangul.josa(it, "이라도", "라도") } ?: "작게라도") + " 해요."
        HabitStatus.LAPSED ->
            (if (Habits.isFreshStart(today)) "새로 시작하기 좋은 날이에요. " else "괜찮아요, 다시 하면 돼요. ") +
                (small?.let { "오늘은 ${Hangul.josa(it, "만", "만")} 해 봐요." } ?: "오늘은 아주 작게 해 봐요.")
        HabitStatus.PENDING -> how
    }
}

@Composable
private fun PageTurn(glyph: String, onClick: () -> Unit) {
    InkLink(glyph, onClick, underline = false, style = Type.display.copy(color = Muted, fontSize = 25.sp))
}

@Composable
private fun EntryMenu(e: Entry, today: String, act: Actions, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (e.kind == EntryKind.TASK) {
            InkLink(if (e.priority) "중요 해제" else "중요", { act.update { Journal.togglePriority(it, e.id) }; onDone() }, style = Type.caption.copy(color = Ink))
            if (e.date < today && e.status == EntryStatus.OPEN) {
                InkLink("오늘로", { act.update { Journal.migrate(it, e.id, today) }; onDone() }, style = Type.caption.copy(color = Ink))
            }
            if (e.status != EntryStatus.CANCELLED) {
                InkLink("안 하기", { act.update { Journal.cancel(it, e.id) }; onDone() }, style = Type.caption.copy(color = Ink))
            }
        }
        InkLink("지우기", { act.update { Journal.delete(it, e.id) }; onDone() }, style = Type.caption.copy(color = Ink))
    }
}

@Composable
fun AddEntry(act: Actions, date: String, onClose: (() -> Unit)? = null) {
    var text by rememberSaveable(date) { mutableStateOf("") }
    fun save() {
        if (text.isNotBlank()) {
            act.update { Journal.add(it, text, date, act.now()) }
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkField(text, { text = it }, "할 일을 적어 주세요", Modifier.weight(1f), onDone = { save() })
        Spacer(Modifier.width(10.dp))
        InkButton("추가", { save() }, height = 48.dp)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        T("- 로 시작하면 메모, * 로 시작하면 중요한 일이 돼요.", Type.caption, modifier = Modifier.weight(1f))
        if (onClose != null) InkLink("닫기", onClose, style = Type.caption.copy(color = Ink))
    }
}

/** 목록 한 줄. 왼쪽 표시: 숫자 = 중요한 일, • 할 일, × 끝낸 일, – 메모 */
@Composable
fun EntryLine(e: Entry, number: Int? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    InkRow(onClick) { dark ->
        val closed = e.kind == EntryKind.TASK && e.status != EntryStatus.OPEN
        val c = if (dark) Paper else if (closed) Muted else Ink
        val glyph = when {
            e.kind == EntryKind.NOTE -> "–"
            e.status == EntryStatus.DONE -> "×"
            e.status == EntryStatus.CANCELLED -> "~"
            number != null -> "$number"
            else -> "•"
        }
        T(glyph, if (number != null && !closed) Type.title.copy(fontSize = 20.sp) else Type.heading, color = c, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            T(e.text, if (number != null && !closed) Type.bodyBold else Type.body, color = c, strike = closed)
            val meta = listOfNotNull(
                if (e.migrations > 0) "${e.migrations}번 옮겼어요" else null,
                if (e.source == Source.IPHONE) "아이폰에서 적음" else null,
            )
            if (meta.isNotEmpty()) T(meta.joinToString(" · "), Type.caption, color = if (dark) Paper else Muted)
        }
        trailing?.invoke()
    }
}

/** 오늘 가장 중요한 일 세 가지 고르기 */
@Composable
fun Top3Screen(state: AppState, act: Actions, onClose: () -> Unit) {
    val today = act.today()
    val chosen = Journal.topThree(state, today)
    val candidates = Journal.forDate(state, today).filter { it.kind == EntryKind.TASK && it.status == EntryStatus.OPEN }
    var newText by rememberSaveable { mutableStateOf("") }
    val full = chosen.size >= 3

    Screen {
        TopBar("오늘 가장 중요한 일", onBack = onClose)
        Gap(6.dp)
        T(if (full) "세 가지를 모두 골랐어요." else "오늘 꼭 끝내고 싶은 일을 세 가지까지 골라 주세요.", Type.body.copy(color = Muted))
        // 계획할 때 지난 기록을 함께 보면 지나치게 낙관적인 계획이 줄어든다 (Buehler 외 1994)
        Stats.dailyAverageMinutes(state, today)?.let { avg ->
            Gap(8.dp)
            T("지난 7일 동안 하루 평균 ${Stats.duration(avg)} 집중했어요. 고른 일이 이 시간 안에 들어갈까요?", Type.caption)
        }
        Gap(10.dp)
        candidates.forEach { e ->
            val on = e.priority
            InkRow({ if (on || !full) act.update { s -> Journal.togglePriority(s, e.id) } }) { dark ->
                Square(on)
                Spacer(Modifier.width(14.dp))
                T(e.text, Type.body, color = if (dark) Paper else Ink, modifier = Modifier.weight(1f))
            }
        }
        if (!full) {
            Gap(6.dp)
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
        Gap(28.dp)
        InkButton("완료", onClose, Modifier.fillMaxWidth(), filled = true)
    }
}

/** 지난 할 일을 하나씩: 다시 적어 옮기기 / 이미 했음 / 하지 않기 */
@Composable
fun MigrateScreen(state: AppState, act: Actions, onClose: () -> Unit) {
    val today = act.today()
    val pending = Journal.pendingMigration(state, today)
    var rewrite by rememberSaveable { mutableStateOf("") }
    var handled by rememberSaveable { mutableStateOf(0) }

    Screen {
        TopBar("지난 할 일 정리", onBack = onClose)
        if (pending.isEmpty()) {
            Gap(28.dp)
            T("정리를 마쳤어요.", Type.title)
            T(if (handled > 0) "${handled}개를 정리했어요. 이제 오늘 할 일만 남았어요." else "남아 있는 지난 할 일이 없어요.", Type.body.copy(color = Muted))
            Gap(28.dp)
            InkButton("닫기", onClose, Modifier.fillMaxWidth(), filled = true)
            return@Screen
        }
        val e = pending.first()
        T("남은 일 ${pending.size}개", Type.caption, modifier = Modifier.padding(top = 4.dp))
        Gap(22.dp)
        T(Dates.long(e.date) + "에 적은 일" + if (e.migrations > 0) " · ${e.migrations}번 옮겼어요" else "", Type.caption)
        Gap(6.dp)
        T(e.text, Type.display)
        Gap(18.dp)
        T(
            if (e.migrations >= 2) "벌써 ${e.migrations}번 미뤘어요. 더 작게 나눠서 다시 적어 볼까요?"
            else "오늘도 할 일인가요? 옮기려면 지금 한 번 더 적어 보세요.",
            Type.lead,
        )
        Gap(6.dp)
        InkField(rewrite, { rewrite = it }, "비워 두면 그대로 옮겨요", singleLine = false)
        Gap(22.dp)
        InkButton("오늘 할 일로 옮기기", {
            act.update { Journal.migrate(it, e.id, today, rewrite) }
            rewrite = ""
            handled++
        }, Modifier.fillMaxWidth(), filled = true, height = 54.dp)
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkButton("이미 했어요", {
                act.update { Journal.markDone(it, e.id, act.now()) }
                rewrite = ""
                handled++
            }, Modifier.weight(1f))
            InkButton("하지 않을래요", {
                act.update { Journal.cancel(it, e.id) }
                rewrite = ""
                handled++
            }, Modifier.weight(1f))
        }
        Gap(24.dp)
    }
}
