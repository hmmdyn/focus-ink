package com.dain.focusink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.EntryKind
import com.dain.focusink.core.EntryStatus
import com.dain.focusink.core.Journal

@Composable
fun JournalScreen(state: AppState, now: Long, act: Actions, openMigrate: () -> Unit) {
    val today = Dates.today(now)
    var date by rememberSaveable { mutableStateOf(today) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = Journal.forDate(state, date)
    val pending = Journal.pendingMigration(state, today)

    Screen {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InkButton("◀", { date = Dates.plusDays(date, -1) }, height = 48.dp)
            T(
                Dates.pretty(date) + if (date == today) " · 오늘" else "",
                Type.heading, align = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            InkButton("▶", { date = Dates.plusDays(date, 1) }, height = 48.dp)
        }
        if (date != today) InkLink("오늘로", { date = today })

        if (date == today && pending.isNotEmpty()) {
            Gap(10.dp)
            Boxed(inverted = true) {
                T("지난 미완료 ${pending.size}개", Type.heading, color = Paper)
                T("옮겨 적을 가치가 있는지 하나씩 결정하세요", Type.body, color = Paper)
                Gap(8.dp)
                InkButton("옮겨 적기", openMigrate, Modifier.fillMaxWidth())
            }
        }

        Section("로그 ${entries.count { it.kind == EntryKind.TASK && it.status == EntryStatus.DONE }}/${entries.count { it.kind == EntryKind.TASK && it.status != EntryStatus.CANCELLED }}") {
            if (entries.isEmpty()) T("비어 있음", Type.caption)
            Paged(entries, pageSize = 8, resetKey = date) { e ->
                EntryLine(
                    e,
                    trailing = { InkLink(if (editing == e.id) "닫기" else "⋯", { editing = if (editing == e.id) null else e.id }) },
                    onClick = if (e.kind == EntryKind.TASK && e.status != EntryStatus.CANCELLED) {
                        { act.update { s -> Journal.toggleDone(s, e.id, act.now()) } }
                    } else null,
                )
                if (editing == e.id) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (e.kind == EntryKind.TASK) {
                            InkButton(if (e.priority) "중요 해제" else "중요", { act.update { s -> Journal.togglePriority(s, e.id) } }, Modifier.weight(1f), height = 44.dp, textSize = 15.sp)
                            InkButton("취소", { act.update { s -> Journal.cancel(s, e.id) }; editing = null }, Modifier.weight(1f), height = 44.dp, textSize = 15.sp)
                            if (date != today) {
                                InkButton("오늘로", { act.update { s -> Journal.migrate(s, e.id, today) }; editing = null }, Modifier.weight(1f), height = 44.dp, textSize = 15.sp)
                            }
                        }
                        InkButton("삭제", { act.update { s -> Journal.delete(s, e.id) }; editing = null }, Modifier.weight(1f), height = 44.dp, textSize = 15.sp)
                    }
                }
            }
        }

        Section("적기") {
            QuickCapture(act, date, if (date == today) "할 일 / - 메모 / * 중요" else "${Dates.shortMd(date)}에 적기")
            T("• 할 일   × 완료   ~ 취소   – 메모   숫자 = 옮긴 횟수", Type.caption, modifier = Modifier.padding(top = 8.dp))
        }
        Gap(24.dp)
    }
}

/** 지난 미완료를 하나씩: 다시 적어 옮기기 / 이미 했음 / 지우기 */
@Composable
fun MigrateScreen(state: AppState, act: Actions, onClose: () -> Unit) {
    val today = act.today()
    val pending = Journal.pendingMigration(state, today)
    var rewrite by rememberSaveable { mutableStateOf("") }
    var handled by rememberSaveable { mutableStateOf(0) }

    Screen {
        TopBar("옮겨 적기", onBack = onClose)
        if (pending.isEmpty()) {
            Gap(24.dp)
            T("정리 끝", Type.title)
            T("오늘 해야 할 일만 남았습니다. ${handled}개를 결정했어요.", Type.body)
            Gap(20.dp)
            InkButton("닫기", onClose, Modifier.fillMaxWidth(), filled = true)
            return@Screen
        }
        val e = pending.first()
        T("남은 ${pending.size}개", Type.caption)
        Gap(16.dp)
        Boxed {
            T("${Dates.pretty(e.date)}에 적음" + if (e.migrations > 0) " · 이미 ${e.migrations}번 옮김" else "", Type.caption)
            Gap(6.dp)
            T(e.text, Type.title)
        }
        Gap(12.dp)
        T(
            if (e.migrations >= 2) "벌써 ${e.migrations}번 미뤘어요. 정말 할 일인가요, 아니면 더 작게 쪼개야 하나요?"
            else "아직 할 가치가 있나요? 옮기려면 지금 다시 적어 보세요.",
            Type.body,
        )
        Gap(8.dp)
        InkField(rewrite, { rewrite = it }, "다시 적기 (비우면 그대로)", singleLine = false)
        Gap(16.dp)
        InkButton("오늘로 옮기기", {
            act.update { Journal.migrate(it, e.id, today, rewrite) }
            rewrite = ""
            handled++
        }, Modifier.fillMaxWidth(), filled = true, height = 56.dp)
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkButton("이미 했음", {
                act.update { Journal.markDone(it, e.id, act.now()) }
                rewrite = ""
                handled++
            }, Modifier.weight(1f))
            InkButton("지우기", {
                act.update { Journal.cancel(it, e.id) }
                rewrite = ""
                handled++
            }, Modifier.weight(1f))
        }
        Gap(24.dp)
    }
}
