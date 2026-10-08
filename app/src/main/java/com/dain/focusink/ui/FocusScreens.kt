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
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay

private val PREP = listOf(
    "폰은 다른 방(주차장)에 두었다",
    "필요 없는 탭과 창을 닫았다",
    "물을 준비했다",
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
        T("딥워크", Type.title)
        T("한 블록에 한 가지만. 처음 ${s.anchorMinutes}분은 닻: 무슨 일이 있어도 자리를 지킵니다.", Type.caption)

        Section("블록 길이") {
            Choice(listOf(90 to "90분", 50 to "50분", 25 to "25분"), minutes, { minutes = it })
        }

        Section("무엇을") {
            if (candidates.isEmpty()) T("오늘 저널에 할 일이 없어요. 직접 적으세요.", Type.caption)
            candidates.forEach { e ->
                InkRow({
                    entryId = if (entryId == e.id) null else e.id
                    custom = ""
                }) { dark ->
                    Square(e.id == entryId)
                    Spacer(Modifier.width(12.dp))
                    T((if (e.priority) "* " else "") + e.text, Type.body, color = if (dark) Paper else Ink, modifier = Modifier.weight(1f))
                }
            }
            InkField(custom, {
                custom = it
                if (it.isNotEmpty()) entryId = null
            }, "또는 직접 적기")
        }

        Section("이번 블록이 끝나면 무엇이 되어 있어야 하나") {
            InkField(intention, { intention = it }, "예: 3장 초안 1쪽 / 버그 원인 찾기", singleLine = false)
        }

        Section("준비") {
            PREP.forEachIndexed { i, text ->
                InkRow({ prep = if (i in prep) prep - i else prep + i }) { dark ->
                    Square(i in prep)
                    Spacer(Modifier.width(12.dp))
                    T(text, Type.body, color = if (dark) Paper else Ink)
                }
            }
        }

        Gap(20.dp)
        InkButton(
            if (label.isBlank()) "무엇을 할지 먼저 고르세요" else "시작 · ${minutes}분",
            { act.startFocus(label, minutes, intention, chosen?.id) },
            Modifier.fillMaxWidth(),
            filled = label.isNotBlank(),
            enabled = label.isNotBlank(),
            height = 64.dp,
            textSize = 22.sp,
        )

        val todays = state.sessions.filter { Dates.dateOf(it.startedAt) == today }
        if (todays.isNotEmpty()) {
            Section("오늘 블록") {
                todays.forEach { x ->
                    val mark = when (x.outcome) {
                        Outcome.COMPLETED -> "완료"
                        Outcome.ENDED_EARLY -> "일찍 끝"
                        Outcome.ABANDONED -> "중단"
                    }
                    T("${Dates.hhmm(x.startedAt)}  ${x.label} · ${x.actualMinutes}분 · $mark · 방해 ${x.distractions}", Type.body)
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
    var urgeFailedPick by rememberSaveable { mutableStateOf(false) }

    // e-ink 는 정지 화면에 전력을 거의 쓰지 않으므로 세션 중엔 화면을 켜 둔다
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // 세션 중 아이폰 기록을 1분마다 가져온다
    LaunchedEffect(a.id) {
        while (true) {
            act.syncNow()
            delay(60_000)
        }
    }

    val sessionDs = Focus.sessionDistractions(state, a.id)
    val lost = sessionDs.count { !it.resisted }
    val won = sessionDs.count { it.resisted }
    val linked = a.entryId?.let { id -> state.entries.firstOrNull { it.id == id } }

    Screen(scroll = true) {
        T(
            when (phase) {
                Phase.ANCHOR -> "닻 내리는 중"
                Phase.DEEP -> "딥워크"
                Phase.OVERTIME -> "계획 시간 완료"
            },
            Type.label,
        )
        T(a.label, Type.title, maxLines = 2)
        if (a.intention.isNotBlank()) T("→ ${a.intention}", Type.body)
        Gap(18.dp)

        val remaining = Focus.remainingMinutes(a, now)
        if (phase == Phase.OVERTIME) {
            T("+${Focus.elapsedMinutes(a, now) - a.plannedMinutes}", Type.hero)
            T("분 초과 · 정리하고 회고하세요", Type.heading)
        } else {
            T("$remaining", Type.hero)
            T("분 남음 · ${Dates.hhmm(Focus.endsAt(a))}에 끝", Type.heading)
        }
        Gap(12.dp)
        Bar(Focus.progress(a, now), height = 18.dp)
        if (phase == Phase.ANCHOR) {
            Gap(10.dp)
            Boxed {
                T("닻 ${Focus.anchorRemainingMinutes(a, now)}분 남음", Type.bodyBold)
                T("다른 창을 열지 말고 이 자리를 지키세요. 처음 15분이 지나면 집중이 따라옵니다.", Type.caption)
            }
        }

        // 충동 10분 버티기
        val urge = a.urgeStartedAt
        if (urge != null) {
            val left = Focus.urgeRemainingMinutes(a, now, s.urgeMinutes)
            Gap(16.dp)
            Boxed(inverted = true) {
                T("충동 서핑", Type.label, color = Paper)
                T(if (left > 0) "${left}분만 버텨 보세요" else "${s.urgeMinutes}분이 지났습니다", Type.heading, color = Paper)
                T("충동에는 유효기간이 있습니다. 물 한 잔, 스트레칭, 숨 다섯 번.", Type.body, color = Paper)
            }
            Gap(8.dp)
            if (!urgeFailedPick) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkButton("지나갔다", {
                        act.update { Focus.resolveUrge(it, act.now(), passed = true, category = DistractionCategory.PHONE) }
                    }, Modifier.weight(1f), filled = true)
                    InkButton("졌다", { urgeFailedPick = true }, Modifier.weight(1f))
                }
            } else {
                T("무엇에 졌나요?", Type.bodyBold)
                CategoryGrid { c ->
                    act.update { Focus.resolveUrge(it, act.now(), passed = false, category = c) }
                    urgeFailedPick = false
                }
            }
        } else if (picking) {
            Gap(16.dp)
            T("무엇이 흔들었나요?", Type.bodyBold)
            CategoryGrid { c ->
                act.update { Distractions.log(it, c, act.now()) }
                picking = false
            }
            InkLink("취소", { picking = false })
        } else {
            Gap(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("충동이 왔다", { act.update { Focus.startUrge(it, act.now()) } }, Modifier.weight(1f))
                InkButton("흔들림 기록", { picking = true }, Modifier.weight(1f))
            }
        }
        Gap(8.dp)
        T("방해 $lost · 버틴 충동 $won", Type.caption)

        if (linked != null && linked.status == EntryStatus.OPEN) {
            Gap(8.dp)
            InkLink("할 일 완료 표시: ${linked.text}", { act.update { Journal.markDone(it, linked.id, act.now()) } }, style = Type.body)
        }

        Gap(24.dp)
        when (phase) {
            Phase.ANCHOR -> {
                T("닻 구간에는 종료 버튼이 없습니다.", Type.caption, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Gap(8.dp)
                ConfirmButton("긴급 중단", "한 번 더 누르면 중단", {
                    act.finishFocus(Outcome.ABANDONED)?.let(onFinished)
                }, Modifier.fillMaxWidth())
            }
            Phase.DEEP -> ConfirmButton("일찍 끝내기", "한 번 더 누르면 종료", {
                act.finishFocus(Outcome.ENDED_EARLY)?.let(onFinished)
            }, Modifier.fillMaxWidth())
            Phase.OVERTIME -> InkButton("완료 · 회고하기", {
                act.finishFocus(Outcome.COMPLETED)?.let(onFinished)
            }, Modifier.fillMaxWidth(), filled = true, height = 64.dp, textSize = 22.sp)
        }
        Gap(24.dp)
    }
}

@Composable
fun CategoryGrid(onPick: (DistractionCategory) -> Unit) {
    DistractionCategory.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { c -> InkButton(c.label, { onPick(c) }, Modifier.weight(1f), height = 48.dp, textSize = 16.sp) }
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
            T("블록 끝", Type.label)
            T(x.label, Type.title)
            T("${x.actualMinutes}분 · 방해 ${x.distractions}", Type.heading)
            if (x.intention.isNotBlank()) {
                Gap()
                T("의도: ${x.intention}", Type.body)
            }
            Section("집중도") {
                Choice(listOf(1 to "흩어짐", 2 to "보통", 3 to "몰입"), quality, { quality = it })
            }
            Section("한 줄 회고") {
                InkField(note, { note = it }, "무엇을 끝냈나 / 무엇이 막았나", singleLine = false)
            }
            if (linked != null && linked.status == EntryStatus.OPEN) {
                Gap(8.dp)
                InkRow({ markDone = !markDone }) { dark ->
                    Square(markDone)
                    Spacer(Modifier.width(12.dp))
                    T("할 일 완료로 표시: ${linked.text}", Type.body, color = if (dark) Paper else Ink)
                }
            }
            Gap(20.dp)
            InkButton("저장하고 쉬기", {
                act.update {
                    var s = Focus.reflect(it, x.id, quality, note)
                    if (markDone && linked != null) s = Journal.markDone(s, linked.id, act.now())
                    s
                }
                saved = true
            }, Modifier.fillMaxWidth(), filled = true, height = 60.dp)
        }
    } else {
        val breakEnd = x.endedAt + state.settings.breakMinutes * 60_000L
        val left = ((breakEnd - maxOf(now, x.endedAt) + 59_999) / 60_000).coerceAtLeast(0)
        Screen(scroll = false) {
            T("휴식", Type.label)
            T(if (left > 0) "$left" else "0", Type.hero)
            T(if (left > 0) "분 쉬기" else "휴식 끝", Type.heading)
            Gap(16.dp)
            Boxed {
                T("물 마시기 · 스트레칭 · 창밖 멀리 보기", Type.bodyBold)
                T("폰은 보지 않기. 쉬는 동안 머리가 백그라운드에서 일합니다.", Type.caption)
            }
            Gap(24.dp)
            InkButton("다음 블록 준비", onDone, Modifier.fillMaxWidth(), filled = left <= 0)
            Gap(8.dp)
            InkButton("오늘 딥워크는 여기까지", onDone, Modifier.fillMaxWidth())
        }
    }
}
