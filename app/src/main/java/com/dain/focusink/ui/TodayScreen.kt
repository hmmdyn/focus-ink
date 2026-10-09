package com.dain.focusink.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.*
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign

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

private val DOW_EN = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** 수첩의 한 쪽. 날짜를 넘기며 지난 기록도 본다. 맨 위에 그날의 판화가 찍힌다. */
@Composable
fun TodayScreen(state: AppState, now: Long, act: Actions, open: (Overlay) -> Unit, goTab: (Tab) -> Unit) {
    val today = Dates.today(now)
    var date by rememberSaveable { mutableStateOf(today) }
    val isToday = date == today
    var adding by rememberSaveable { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    val goal = state.settings.dailyGoalMinutes
    val stat = Stats.day(state, date)
    val live = if (isToday) state.active?.let { a -> Focus.elapsedMinutes(a, now).coerceAtMost(a.plannedMinutes) } ?: 0 else 0
    val minutes = stat.deepMinutes + live

    Screen {
        // 머리말: 점 행렬 요일, 날짜와 쪽 넘김, 설정
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                DotText(DOW_EN[Dates.dayOfWeek(date) - 1], dot = 7.dp, gap = 2.dp)
                Gap(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PageTurn("‹") { date = Dates.plusDays(date, -1) }
                    T(Dates.long(date), Type.heading, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
                    PageTurn("›") { date = Dates.plusDays(date, 1) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                InkLink("설정", { open(Overlay.SETTINGS) }, underline = false, style = Type.body)
                T("오늘 점수", Type.label)
                T("${Score.day(state, date)}", Type.display)
            }
        }
        if (!isToday) {
            InkLink("오늘로 돌아가기", { date = today }, style = Type.caption)
        } else if (Guide.isManagerDay(state, today)) {
            T("오늘은 회의와 통화를 오후에 몰아 두는 날이에요.", Type.caption)
        }
        Gap(10.dp)

        // 오늘의 판화
        PrintCard(date, minutes, goal)

        // 지금 할 일 (오늘 쪽에만)
        if (isToday) {
            val next = Guide.next(state, now)
            Gap(14.dp)
            Boxed {
                Row(verticalAlignment = Alignment.Top) {
                    PixelIcon("coach", pixel = 3.dp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(next.title, Type.title)
                        if (next.detail.isNotBlank()) T(next.detail, Type.caption, modifier = Modifier.padding(top = 2.dp))
                        T(printLine(minutes, goal), Type.caption, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                val action: Pair<String, () -> Unit>? = when (next.kind) {
                    NextKind.MIGRATE -> "정리하기" to { open(Overlay.MIGRATE) }
                    NextKind.PLAN_TOP3 -> "고르기" to { open(Overlay.TOP3) }
                    NextKind.FIRST_BLOCK, NextKind.NEXT_BLOCK -> "집중 준비하기" to { goTab(Tab.FOCUS) }
                    NextKind.REVIEW -> "마무리하기" to { open(Overlay.REVIEW) }
                    else -> null
                }
                if (action != null) {
                    Gap(12.dp)
                    InkButton(action.first, action.second, Modifier.fillMaxWidth(), filled = true, height = 48.dp)
                }
            }

            // 바로 시작: 모드를 고르고 타이머 탭으로
            if (state.active == null) {
                Section("바로 시작") {
                    Gap(4.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Preset.SPRINT, Preset.POMODORO, Preset.DEEP50, Preset.DEEP90).forEach { p ->
                            QuickStart(p, Modifier.weight(1f)) {
                                act.update { it.copy(settings = it.settings.copy(lastPreset = p.id)) }
                                goTab(Tab.FOCUS)
                            }
                        }
                    }
                }
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
            if (entries.isEmpty() && !adding) T(if (isToday) "아직 적은 일이 없어요. 떠오르는 일부터 적어 볼까요?" else "이날은 적은 일이 없어요.", Type.caption, modifier = Modifier.padding(vertical = 10.dp))
            (top + rest).forEach { e ->
                val number = top.indexOfFirst { it.id == e.id }.takeIf { it >= 0 }?.plus(1)
                EntryLine(
                    e, number = number,
                    trailing = { InkLink("⋯", { menuFor = if (menuFor == e.id) null else e.id }, underline = false, style = Type.heading) },
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
                InkLink("+ 할 일 추가", { adding = true }, underline = false, style = Type.body)
            }
        }

        // 습관 (체크만. 자세한 기록은 습관 탭)
        val habits = Habits.active(state).filter { it.createdDate <= date }
        if (habits.isNotEmpty()) {
            Section("습관", trailing = { InkLink("전체 보기", { goTab(Tab.HABITS) }, style = Type.caption) }) {
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
                            if (hint != null) T(hint, Type.caption, color = c)
                        }
                        val streak = Habits.streak(state, h.id, date)
                        if (streak > 0) T("${streak}일째", Type.caption, color = c)
                    }
                }
            }
        }

        // 꼬리말
        Gap(26.dp)
        Rule()
        Gap(10.dp)
        T(Stats.summaryLine(stat), Type.caption)
        Distractions.weeklyTarget(state, date)?.let { w ->
            T("이번 주에 줄일 딴짓: ${w.category.label}", Type.caption)
        }
        Gap(20.dp)
    }
}

/** "다음 판까지 15분 남았어요" 같은 판화 안내 한 줄 */
fun printLine(minutes: Int, goal: Int): String {
    val passes = Print.passes(minutes, goal)
    return when {
        minutes >= goal -> "오늘 판화를 다 찍었어요! 이제부터는 보너스예요."
        minutes == 0 -> "${goal / Print.PASSES}분 집중하면 첫 판(줄기)이 찍혀요."
        else -> "${minutes}분 찍었어요. ${Print.PASS_NAMES[passes]} 판까지 ${Print.minutesToNextPass(minutes, goal)}분 남았어요."
    }
}

/** 오늘의 판화 카드: 그림 + 판 진행 칸 + 분 */
@Composable
fun PrintCard(date: String, minutes: Int, goal: Int) {
    val passes = Print.passes(minutes, goal)
    val progress = Print.progress(minutes, goal)
    Column(Modifier.fillMaxWidth().border(2.dp, Ink)) {
        Box {
            HalftonePrint(Print.seed(date), progress, Modifier.padding(6.dp), cell = 4.dp)
            T(if (minutes >= goal) "PRINTED" else "PASS ${(passes + if (progress * Print.PASSES > passes) 1 else 0).coerceIn(1, 4)}/4", Type.label,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Paper).border(1.5.dp, Ink).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Rule(strong = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("오늘의 판화", Type.bodyBold)
            Spacer(Modifier.width(8.dp))
            T(if (minutes >= goal) "완성" else Print.PASS_NAMES[passes.coerceAtMost(3)] + " 찍는 중", Type.caption, modifier = Modifier.weight(1f), maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until Print.PASSES) {
                    val f = (progress * Print.PASSES - i).coerceIn(0f, 1f)
                    Box(
                        Modifier.size(14.dp, 10.dp).border(1.5.dp, Ink)
                            .background(if (f >= 1f) androidx.compose.ui.graphics.SolidColor(Ink) else if (f > 0f) ditherBrush(8) else androidx.compose.ui.graphics.SolidColor(Paper)),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            T("$minutes/${goal}분", Type.small)
        }
    }
}

@Composable
private fun QuickStart(p: Preset, modifier: Modifier, onClick: () -> Unit) {
    val (source, pressed) = rememberPress()
    val c = if (pressed) Paper else Ink
    Column(
        modifier.heightIn(min = 72.dp).border(2.dp, Ink).background(if (pressed) Ink else Paper).inkClick(source, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotText("${p.minutes}", dot = 4.dp, gap = 1.dp, ghost = false, color = c)
        Gap(6.dp)
        T(p.label, Type.small, color = c)
    }
}

/** 오늘 쪽 습관 한 줄 아래에 붙는 안내. 놓친 날을 탓하지 않고 다음 행동 하나만 말한다. */
fun habitHint(state: AppState, h: Habit, today: String): String? {
    val small = h.tiny.ifBlank { null }
    val how = listOf(h.anchor, h.tiny).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    return when (Habits.status(state, h, today)) {
        HabitStatus.DONE -> when {
            Habits.cameBack(state, h, today) -> "다시 돌아왔어요! 다시 시작한 게 제일 중요해요."
            else -> milestone(Habits.streak(state, h.id, today))
        }
        HabitStatus.RESTING -> "오늘은 쉬어 가는 날이에요. 연속 기록은 그대로예요."
        HabitStatus.MUST_TODAY -> "오늘은 " + (small?.let { Hangul.josa(it, "이라도", "라도") } ?: "작게라도") + " 해 봐요. 두 번 연속만 빠지지 않으면 돼요."
        HabitStatus.LAPSED ->
            (if (Habits.isFreshStart(today)) "새로 시작하기 좋은 날이에요. " else "괜찮아요, 다시 하면 돼요. ") +
                (small?.let { "오늘은 ${Hangul.josa(it, "만", "만")} 해 봐요." } ?: "오늘은 아주 작게 해 봐요.")
        HabitStatus.PENDING -> how
    }
}

/** 연속 일수 이정표. 66일은 습관이 자리 잡는 데 걸린 중앙값이다 (Lally 외 2010). */
private fun milestone(streak: Int): String? = when (streak) {
    7 -> "7일째예요! 한 주를 꼬박 채웠어요."
    14, 21, 30, 50, 100 -> "${streak}일째예요! 여기까지 온 것 자체가 대단해요."
    66 -> "66일째예요! 습관이 자리 잡는 데 보통 걸리는 시간을 채웠어요."
    else -> null
}

@Composable
private fun PageTurn(glyph: String, onClick: () -> Unit) {
    InkLink(glyph, onClick, underline = false, style = Type.title)
}

@Composable
private fun EntryMenu(e: Entry, today: String, act: Actions, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (e.kind == EntryKind.TASK) {
            InkLink(if (e.priority) "중요 해제" else "중요", { act.update { Journal.togglePriority(it, e.id) }; onDone() }, style = Type.caption)
            if (e.date < today && e.status == EntryStatus.OPEN) {
                InkLink("오늘로", { act.update { Journal.migrate(it, e.id, today) }; onDone() }, style = Type.caption)
            }
            if (e.status != EntryStatus.CANCELLED) {
                InkLink("안 하기", { act.update { Journal.cancel(it, e.id) }; onDone() }, style = Type.caption)
            }
        }
        InkLink("지우기", { act.update { Journal.delete(it, e.id) }; onDone() }, style = Type.caption)
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
        InkField(text, { text = it }, "할 일을 적어 보세요", Modifier.weight(1f), onDone = { save() })
        Spacer(Modifier.width(10.dp))
        InkButton("추가", { save() }, height = 48.dp)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        T("- 로 시작하면 메모, * 로 시작하면 중요한 일이 돼요.", Type.caption, modifier = Modifier.weight(1f))
        if (onClose != null) InkLink("닫기", onClose, style = Type.caption)
    }
}

/** 목록 한 줄. 왼쪽 표시: 숫자 = 중요한 일, • 할 일, × 끝낸 일, – 메모 */
@Composable
fun EntryLine(e: Entry, number: Int? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    InkRow(onClick) { dark ->
        val closed = e.kind == EntryKind.TASK && e.status != EntryStatus.OPEN
        val c = if (dark) Paper else Ink
        val glyph = when {
            e.kind == EntryKind.NOTE -> "–"
            e.status == EntryStatus.DONE -> "×"
            e.status == EntryStatus.CANCELLED -> "~"
            number != null -> "$number"
            else -> "•"
        }
        T(glyph, if (number != null && !closed) Type.title else Type.heading, color = c, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            T(e.text, if (number != null && !closed) Type.bodyBold else Type.body, color = c, strike = closed)
            val meta = listOfNotNull(
                if (e.migrations > 0) "${e.migrations}번 옮겼어요" else null,
                if (e.source == Source.IPHONE) "아이폰에서 적었어요" else null,
            )
            if (meta.isNotEmpty()) T(meta.joinToString(" · "), Type.caption, color = if (dark) Paper else Ink)
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
        T(if (full) "세 가지를 모두 골랐어요." else "오늘 꼭 끝내고 싶은 일을 세 가지까지 골라 봐요.", Type.caption)
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
            T(if (handled > 0) "${handled}개를 정리했어요. 이제 오늘 할 일만 남았어요." else "남아 있는 지난 할 일이 없어요.", Type.caption)
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
