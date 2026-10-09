package com.dain.focusink.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.*
import androidx.compose.material3.Text

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
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Distractions
import com.dain.focusink.core.Habit
import com.dain.focusink.core.Habits
import com.dain.focusink.core.Hangul
import com.dain.focusink.core.Source
import com.dain.focusink.core.Stats
import java.util.Locale

private enum class Span(val label: String) { DAY("일"), WEEK("주"), MONTH("월") }

private val DOW_SHORT_EN = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

@Composable
fun RecordScreen(state: AppState, now: Long, act: Actions) {
    val today = Dates.today(now)
    var span by rememberSaveable { mutableStateOf(Span.WEEK) }
    Screen {
        Choice(Span.entries.map { it to it.label }, span, { span = it })
        when (span) {
            Span.DAY -> DayRecord(state, today)
            Span.WEEK -> WeekRecord(state, today, act)
            Span.MONTH -> MonthRecord(state, today)
        }
        Gap(24.dp)
    }
}

/** 하루: 판화, 24시간 띠, 집중 목록 */
@Composable
private fun DayRecord(state: AppState, today: String) {
    var date by rememberSaveable { mutableStateOf(today) }
    val stat = Stats.day(state, date)
    val goal = state.settings.dailyGoalMinutes
    Gap(14.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InkButton("‹", { date = Dates.plusDays(date, -1) }, Modifier.width(56.dp), height = 44.dp)
        T(Dates.pretty(date) + if (date == today) " · 오늘" else "", Type.bodyBold, modifier = Modifier.weight(1f), align = TextAlign.Center)
        InkButton("›", { if (date < today) date = Dates.plusDays(date, 1) }, Modifier.width(56.dp), height = 44.dp, enabled = date < today)
    }
    Gap(14.dp)
    Row(verticalAlignment = Alignment.Bottom) {
        MiniPrint(Print.seed(date), Print.progress(stat.deepMinutes, goal), 120.dp, cell = 3.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            T("집중", Type.label)
            val m = stat.deepMinutes
            DotText(if (m >= 60) "${m / 60}H${(m % 60).toString().padStart(2, '0')}" else "${m}M", dot = 5.dp, gap = 1.5.dp)
            Gap(6.dp)
            T("${stat.blocks}번 끝까지 · 점수 ${Score.day(state, date)}", Type.caption)
        }
    }
    val sessions = state.sessions.filter { Dates.dateOf(it.startedAt) == date }.sortedBy { it.startedAt }
    Section("24시간", trailing = { T("0 · 6 · 12 · 18 · 24", Type.small) }) {
        Gap(4.dp)
        Canvas(Modifier.fillMaxWidth().height(24.dp).border(1.5.dp, Ink)) {
            for (q in 1..3) drawRect(Ink, androidx.compose.ui.geometry.Offset(size.width * q / 4f, 0f), androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height))
            val zone = java.time.ZoneId.systemDefault()
            sessions.forEach { x ->
                val t = Dates.timeOf(x.startedAt, zone)
                val from = (t.hour * 60 + t.minute) / 1440f
                val w = (x.actualMinutes / 1440f).coerceAtLeast(0.004f)
                drawRect(Ink, androidx.compose.ui.geometry.Offset(size.width * from, 5.dp.toPx()), androidx.compose.ui.geometry.Size(size.width * w, size.height - 10.dp.toPx()))
            }
        }
    }
    Section("집중 기록") {
        if (sessions.isEmpty()) T("이날은 집중한 기록이 없어요.", Type.caption, modifier = Modifier.padding(vertical = 8.dp))
        sessions.forEach { x ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                T("▶ " + Dates.hhmm(x.startedAt), Type.small, modifier = Modifier.width(72.dp).padding(top = 4.dp))
                Column(Modifier.weight(1f)) {
                    T(x.label, Type.body)
                    val meta = listOfNotNull(
                        Preset.of(x.preset).label.takeIf { x.preset.isNotEmpty() },
                        when (x.outcome) { Outcome.COMPLETED -> "끝까지 했어요"; Outcome.ENDED_EARLY -> "일찍 끝냈어요"; Outcome.ABANDONED -> "그만뒀어요" },
                        if (x.distractions > 0) "딴짓 ${x.distractions}번" else null,
                    )
                    T(meta.joinToString(" · "), Type.caption)
                    if (x.reflection.isNotBlank()) T(x.reflection, Type.caption)
                    if (x.quality > 0) T("●".repeat(x.quality) + "○".repeat(3 - x.quality), Type.small)
                }
                T("${x.actualMinutes}분", Type.bodyBold)
            }
            Rule()
        }
    }
}

/** 한 주: 숫자 요약, 요일별 막대(목표 점선), 딴짓 */
@Composable
private fun WeekRecord(state: AppState, today: String, act: Actions) {
    val weekFrom = Dates.plusDays(today, -6)
    val days = Stats.lastDays(state, today, 7)
    val total = days.sumOf { it.deepMinutes }
    val goal = state.settings.dailyGoalMinutes
    Gap(18.dp)
    T("지난 7일", Type.label)
    Gap(4.dp)
    T(if (total > 0) "${Stats.duration(total)} 집중했어요" else "아직 집중한 기록이 없어요", Type.display)
    if (total == 0) T("타이머 탭에서 스프린트 15분부터 시작해 볼까요?", Type.caption)
    val rate = Stats.completionRate(state, weekFrom, today)
    val q = Stats.averageQuality(state, weekFrom, today)
    Gap(12.dp)
    Row(Modifier.fillMaxWidth().border(2.dp, Ink)) {
        listOf(
            "끝까지" to (rate?.let { "$it%" } ?: "–"),
            "집중도" to (q?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "–"),
            "딴짓" to "${days.sumOf { it.distractions }}",
            "참음" to "${days.sumOf { it.urgesResisted }}",
        ).forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.width(1.5.dp).height(64.dp).background(Ink))
            Column(Modifier.weight(1f).padding(8.dp)) {
                T(k, Type.label)
                T(v, Type.title)
            }
        }
    }

    // 막대: 목표 이상은 검정, 아래는 망점. 점선 = 하루 목표
    Gap(20.dp)
    val max = maxOf(days.maxOf { it.deepMinutes }, goal * 5 / 4)
    Box(Modifier.fillMaxWidth().height(170.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { d ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (d.deepMinutes > 0) T("${d.deepMinutes}", Type.small)
                    val frac = d.deepMinutes / max.toFloat()
                    if (d.deepMinutes > 0) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((frac * 0.88f).coerceAtLeast(0.03f))
                                .border(1.5.dp, Ink)
                                .background(if (d.deepMinutes >= goal) androidx.compose.ui.graphics.SolidColor(Ink) else ditherBrush(10)),
                        )
                    }
                }
            }
        }
        // 목표선
        val goalFrac = goal / max.toFloat() * 0.88f
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * (1 - goalFrac)
            var x = 0f
            val dash = 4.dp.toPx()
            while (x < size.width) {
                drawRect(Ink, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(dash, 1.5.dp.toPx()))
                x += dash * 2
            }
        }
    }
    Rule(strong = true)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { d ->
            T(Dates.dowShort(d.date), if (d.date == today) Type.bodyBold else Type.caption, modifier = Modifier.weight(1f), align = TextAlign.Center)
        }
    }
    T("점선은 하루 목표 ${goal}분이에요. 채운 날은 검게 칠해요.", Type.caption, modifier = Modifier.padding(top = 6.dp))

    DistractionSection(state, today, weekFrom, act)
}

/** 한 달: 디더링 달력(많이 할수록 진하게)과 완성한 판화 모음 */
@Composable
private fun MonthRecord(state: AppState, today: String) {
    var month by rememberSaveable { mutableStateOf(today.take(7)) }
    val first = java.time.LocalDate.parse("$month-01")
    val dim = first.lengthOfMonth()
    val lead = first.dayOfWeek.value - 1
    val goal = state.settings.dailyGoalMinutes
    val dates = (1..dim).map { first.plusDays((it - 1).toLong()).toString() }
    val mins = dates.associateWith { Stats.day(state, it).deepMinutes }
    Gap(14.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InkButton("‹", { month = first.minusMonths(1).toString().take(7) }, Modifier.width(56.dp), height = 44.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            DotText(listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")[first.monthValue - 1] + " " + first.year, dot = 4.dp, gap = 1.dp)
        }
        InkButton("›", { if (month < today.take(7)) month = first.plusMonths(1).toString().take(7) }, Modifier.width(56.dp), height = 44.dp, enabled = month < today.take(7))
    }
    Gap(6.dp)
    T("${mins.values.count { it > 0 }}일 집중 · ${Stats.duration(mins.values.sum())}", Type.caption, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Gap(10.dp)
    Row(Modifier.fillMaxWidth()) {
        DOW_SHORT_EN.forEach { T(it.take(1), Type.label, modifier = Modifier.weight(1f), align = TextAlign.Center) }
    }
    Gap(4.dp)
    val cells: List<String?> = List(lead) { null } + dates
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 7) {
                val d = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    if (d != null) {
                        val m = mins[d] ?: 0
                        Box(
                            Modifier.fillMaxSize()
                                .border(if (d == today) 3.dp else 1.5.dp, Ink)
                                .background(ditherBrush(minutesLevel(m, goal))),
                        )
                        T(d.takeLast(2).trimStart('0'), Type.small, modifier = Modifier.padding(3.dp).background(Paper).padding(horizontal = 2.dp))
                    }
                }
            }
        }
    }
    Gap(8.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        T("0", Type.small)
        listOf(0, 3, 6, 9, 12, 16).forEach { Box(Modifier.size(16.dp).border(1.5.dp, Ink).background(ditherBrush(it))) }
        T("${goal}분+", Type.small)
    }

    val printed = dates.filter { (mins[it] ?: 0) >= goal }
    Section("이번 달 판화") {
        if (printed.isEmpty()) {
            T("하루 목표를 채운 날의 판화가 여기에 모여요.", Type.caption, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            printed.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            MiniPrint(Print.seed(d), 1f, 72.dp)
                            T(Dates.shortMd(d), Type.small)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 습관 탭: 챌린지 카드와 습관별 4주 격자 */
@Composable
fun HabitsScreen(state: AppState, now: Long, act: Actions) {
    val today = Dates.today(now)
    Screen {
        ChallengeCard(state, today, act)
        HabitsSection(state, today, act)
        Gap(24.dp)
    }
}

@Composable
private fun ChallengeCard(state: AppState, today: String, act: Actions) {
    val st = state.settings
    if (st.challengeStart == null) {
        Boxed {
            T("CHALLENGE", Type.label)
            Gap(4.dp)
            T("${st.challengeDays}일 동안 매일 ${st.challengeMinutes}분 집중해 볼까요?", Type.title)
            T("하루 ${st.challengeMinutes}분을 채우면 자동으로 인증돼요.", Type.caption)
            Gap(12.dp)
            InkButton("챌린지 시작하기", { act.update { Challenge.start(it, today) } }, Modifier.fillMaxWidth(), height = 48.dp)
        }
        return
    }
    val days = Challenge.days(state, today)
    val idx = days.indexOfFirst { it.second == Challenge.Day.TODAY || it.second == Challenge.Day.TODAY_PASSED }
    Boxed {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("CHALLENGE · " + if (idx >= 0) "${idx + 1}일째" else "끝", Type.label, modifier = Modifier.weight(1f))
            T("${Challenge.passedCount(days)}/${st.challengeDays}", Type.title)
        }
        T("${st.challengeDays}일 챌린지 · 매일 집중 ${st.challengeMinutes}분", Type.bodyBold)
        Gap(10.dp)
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEachIndexed { i, (_, d) ->
                    val n = days.indexOfFirst { it == week[i] } + 1
                    val filled = d == Challenge.Day.PASSED || d == Challenge.Day.TODAY_PASSED
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .border(if (d == Challenge.Day.TODAY || d == Challenge.Day.TODAY_PASSED) 3.dp else 1.5.dp, Ink)
                            .background(
                                when {
                                    filled -> androidx.compose.ui.graphics.SolidColor(Ink)
                                    d == Challenge.Day.MISSED -> ditherBrush(4)
                                    else -> androidx.compose.ui.graphics.SolidColor(Paper)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        T("$n", Type.small, color = if (filled) Paper else Ink, modifier = if (d == Challenge.Day.MISSED) Modifier.background(Paper) else Modifier)
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Gap(8.dp)
        T(
            if (Challenge.finished(state, today)) "챌린지가 끝났어요! ${Challenge.passedCount(days)}일을 채웠어요."
            else "망점 칸은 놓친 날이에요. 놓쳐도 이어서 하면 돼요.",
            Type.caption,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (Challenge.finished(state, today)) InkLink("다시 시작하기", { act.update { Challenge.start(it, today) } }, style = Type.caption)
            InkLink("챌린지 그만두기", { act.update { Challenge.stop(it) } }, style = Type.caption)
        }
    }
}

@Composable
fun HabitsSection(state: AppState, today: String, act: Actions) {
    val habits = Habits.active(state)
    var adding by rememberSaveable { mutableStateOf(false) }
    Section("습관 ${habits.size}개", trailing = { if (!adding) InkLink("+ 추가", { adding = true }, style = Type.caption) }) {
        if (habits.isEmpty() && !adding) T("한 번에 하나만, 아주 작게 시작해요.", Type.caption, modifier = Modifier.padding(vertical = 8.dp))
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
            T(h.name, Type.title, modifier = Modifier.weight(1f))
            val streak = Habits.streak(state, h.id, today)
            if (streak > 0) T("STREAK $streak", Type.label)
            InkLink("⋯", { menu = !menu }, underline = false, style = Type.heading)
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
            T("익숙해진 정도", Type.caption, modifier = Modifier.width(110.dp))
            Bar(strength / 100f, Modifier.weight(1f), height = 12.dp)
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

    Section("딴짓", trailing = { InkLink(if (logging) "닫기" else "기록하기", { logging = !logging }, style = Type.caption) }) {
        if (logging) {
            T("집중하지 않을 때 한 딴짓도 적어 두면 원인이 잘 보여요.", Type.caption, modifier = Modifier.padding(vertical = 6.dp))
            CategoryGrid { c ->
                act.update { Distractions.log(it, c, act.now()) }
                logging = false
            }
            Gap(8.dp)
        }
        if (top.isEmpty()) {
            T("이번 주에는 적어 둔 딴짓이 없어요.", Type.caption, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            val maxC = top.maxOf { it.count }
            top.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(c.category.label, Type.body, modifier = Modifier.width(104.dp))
                    Box(Modifier.weight(1f).height(14.dp).border(1.5.dp, Ink)) {
                        Box(Modifier.fillMaxWidth(c.count / maxC.toFloat()).height(14.dp).background(ditherBrush(if (c == top.first()) 16 else 8)))
                    }
                    T("${c.count}번", Type.caption, modifier = Modifier.width(52.dp), align = TextAlign.End)
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
                            if (d.resisted) Type.caption else Type.body,
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
