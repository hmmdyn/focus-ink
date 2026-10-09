package com.dain.focusink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Habits
import com.dain.focusink.core.Journal
import java.time.LocalTime

@Composable
fun OnboardingScreen(state: AppState, act: Actions, onRequestNotifications: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var habit by rememberSaveable { mutableStateOf("") }
    var tiny by rememberSaveable { mutableStateOf("") }
    var anchor by rememberSaveable { mutableStateOf("") }
    var review by rememberSaveable { mutableStateOf(state.settings.reviewTime) }
    var sunset by rememberSaveable { mutableStateOf(state.settings.sunsetTime) }
    var t1 by rememberSaveable { mutableStateOf("") }
    var t2 by rememberSaveable { mutableStateOf("") }
    var t3 by rememberSaveable { mutableStateOf("") }

    Screen {
        // 진행 표시: 네 칸
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            repeat(4) { i -> Box(Modifier.width(28.dp).height(3.dp).background(if (i <= step) Ink else Faint)) }
        }
        Gap(28.dp)
        when (step) {
            0 -> {
                T("집중할 때만 꺼내는\n수첩이에요", Type.display)
                Gap(24.dp)
                listOf(
                    "할 일을 적고, 가장 중요한 일을 세 가지까지 골라요.",
                    "집중을 시작하면 처음 15분은 멈추지 않아요.",
                    "습관은 하나만, 아주 작게 시작해요.",
                    "저녁에는 하루를 돌아보고 내일 할 일을 정해요.",
                ).forEach {
                    T(it, Type.lead, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
            1 -> {
                T("습관 하나를\n정해 볼까요?", Type.display)
                Gap(18.dp)
                InkField(habit, { habit = it }, "어떤 습관인가요? (예: 매일 독서)")
                InkField(tiny, { tiny = it }, "가장 작게 하면? (예: 책 펴기)")
                InkField(anchor, { anchor = it }, "언제 할까요? (예: 아침 커피를 내린 뒤)")
                Gap(12.dp)
                T("나중에 기록 탭에서 정해도 괜찮아요.", Type.caption)
            }
            2 -> {
                T("알림 받을 시간을\n정해 볼까요?", Type.display)
                Gap(18.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("하루 마무리", Type.body, modifier = Modifier.width(150.dp))
                    InkField(review, { review = it }, "21:30", Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("휴대폰 내려놓기", Type.body, modifier = Modifier.width(150.dp))
                    InkField(sunset, { sunset = it }, "22:30", Modifier.weight(1f))
                }
                Gap(12.dp)
                T("알림은 이 두 번만 보내요.", Type.caption)
            }
            else -> {
                T("오늘 가장 중요한 일은\n무엇인가요?", Type.display)
                Gap(18.dp)
                InkField(t1, { t1 = it }, "첫 번째")
                InkField(t2, { t2 = it }, "두 번째")
                InkField(t3, { t3 = it }, "세 번째")
                Gap(12.dp)
                T("비워 두고 나중에 정해도 돼요.", Type.caption)
            }
        }
        Gap(36.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) InkButton("이전", { step-- }, Modifier.weight(1f))
            InkButton(if (step < 3) "다음" else "시작하기", {
                if (step == 2) onRequestNotifications()
                if (step < 3) step++ else {
                    val n = act.now()
                    val today = act.today()
                    val okReview = runCatching { LocalTime.parse(review.trim()) }.isSuccess
                    val okSunset = runCatching { LocalTime.parse(sunset.trim()) }.isSuccess
                    act.update {
                        var s = it
                        if (habit.isNotBlank()) s = Habits.add(s, habit, tiny, anchor, true, today)
                        s = Journal.setTopThree(s, today, listOf(t1, t2, t3), n)
                        s.copy(
                            settings = s.settings.copy(
                                reviewTime = if (okReview) review.trim() else s.settings.reviewTime,
                                sunsetTime = if (okSunset) sunset.trim() else s.settings.sunsetTime,
                                onboarded = true,
                            ),
                        )
                    }
                    act.flash()
                }
            }, Modifier.weight(2f), filled = true)
        }
    }
}
