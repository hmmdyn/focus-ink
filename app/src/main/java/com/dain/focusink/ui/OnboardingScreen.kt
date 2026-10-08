package com.dain.focusink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
        T("${step + 1} / 4", Type.label)
        Gap(8.dp)
        when (step) {
            0 -> {
                T("Focus Ink", Type.title)
                Gap()
                T("이 기기는 집중 전용입니다.", Type.heading)
                Gap()
                Principle("덜어내기", "피드·색·알림 없음. 폰은 주차장에.")
                Principle("닻 15분", "블록을 시작하면 처음 15분은 무조건 자리를 지킵니다.")
                Principle("작게, 매일", "습관 하나를 민망할 만큼 작게. 두 번 연속 빠지지 않기.")
                Principle("적고 내려놓기", "할 일은 저널에. 미룬 일은 직접 다시 옮겨 적기.")
                Principle("저녁 리뷰", "결과 확인 → 미완료 정리 → 내일 Top 3.")
            }
            1 -> {
                T("습관 하나", Type.title)
                T("30일 동안 이것 하나만. 동기가 없어도 할 수 있을 만큼 작게.", Type.caption)
                Gap()
                InkField(habit, { habit = it }, "습관 (예: 매일 독서)")
                InkField(tiny, { tiny = it }, "작은 버전 (예: 책 펴기)")
                InkField(anchor, { anchor = it }, "언제 (예: 아침 커피 내린 후)")
                Gap()
                T("건너뛰고 나중에 습관 탭에서 추가해도 됩니다.", Type.caption)
            }
            2 -> {
                T("하루 리듬", Type.title)
                T("저녁 리뷰 시각과, 화면을 끄는 디지털 선셋 시각.", Type.caption)
                Gap()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("저녁 리뷰", Type.body, modifier = Modifier.width(110.dp))
                    InkField(review, { review = it }, "21:30", Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("디지털 선셋", Type.body, modifier = Modifier.width(110.dp))
                    InkField(sunset, { sunset = it }, "22:30", Modifier.weight(1f))
                }
                Gap()
                T("이 두 시각에만 알림이 옵니다. 다음 화면에서 알림 권한을 허용해 주세요.", Type.caption)
            }
            else -> {
                T("오늘 Top 3", Type.title)
                T("오늘 반드시 앞으로 나아가게 할 세 가지. 비워 두면 오늘 화면에서 정합니다.", Type.caption)
                Gap()
                InkField(t1, { t1 = it }, "1.")
                InkField(t2, { t2 = it }, "2.")
                InkField(t3, { t3 = it }, "3.")
            }
        }
        Gap(28.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun Principle(title: String, body: String) {
    Gap(8.dp)
    T(title, Type.bodyBold)
    T(body, Type.body)
}
