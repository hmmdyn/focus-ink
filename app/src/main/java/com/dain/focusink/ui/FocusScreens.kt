package com.dain.focusink.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val PREP = listOf(
    "휴대폰을 다른 방에 두었어요",
    "필요 없는 창을 닫았어요",
    "물을 준비했어요",
)

@Composable
fun FocusSetupScreen(state: AppState, now: Long, act: Actions) {
    val today = Dates.today(now)
    val s = state.settings
    var minutes by rememberSaveable { mutableIntStateOf(s.deepMinutes) }
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    var custom by rememberSaveable { mutableStateOf("") }
    var intention by rememberSaveable { mutableStateOf("") }
    var prep by rememberSaveable { mutableStateOf(listOf<Int>()) }

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

        Section("얼마나") {
            Gap(4.dp)
            Choice(listOf(90 to "90분", 50 to "50분", 25 to "25분"), minutes, { minutes = it })
        }

        Section("끝나면 무엇이 되어 있을까요") {
            InkField(intention, { intention = it }, "예: 3장 1절 초안 한 쪽", singleLine = false)
        }

        Section("시작하기 전에") {
            PREP.forEachIndexed { i, text ->
                InkRow({ prep = if (i in prep) prep - i else prep + i }, minHeight = 46.dp) { dark ->
                    Square(i in prep, size = 20.dp)
                    Spacer(Modifier.width(14.dp))
                    T(text, Type.body, color = if (dark) Paper else Ink)
                }
            }
        }

        Gap(28.dp)
        InkButton(
            if (label.isBlank()) "집중할 일을 골라 주세요" else "${minutes}분 집중 시작하기",
            { act.startFocus(label, minutes, intention, chosen?.id) },
            Modifier.fillMaxWidth(),
            filled = label.isNotBlank(),
            enabled = label.isNotBlank(),
            height = 60.dp,
            textSize = 19.sp,
        )
        T("처음 ${s.anchorMinutes}분 동안은 중간에 멈출 수 없어요.", Type.caption, modifier = Modifier.padding(top = 8.dp))

        val todays = state.sessions.filter { Dates.dateOf(it.startedAt) == today }
        if (todays.isNotEmpty()) {
            Section("오늘 집중한 시간") {
                todays.forEach { x ->
                    val mark = when (x.outcome) {
                        Outcome.COMPLETED -> "끝까지"
                        Outcome.ENDED_EARLY -> "일찍 끝냄"
                        Outcome.ABANDONED -> "그만둠"
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        T(Dates.hhmm(x.startedAt), Type.body.copy(color = Muted), modifier = Modifier.width(64.dp))
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
        if (a.intention.isNotBlank()) T(a.intention, Type.body.copy(color = Muted))
        Gap(24.dp)

        if (phase == Phase.OVERTIME) {
            T("+${Focus.elapsedMinutes(a, now) - a.plannedMinutes}", Type.hero)
            T("분 더 했어요", Type.lead)
        } else {
            T("${Focus.remainingMinutes(a, now)}", Type.hero)
            T("분 남았어요 · ${Dates.hhmm(Focus.endsAt(a))}에 끝나요", Type.lead)
        }
        Gap(16.dp)
        Bar(Focus.progress(a, now))
        if (phase == Phase.ANCHOR) {
            T(
                "${Focus.anchorRemainingMinutes(a, now)}분 뒤부터 멈출 수 있어요. 다른 창은 열지 말아요.",
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
                    T("하고 싶은 마음은 대개 금방 지나가요. 물을 마시거나 몸을 펴 보세요.", Type.body)
                    Gap(14.dp)
                    if (!urgeLost) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InkButton("괜찮아졌어요", {
                                act.update { Focus.resolveUrge(it, act.now(), passed = true, category = DistractionCategory.PHONE) }
                            }, Modifier.weight(1f), filled = true)
                            InkButton("결국 했어요", { urgeLost = true }, Modifier.weight(1f))
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
                InkLink("취소", { picking = false }, style = Type.caption.copy(color = Ink))
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton("딴짓하고 싶어요", { act.update { Focus.startUrge(it, act.now()) } }, Modifier.weight(1f))
                    InkButton("딴짓했어요", { picking = true }, Modifier.weight(1f))
                }
            }
        }
        if (lost + won > 0) {
            T(listOfNotNull(if (lost > 0) "딴짓 ${lost}번" else null, if (won > 0) "참음 ${won}번" else null).joinToString(" · "), Type.caption, modifier = Modifier.padding(top = 10.dp))
        }

        if (linked != null && linked.status == EntryStatus.OPEN) {
            Gap(10.dp)
            InkLink("‘${linked.text}’ 끝냈어요", { act.update { Journal.markDone(it, linked.id, act.now()) } }, style = Type.body)
        }

        Gap(36.dp)
        when (phase) {
            Phase.ANCHOR -> ConfirmButton("그래도 그만두기", "한 번 더 누르면 그만둬요", {
                act.finishFocus(Outcome.ABANDONED)?.let(onFinished)
            }, Modifier.fillMaxWidth())
            Phase.DEEP -> ConfirmButton("일찍 끝내기", "한 번 더 누르면 끝나요", {
                act.finishFocus(Outcome.ENDED_EARLY)?.let(onFinished)
            }, Modifier.fillMaxWidth())
            Phase.OVERTIME -> InkButton("마치고 돌아보기", {
                act.finishFocus(Outcome.COMPLETED)?.let(onFinished)
            }, Modifier.fillMaxWidth(), filled = true, height = 60.dp, textSize = 19.sp)
        }
        Gap(24.dp)
    }
}

@Composable
fun CategoryGrid(onPick: (DistractionCategory) -> Unit) {
    DistractionCategory.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { c -> InkButton(c.label, { onPick(c) }, Modifier.weight(1f), height = 46.dp, textSize = 16.sp) }
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
    val linked = x.entryId?.let { id -> state.entries.firstOrNull { it.id == id } }
    var markDone by rememberSaveable { mutableStateOf(linked != null && linked.status == EntryStatus.OPEN && x.outcome == Outcome.COMPLETED) }

    if (!saved) {
        Screen {
            T(if (x.outcome == Outcome.ABANDONED) "이번에는 여기까지예요" else "수고했어요", Type.display)
            Gap(6.dp)
            val spent = if (x.actualMinutes < 1) "시작하고 바로 멈췄어요" else "${Stats.duration(x.actualMinutes)} 동안 집중했어요"
            T(
                spent + if (x.distractions > 0) ". 딴짓은 ${x.distractions}번 했어요." else ".",
                Type.lead,
            )
            Section("집중은 어땠나요") {
                Gap(4.dp)
                Choice(listOf(1 to "산만했어요", 2 to "보통이에요", 3 to "몰입했어요"), quality, { quality = it })
            }
            Section("한 줄 메모") {
                InkField(note, { note = it }, "끝낸 것이나 막힌 것을 적어 주세요", singleLine = false)
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
                    var s = Focus.reflect(it, x.id, quality, note)
                    if (markDone && linked != null) s = Journal.markDone(s, linked.id, act.now())
                    s
                }
                saved = true
            }, Modifier.fillMaxWidth(), filled = true, height = 56.dp)
        }
    } else {
        val breakEnd = x.endedAt + state.settings.breakMinutes * 60_000L
        val left = ((breakEnd - maxOf(now, x.endedAt) + 59_999) / 60_000).coerceAtLeast(0)
        Screen(scroll = false) {
            T(if (left > 0) "쉬는 시간" else "다 쉬었어요", Type.label)
            Gap(6.dp)
            T("$left", Type.hero)
            T(if (left > 0) "분 쉬어요" else "다음 집중을 시작해도 좋아요", Type.lead)
            Gap(20.dp)
            T("물을 마시고, 몸을 펴고, 창밖 먼 곳을 봐요. 휴대폰은 보지 않아요.", Type.body)
            Gap(36.dp)
            InkButton("다음 집중 준비하기", onDone, Modifier.fillMaxWidth(), filled = left <= 0)
            Gap(10.dp)
            InkButton("오늘은 여기까지", onDone, Modifier.fillMaxWidth())
        }
    }
}
