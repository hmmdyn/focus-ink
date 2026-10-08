package com.dain.focusink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Distractions
import com.dain.focusink.core.Source
import com.dain.focusink.core.Stats
import java.util.Locale

@Composable
fun StatsScreen(state: AppState, now: Long, act: Actions) {
    val today = Dates.today(now)
    val weekFrom = Dates.plusDays(today, -6)
    val days = Stats.lastDays(state, today, 7)
    var logging by rememberSaveable { mutableStateOf(false) }
    var planText by rememberSaveable { mutableStateOf("") }

    Screen {
        T("기록", Type.title)
        T("최근 7일", Type.caption)

        Section("딥워크 (분)") {
            val max = (days.maxOf { it.deepMinutes }).coerceAtLeast(180)
            days.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("${Dates.shortMd(d.date)} ${Dates.dowShort(d.date)}", if (d.date == today) Type.bodyBold else Type.body, modifier = Modifier.width(84.dp))
                    Box(Modifier.weight(1f).height(22.dp)) {
                        if (d.deepMinutes > 0) {
                            Box(Modifier.fillMaxWidth(d.deepMinutes / max.toFloat()).height(22.dp).background(Ink))
                        } else {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Muted).align(Alignment.CenterStart))
                        }
                    }
                    T("${d.deepMinutes}", Type.bodyBold, modifier = Modifier.width(56.dp).padding(start = 8.dp))
                }
            }
        }

        Section("이번 주 요약") {
            val total = days.sumOf { it.deepMinutes }
            val blocks = days.sumOf { it.blocks }
            val rate = Stats.completionRate(state, weekFrom, today)
            val q = Stats.averageQuality(state, weekFrom, today)
            val urges = days.sumOf { it.urgesResisted }
            val done = days.sumOf { it.tasksDone }
            T("딥워크 ${total / 60}시간 ${total % 60}분 · 완료 블록 $blocks", Type.bodyBold)
            T(
                listOfNotNull(
                    rate?.let { "블록 완주율 $it%" },
                    q?.let { "평균 집중도 " + String.format(Locale.ROOT, "%.1f", it) + "/3" },
                    "버틴 충동 $urges",
                    "끝낸 할 일 $done",
                ).joinToString(" · "),
                Type.body,
            )
        }

        Section("방해 원인 (이번 주)") {
            val top = Stats.topCategories(state, weekFrom, today)
            if (top.isEmpty()) T("기록된 방해가 없어요. 기록할수록 정확해집니다.", Type.caption)
            val maxC = top.maxOfOrNull { it.count } ?: 1
            top.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(c.category.label, Type.body, modifier = Modifier.width(110.dp))
                    Box(Modifier.weight(1f).height(18.dp).border(1.dp, Ink)) {
                        Box(Modifier.fillMaxWidth(c.count / maxC.toFloat()).height(18.dp).background(Ink))
                    }
                    T("${c.count}", Type.bodyBold, modifier = Modifier.width(44.dp).padding(start = 8.dp))
                }
            }
            val target = Distractions.weeklyTarget(state, today)
            Gap(10.dp)
            if (target != null) {
                Boxed(inverted = true) {
                    T("이번 주 제거 대상: ${target.category.label}", Type.bodyBold, color = Paper)
                    if (target.plan.isNotBlank()) T(target.plan, Type.body, color = Paper)
                }
            } else if (top.isNotEmpty()) {
                val first = top.first().category
                T("매주 하나만 없앱니다. 1위 '${first.label}'을(를) 어떻게 막을까요?", Type.body)
                InkField(planText, { planText = it }, "예: 카톡 알림 끄고 점심에만 확인")
                Gap(8.dp)
                InkButton("이번 주 제거 대상으로 정하기", {
                    act.update { Distractions.setWeeklyTarget(it, Dates.weekStart(today), first, planText) }
                    planText = ""
                }, Modifier.fillMaxWidth(), filled = true)
            }
        }

        Section("방해 로그", trailing = { InkLink(if (logging) "닫기" else "+ 기록", { logging = !logging }, style = Type.caption) }) {
            if (logging) {
                T("세션 밖에서 흔들린 것도 적어 두세요", Type.caption)
                CategoryGrid { c ->
                    act.update { Distractions.log(it, c, act.now()) }
                    logging = false
                }
                Gap(8.dp)
            }
            val log = state.distractions.sortedByDescending { it.at }
            if (log.isEmpty()) T("비어 있음", Type.caption)
            Paged(log, pageSize = 8) { d ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    T(
                        "${Dates.shortMd(Dates.dateOf(d.at))} ${Dates.hhmm(d.at)}  ${d.category.label}" +
                            (if (d.resisted) " · 버팀" else "") +
                            (if (d.source == Source.IPHONE) " · 아이폰" else "") +
                            (if (d.sessionId != null) " · 세션 중" else ""),
                        if (d.resisted) Type.body else Type.bodyBold,
                    )
                    if (d.note.isNotBlank()) T(d.note, Type.caption)
                }
                Rule()
            }
        }
        Gap(24.dp)
    }
}
