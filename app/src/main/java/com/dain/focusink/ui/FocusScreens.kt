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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.DistractionCategory
import com.dain.focusink.core.Distractions
import com.dain.focusink.core.EntryKind
import com.dain.focusink.core.EntryStatus
import com.dain.focusink.core.Focus
import com.dain.focusink.core.Journal
import com.dain.focusink.core.Outcome
import com.dain.focusink.core.Phase
import com.dain.focusink.core.Stats
import kotlinx.coroutines.delay

/** 딴짓하고 싶어질 때 할 일. 미리 정해 두면(if-then 계획) 유혹이 와도 목표를 지키기 쉽다 (Gollwitzer & Sheeran 2006) */
private val IF_THEN_PRESETS = listOf(
    "할 일 목록에 적어 두고 돌아오기",
    "물 한 잔 마시고 자리로 돌아오기",
    "숨을 세 번 쉬고 하던 문장부터 다시 읽기",
)

private val PREP = listOf(
    "휴대폰을 다른 방에 두었어요",
    "필요 없는 창을 닫았어요",
    "물을 준비했어요",
)

@Composable
fun FocusSetupScreen(state: AppState, now: Long, act: Actions) {
    val today = Dates.today(now)
    val s = state.settings
    val preset = Preset.of(s.lastPreset)
    val minutes = Preset.plannedMinutes(preset, s.customMinutes)
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    var custom by rememberSaveable { mutableStateOf("") }
    var intention by rememberSaveable { mutableStateOf("") }
    var prep by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val ifThenOptions = (Focus.recentIfThens(state) + IF_THEN_PRESETS).distinct().take(3)
    var ifThen by rememberSaveable { mutableStateOf(ifThenOptions.first()) }
    var ifThenCustom by rememberSaveable { mutableStateOf("") }

    val candidates = Journal.forDate(state, today)
        .filter { it.kind == EntryKind.TASK && it.status == EntryStatus.OPEN }
        .sortedByDescending { it.priority }
        .take(5)
    val chosen = candidates.firstOrNull { it.id == entryId }
    val label = chosen?.text ?: custom.trim()

    Screen {
        T("무엇에 집중할까요?", Type.display)
        Gap(10.dp)
        candidates.forEach { e ->
            InkRow({
                entryId = if (entryId == e.id) null else e.id
                custom = ""
            }) { dark ->
                Square(e.id == entryId)
                Spacer(Modifier.width(14.dp))
                T(e.text, if (e.priority) Type.bodyBold else Type.body, color = if (dark) Paper else Ink, modifier = Modifier.weight(1f))
            }
        }
        InkField(custom, {
            custom = it
            if (it.isNotEmpty()) entryId = null
        }, if (candidates.isEmpty()) "집중할 일을 적어 주세요" else "다른 일을 직접 적기")

        // 지난번에 같은 일을 하다 남긴 "다음에 이어서 할 일"
        Focus.resumeNote(state, chosen?.id, label)?.let { prev ->
            Gap(14.dp)
            Boxed {
                T("지난번에 여기서 멈췄어요", Type.label)
                Gap(4.dp)
                T(prev.nextStep, Type.body)
                if (intention.isBlank()) {
                    InkLink("이걸로 시작하기", { intention = prev.nextStep }, style = Type.caption)
                }
            }
        }

        Section("모드") {
            PresetList(preset, s.customMinutes, onPick = { p ->
                act.update { it.copy(settings = it.settings.copy(lastPreset = p.id)) }
            }, onCustom = { m ->
                act.update { it.copy(settings = it.settings.copy(customMinutes = m.coerceIn(5, 180))) }
            })
        }

        Section("끝나면 무엇이 되어 있을까요") {
            InkField(intention, { intention = it }, "예: 3장 1절 초안 한 쪽", singleLine = false)
        }

        Section("딴짓하고 싶어지면") {
            ifThenOptions.forEach { o ->
                InkRow({
                    ifThen = o
                    ifThenCustom = ""
                }, minHeight = 48.dp) { dark ->
                    Square(ifThenCustom.isBlank() && ifThen == o, size = 20.dp)
                    Spacer(Modifier.width(14.dp))
                    T(o, Type.body, color = if (dark) Paper else Ink)
                }
            }
            InkField(ifThenCustom, { ifThenCustom = it }, "직접 정하기")
        }

        Section("시작하기 전에") {
            PREP.forEachIndexed { i, text ->
                InkRow({ prep = if (i in prep) prep - i else prep + i }, minHeight = 48.dp) { dark ->
                    Square(i in prep, size = 20.dp)
                    Spacer(Modifier.width(14.dp))
                    T(text, Type.body, color = if (dark) Paper else Ink)
                }
            }
        }

        Gap(28.dp)
        InkButton(
            if (label.isBlank()) "집중할 일을 골라 주세요" else if (preset.isFlow) "플로우 시작하기" else "${minutes}분 집중 시작하기",
            { act.startFocus(label, minutes, intention, chosen?.id, ifThenCustom.ifBlank { ifThen }, preset) },
            Modifier.fillMaxWidth(),
            filled = label.isNotBlank(),
            enabled = label.isNotBlank(),
            height = 60.dp,
                    )
        T("처음 ${Preset.anchorMinutes(minutes, s.anchorMinutes)}분은 중간에 멈출 수 없어요. 그 고비만 넘기면 돼요.", Type.caption, modifier = Modifier.padding(top = 8.dp))

        val todays = state.sessions.filter { Dates.dateOf(it.startedAt) == today }
        if (todays.isNotEmpty()) {
            Section("오늘 집중한 시간") {
                todays.forEach { x ->
                    val mark = when (x.outcome) {
                        Outcome.COMPLETED -> "끝까지 했어요"
                        Outcome.ENDED_EARLY -> "일찍 끝냈어요"
                        Outcome.ABANDONED -> "그만뒀어요"
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        T(Dates.hhmm(x.startedAt), Type.small, modifier = Modifier.width(56.dp).padding(top = 4.dp))
                        T(x.label, Type.body, modifier = Modifier.weight(1f), maxLines = 1)
                        T("${x.actualMinutes}분 · $mark", Type.caption)
                    }
                }
            }
        }
        Gap(24.dp)
    }
}

@Composable
fun FocusRunScreen(state: AppState, now: Long, act: Actions, onFinished: (String) -> Unit) {
    val a = state.active ?: return
    val s = state.settings
    val phase = Focus.phase(a, now)
    var picking by rememberSaveable { mutableStateOf(false) }
    var urgeLost by rememberSaveable { mutableStateOf(false) }

    // e-ink 는 멈춘 화면에 전력을 거의 쓰지 않으므로 집중하는 동안 화면을 켜 둔다
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // 아이폰에서 보낸 기록을 1분마다 가져온다
    LaunchedEffect(a.id) {
        while (true) {
            act.syncNow()
            delay(60_000)
        }
    }

    val ds = Focus.sessionDistractions(state, a.id)
    val lost = ds.count { !it.resisted }
    val won = ds.count { it.resisted }
    val linked = a.entryId?.let { id -> state.entries.firstOrNull { it.id == id } }

    Screen {
        T(
            when (phase) {
                Phase.ANCHOR -> "처음 ${a.anchorMinutes}분은 자리를 지켜요"
                Phase.DEEP -> "집중하는 중"
                Phase.OVERTIME -> "계획한 시간이 끝났어요"
            },
            Type.label,
        )
        Gap(6.dp)
        T(a.label, Type.title, maxLines = 2)
        if (a.intention.isNotBlank()) T(a.intention, Type.caption)
        Gap(22.dp)

        val flow = Preset.of(a.preset).isFlow && a.preset.isNotEmpty()
        val elapsed = Focus.elapsedMinutes(a, now)
        val big = when {
            flow -> "$elapsed"
            phase == Phase.OVERTIME -> "+${elapsed - a.plannedMinutes}"
            else -> "${Focus.remainingMinutes(a, now)}"
        }
        // 큰 숫자는 5×7 점 행렬. 1분마다만 바뀐다
        BigDots(big)
        Gap(10.dp)
        T(
            when {
                flow -> "분째 집중하고 있어요 · ${Dates.hhmm(a.startedAt)}에 시작했어요"
                phase == Phase.OVERTIME -> "분 더 했어요"
                else -> "분 남았어요 · ${Dates.hhmm(Focus.endsAt(a))}에 끝나요"
            },
            Type.lead,
        )
        Gap(14.dp)
        if (flow) {
            MinuteGrid(((elapsed / 30) + 1) * 30, elapsed)
        } else {
            MinuteGrid(a.plannedMinutes, elapsed.coerceAtMost(a.plannedMinutes), perRow = if (a.plannedMinutes > 60) 15 else 10)
        }
        if (phase == Phase.ANCHOR) {
            T(
                "${Focus.anchorRemainingMinutes(a, now)}분만 더 버텨 봐요. 그다음부터는 멈출 수 있어요.",
                Type.caption, modifier = Modifier.padding(top = 10.dp),
            )
        }

        val urge = a.urgeStartedAt
        Gap(28.dp)
        when {
            urge != null -> {
                val left = Focus.urgeRemainingMinutes(a, now, s.urgeMinutes)
                Boxed {
                    T(if (left > 0) "${left}분만 기다려 볼까요?" else "${s.urgeMinutes}분이 지났어요", Type.title)
                    Gap(4.dp)
                    if (a.ifThen.isNotBlank()) {
                        T("시작할 때 정해 둔 대로 해 봐요.", Type.body)
                        T(a.ifThen, Type.bodyBold)
                    } else {
                        T("하고 싶은 마음은 대개 금방 지나가요. 물을 마시거나 몸을 펴 보세요.", Type.body)
                    }
                    Gap(14.dp)
                    if (!urgeLost) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InkButton("괜찮아졌어요", {
                                act.update { Focus.resolveUrge(it, act.now(), passed = true, category = DistractionCategory.PHONE) }
                            }, Modifier.weight(1f), filled = true)
                            InkButton("딴짓했어요", { urgeLost = true }, Modifier.weight(1f))
                        }
                    } else {
                        T("무엇을 했나요?", Type.bodyBold)
                        Gap(6.dp)
                        CategoryGrid { c ->
                            act.update { Focus.resolveUrge(it, act.now(), passed = false, category = c) }
                            urgeLost = false
                        }
                    }
                }
            }
            picking -> {
                T("무엇이 집중을 끊었나요?", Type.bodyBold)
                Gap(8.dp)
                CategoryGrid { c ->
                    act.update { Distractions.log(it, c, act.now()) }
                    picking = false
                }
                InkLink("닫기", { picking = false }, style = Type.caption)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton("딴짓하고 싶어요", { act.update { Focus.startUrge(it, act.now()) } }, Modifier.weight(1f))
                    InkButton("딴짓했어요", { picking = true }, Modifier.weight(1f))
                }
            }
        }
        if (lost + won > 0) {
            T(listOfNotNull(if (lost > 0) "딴짓 ${lost}번" else null, if (won > 0) "${won}번 참았어요" else null).joinToString(" · "), Type.caption, modifier = Modifier.padding(top = 10.dp))
        }

        if (linked != null && linked.status == EntryStatus.OPEN) {
            Gap(10.dp)
            InkLink("‘${linked.text}’ 끝냈어요", { act.update { Journal.markDone(it, linked.id, act.now()) } }, style = Type.body)
        }

        Gap(36.dp)
        when (phase) {
            Phase.ANCHOR -> ConfirmButton("지금 그만두기", "한 번 더 누르면 그만둬요", {
                act.finishFocus(Outcome.ABANDONED)?.let(onFinished)
            }, Modifier.fillMaxWidth())
            Phase.DEEP -> if (flow) InkButton("마치고 돌아보기", {
                act.finishFocus(Outcome.COMPLETED)?.let(onFinished)
            }, Modifier.fillMaxWidth(), filled = true, height = 60.dp) else ConfirmButton("일찍 끝내기", "한 번 더 누르면 끝나요", {
                act.finishFocus(Outcome.ENDED_EARLY)?.let(onFinished)
            }, Modifier.fillMaxWidth())
            Phase.OVERTIME -> InkButton("마치고 돌아보기", {
                act.finishFocus(Outcome.COMPLETED)?.let(onFinished)
            }, Modifier.fillMaxWidth(), filled = true, height = 60.dp)
        }
        Gap(24.dp)
    }
}

@Composable
fun CategoryGrid(onPick: (DistractionCategory) -> Unit) {
    DistractionCategory.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { c -> InkButton(c.label, { onPick(c) }, Modifier.weight(1f), height = 48.dp) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun ReflectScreen(state: AppState, now: Long, sessionId: String, act: Actions, onDone: () -> Unit) {
    val x = state.sessions.firstOrNull { it.id == sessionId }
    if (x == null) {
        LaunchedEffect(sessionId) { onDone() }
        return
    }
    var saved by rememberSaveable { mutableStateOf(false) }
    var quality by rememberSaveable { mutableIntStateOf(x.quality.takeIf { it > 0 } ?: 2) }
    var note by rememberSaveable { mutableStateOf(x.reflection) }
    var nextStep by rememberSaveable { mutableStateOf(x.nextStep) }
    val linked = x.entryId?.let { id -> state.entries.firstOrNull { it.id == id } }
    var markDone by rememberSaveable { mutableStateOf(linked != null && linked.status == EntryStatus.OPEN && x.outcome == Outcome.COMPLETED) }

    if (!saved) {
        Screen {
            T(
                when (x.outcome) {
                    Outcome.COMPLETED -> "끝까지 해냈어요!"
                    Outcome.ENDED_EARLY -> "수고했어요"
                    Outcome.ABANDONED -> "이번에는 여기까지예요"
                },
                Type.display,
            )
            Gap(6.dp)
            val spent = if (x.actualMinutes < 1) "시작하고 바로 멈췄어요" else "${Stats.duration(x.actualMinutes)} 동안 집중했어요"
            T(
                spent + if (x.distractions > 0) ". 딴짓은 ${x.distractions}번 했어요." else ".",
                Type.lead,
            )
            if (x.outcome == Outcome.ABANDONED) {
                T("괜찮아요. 다음에는 스프린트 15분만 해 봐요.", Type.caption, modifier = Modifier.padding(top = 6.dp))
            }
            Section("집중은 어땠나요") {
                Gap(4.dp)
                Choice(listOf(1 to "산만했어요", 2 to "보통이에요", 3 to "몰입했어요"), quality, { quality = it })
            }
            Section("한 줄 메모") {
                InkField(note, { note = it }, "끝낸 것이나 막힌 것을 한 줄로 적어 봐요", singleLine = false)
            }
            if (!markDone) {
                Section("다음에 이어서 할 일") {
                    InkField(nextStep, { nextStep = it }, "예: 2절 첫 문단부터 쓰기", singleLine = false)
                    T("다음에 같은 일로 집중할 때 다시 보여 드려요.", Type.caption, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (linked != null && linked.status == EntryStatus.OPEN) {
                Gap(10.dp)
                InkRow({ markDone = !markDone }) { dark ->
                    Square(markDone)
                    Spacer(Modifier.width(14.dp))
                    T("‘${linked.text}’ 끝냈어요", Type.body, color = if (dark) Paper else Ink)
                }
            }
            Gap(28.dp)
            InkButton("저장하기", {
                act.update {
                    var s = Focus.reflect(it, x.id, quality, note, if (markDone) "" else nextStep)
                    if (markDone && linked != null) s = Journal.markDone(s, linked.id, act.now())
                    s
                }
                saved = true
            }, Modifier.fillMaxWidth(), filled = true, height = 56.dp)
        }
    } else {
        val breakMin = Breaks.minutes(state, x, state.settings.breakMinutes)
        val breakEnd = x.endedAt + breakMin * 60_000L
        val left = ((breakEnd - maxOf(now, x.endedAt) + 59_999) / 60_000).coerceAtLeast(0)
        Screen(scroll = false) {
            T(if (left > 0) (if (breakMin >= 15) "긴 쉬는 시간" else "쉬는 시간") else "다 쉬었어요", Type.label)
            Gap(6.dp)
            BigDots("$left")
            Gap(10.dp)
            T(if (left > 0) "분 쉬어요" else "다음 집중을 시작해도 좋아요", Type.lead)
            Gap(20.dp)
            T("물을 마시고, 몸을 펴고, 창밖 먼 곳을 봐요. 휴대폰은 잠깐 미뤄 둬요.", Type.body)
            Gap(36.dp)
            InkButton("다음 집중 준비하기", onDone, Modifier.fillMaxWidth(), filled = left <= 0)
            Gap(10.dp)
            InkButton("오늘은 여기까지", onDone, Modifier.fillMaxWidth())
        }
    }
}


/** 화면 폭에 맞춘 큰 점 행렬 숫자 */
@Composable
fun BigDots(text: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (text.length * 6 - 1).coerceAtLeast(1)
        // 점 : 간격 = 4 : 1, 최대 점 16dp
        val unit = (maxWidth / (cols * 5f)).coerceAtMost(4.dp)
        DotText(text, dot = unit * 4, gap = unit)
    }
}

/** 타이머 모드 목록. 고른 줄은 흑백 반전 */
@Composable
fun PresetList(selected: Preset, custom: Int, onPick: (Preset) -> Unit, onCustom: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).border(2.dp, Ink)) {
        Preset.entries.forEachIndexed { i, p ->
            val on = p == selected
            val (source, pressed) = rememberPress()
            val dark = on xor pressed
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).background(if (dark) Ink else Paper).inkClick(source) { onPick(p) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val c = if (dark) Paper else Ink
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(16.dp).border(2.dp, c), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(6.dp).background(c))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(p.label, Type.bodyBold, color = c)
                    T(p.detail, Type.small, color = c)
                }
                T(
                    when (p) {
                        Preset.FLOW -> "∞"
                        Preset.CUSTOM -> "$custom"
                        else -> "${p.minutes}"
                    },
                    Type.title, color = c, modifier = Modifier.padding(end = 12.dp),
                )
            }
            if (i < Preset.entries.lastIndex) Box(Modifier.fillMaxWidth().height(1.5.dp).background(Ink))
        }
    }
    if (selected == Preset.CUSTOM) {
        Gap(8.dp)
        Row(Modifier.fillMaxWidth().border(2.dp, Ink), verticalAlignment = Alignment.CenterVertically) {
            InkButton("−5", { onCustom(custom - 5) }, Modifier.width(72.dp), height = 48.dp)
            T("${custom}분", Type.title, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
            InkButton("+5", { onCustom(custom + 5) }, Modifier.width(72.dp), height = 48.dp)
        }
    }
}
