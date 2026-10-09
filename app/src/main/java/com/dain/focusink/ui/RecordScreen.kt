package com.dain.focusink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Distractions
import com.dain.focusink.core.Habit
import com.dain.focusink.core.Habits
import com.dain.focusink.core.Hangul
import com.dain.focusink.core.Source
import com.dain.focusink.core.Stats
import java.util.Locale

@Composable
fun RecordScreen(state: AppState, now: Long, act: Actions) {
    val today = Dates.today(now)
    val weekFrom = Dates.plusDays(today, -6)
    val days = Stats.lastDays(state, today, 7)
    val total = days.sumOf { it.deepMinutes }

    Screen {
        T("지난 7일", Type.label)
        Gap(4.dp)
        T(if (total > 0) "${Stats.duration(total)} 집중했어요" else "아직 집중한 기록이 없어요", Type.display)
        if (total == 0) T("집중 탭에서 25분부터 시작해 볼까요?", Type.body.copy(color = Muted))
        val rate = Stats.completionRate(state, weekFrom, today)
        val q = Stats.averageQuality(state, weekFrom, today)
        val parts = listOfNotNull(
            rate?.let { "시작한 집중의 $it%를 끝까지 했어요." },
            q?.let { "집중도는 평균 " + String.format(Locale.ROOT, "%.1f", it) + "점이에요." },
            days.sumOf { it.urgesResisted }.takeIf { it > 0 }?.let { "딴짓하고 싶을 때 ${it}번 참았어요." },
        )
        if (total > 0 && parts.isNotEmpty()) T(parts.joinToString(" "), Type.body.copy(color = Muted))

        // 막대: 세로 막대 7개, 아래에 요일
        Gap(22.dp)
        val max = days.maxOf { it.deepMinutes }.coerceAtLeast(120)
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (d.deepMinutes > 0) T("${d.deepMinutes}", Type.caption)
                    val frac = d.deepMinutes / max.toFloat()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (d.deepMinutes > 0) (frac * 0.8f).coerceAtLeast(0.03f) else 0.01f)
                            .background(if (d.deepMinutes > 0) Ink else Faint),
                    )
                }
            }
        }
        Rule()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { d ->
                T(Dates.dowShort(d.date), if (d.date == today) Type.bodyBold else Type.caption, modifier = Modifier.weight(1f), align = TextAlign.Center)
            }
        }

        HabitsSection(state, today, act)
        DistractionSection(state, today, weekFrom, act)
        Gap(24.dp)
    }
}

@Composable
private fun HabitsSection(state: AppState, today: String, act: Actions) {
    val habits = Habits.active(state)
    var adding by rememberSaveable { mutableStateOf(false) }
    Section("습관", trailing = { if (!adding) InkLink("추가", { adding = true }, style = Type.caption.copy(color = Ink)) }) {
        if (habits.isEmpty() && !adding) T("한 번에 하나만, 아주 작게 시작해요.", Type.body.copy(color = Muted), modifier = Modifier.padding(vertical = 8.dp))
        habits.forEach { h -> HabitBlock(state, h, today, act) }
        if (habits.size >= 2 && !adding) {
            T("지금 습관이 익숙해진 뒤에 새 습관을 더하는 편이 좋아요.", Type.caption, modifier = Modifier.padding(top = 8.dp))
        }
        if (adding) HabitForm(today, act) { adding = false }
    }
}

@Composable
private fun HabitBlock(state: AppState, h: Habit, today: String, act: Actions) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(h.name, Type.heading, modifier = Modifier.weight(1f))
            val streak = Habits.streak(state, h.id, today)
            if (streak > 0) T("${streak}일째", Type.body.copy(color = Muted))
            InkLink("⋯", { menu = !menu }, underline = false, style = Type.heading.copy(color = Muted))
        }
        if (h.anchor.isNotBlank() || h.tiny.isNotBlank()) {
            T(listOf(h.anchor, h.tiny).filter { it.isNotBlank() }.joinToString(" "), Type.caption)
        }
        Gap(10.dp)
        // 최근 4주: 7칸씩 4줄, 오늘은 굵은 테두리
        Habits.grid(state, h, today, 28).chunked(7).forEach { week ->
            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                week.forEach { (d, v) -> Square(v, size = 26.dp, today = d == today, rest = v == false && Habits.isResting(state, h.id, d)) }
            }
        }
        Gap(10.dp)
        // 익숙한 정도: 하루 빠져도 조금만 내려가고, 매일 하면 두 달쯤 뒤 거의 다 찬다 (Loop 방식, Lally 외 2010)
        val strength = Habits.strength(state, h, today)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("익숙해진 정도", Type.body, modifier = Modifier.width(120.dp))
            Bar(strength / 100f, Modifier.weight(1f), height = 8.dp)
            T("$strength%", Type.bodyBold, modifier = Modifier.width(56.dp), align = TextAlign.End)
        }
        val misses = Habits.doubleMisses(state, h, today)
        T(
            "매일 하면 두 달쯤 걸려요. 하루 빠져도 조금만 내려가요." + if (misses > 0) " 두 번 연속 빠진 적이 ${misses}번 있어요." else "",
            Type.caption, modifier = Modifier.padding(top = 4.dp),
        )
        if (menu) {
            Gap(6.dp)
            if (Habits.canRest(state, h.id, today)) {
                InkButton("오늘은 쉬어 가기", { act.update { Habits.toggleRest(it, h.id, today) }; menu = false }, Modifier.fillMaxWidth())
                Gap(8.dp)
            } else if (Habits.isResting(state, h.id, today)) {
                InkButton("오늘도 할래요", { act.update { Habits.toggleRest(it, h.id, today) }; menu = false }, Modifier.fillMaxWidth())
                Gap(8.dp)
            }
            ConfirmButton("이 습관 그만두기", "한 번 더 누르면 보관해요", { act.update { Habits.archive(it, h.id) } }, Modifier.fillMaxWidth())
        }
    }
    Rule()
}

@Composable
private fun HabitForm(today: String, act: Actions, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var tiny by rememberSaveable { mutableStateOf("") }
    var anchor by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(top = 6.dp)) {
        InkField(name, { name = it }, "어떤 습관인가요? (예: 매일 독서)")
        InkField(tiny, { tiny = it }, "가장 작게 하면? (예: 책 펴기)")
        InkField(anchor, { anchor = it }, "언제 할까요? (예: 아침 커피를 내린 뒤)")
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkButton("추가하기", {
                if (name.isNotBlank()) {
                    act.update { Habits.add(it, name, tiny, anchor, false, today) }
                    onClose()
                }
            }, Modifier.weight(1f), filled = name.isNotBlank(), enabled = name.isNotBlank())
            InkButton("닫기", onClose, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DistractionSection(state: AppState, today: String, weekFrom: String, act: Actions) {
    var logging by rememberSaveable { mutableStateOf(false) }
    var plan by rememberSaveable { mutableStateOf("") }
    val top = Stats.topCategories(state, weekFrom, today)
    val target = Distractions.weeklyTarget(state, today)

    Section("딴짓", trailing = { InkLink(if (logging) "닫기" else "기록하기", { logging = !logging }, style = Type.caption.copy(color = Ink)) }) {
        if (logging) {
            T("집중하지 않을 때 한 딴짓도 적어 두면 원인이 잘 보여요.", Type.caption, modifier = Modifier.padding(vertical = 6.dp))
            CategoryGrid { c ->
                act.update { Distractions.log(it, c, act.now()) }
                logging = false
            }
            Gap(8.dp)
        }
        if (top.isEmpty()) {
            T("이번 주에는 적어 둔 딴짓이 없어요.", Type.body.copy(color = Muted), modifier = Modifier.padding(vertical = 8.dp))
        } else {
            val maxC = top.maxOf { it.count }
            top.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(c.category.label, Type.body, modifier = Modifier.width(104.dp))
                    Box(Modifier.weight(1f).height(10.dp)) {
                        Box(Modifier.fillMaxWidth(c.count / maxC.toFloat()).height(10.dp).background(Ink))
                    }
                    T("${c.count}번", Type.body.copy(color = Muted), modifier = Modifier.width(52.dp), align = TextAlign.End)
                }
            }
            Gap(12.dp)
            if (target != null) {
                Boxed {
                    T("이번 주에는 ${target.category.label} 줄이기", Type.heading)
                    if (target.plan.isNotBlank()) T(target.plan, Type.body)
                }
            } else {
                val first = top.first().category
                T("가장 많은 딴짓은 ${Hangul.josa(first.label, "이에요", "예요")}. 이번 주에는 이것 하나만 줄여 볼까요?", Type.body)
                InkField(plan, { plan = it }, "어떻게 줄일까요? (예: 알림 끄기)")
                Gap(10.dp)
                InkButton("이번 주 목표로 정하기", {
                    act.update { Distractions.setWeeklyTarget(it, Dates.weekStart(today), first, plan) }
                    plan = ""
                }, Modifier.fillMaxWidth(), filled = true)
            }
        }

        val log = state.distractions.sortedByDescending { it.at }
        if (log.isNotEmpty()) {
            Gap(18.dp)
            T("최근 기록", Type.label)
            Gap(4.dp)
            Paged(log, pageSize = 6) { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    T("${Dates.shortMd(Dates.dateOf(d.at))} ${Dates.hhmm(d.at)}", Type.caption, modifier = Modifier.width(96.dp))
                    Column(Modifier.weight(1f)) {
                        T(
                            d.category.label + if (d.resisted) " · 참았어요" else "",
                            if (d.resisted) Type.body.copy(color = Muted) else Type.body,
                        )
                        val meta = listOfNotNull(
                            d.note.takeIf { it.isNotBlank() },
                            if (d.source == Source.IPHONE) "아이폰에서" else null,
                            if (d.sessionId != null) "집중하던 중" else null,
                        )
                        if (meta.isNotEmpty()) T(meta.joinToString(" · "), Type.caption)
                    }
                }
                Rule()
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
