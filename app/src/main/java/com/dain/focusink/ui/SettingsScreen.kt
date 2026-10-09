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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Ids
import com.dain.focusink.core.Settings
import java.time.LocalTime

private val DOW = listOf("월", "화", "수", "목", "금", "토", "일")

private fun validTime(t: String): Boolean = runCatching { LocalTime.parse(t.trim()) }.isSuccess

/**
 * 설정(조정) 화면. 자주 바꾸는 것부터: 하루 목표 → 타이머 → 챌린지 → 하루 → 아이폰 → 백업 → 정보.
 * 고르는 값은 모두 Choice 한 줄로, 설명은 꼭 필요한 곳에만 한 줄.
 */
@Composable
fun SettingsScreen(state: AppState, act: Actions, onClose: () -> Unit) {
    val s = state.settings
    fun set(f: (Settings) -> Settings) = act.update { it.copy(settings = f(it.settings)) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val syncStatus by act.syncStatus.collectAsState()
    var review by rememberSaveable { mutableStateOf(s.reviewTime) }
    var sunset by rememberSaveable { mutableStateOf(s.sunsetTime) }
    var server by rememberSaveable { mutableStateOf(s.syncServer) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importMsg by rememberSaveable { mutableStateOf("") }

    Screen {
        TopBar("설정", onBack = onClose)

        // 하루 목표와 판 나눔: 목표를 바꾸면 한 판에 드는 시간이 바로 보인다
        Section("하루 목표") {
            Gap(4.dp)
            Choice(listOf(60, 90, 120, 180, 240).map { it to "$it" }, s.dailyGoalMinutes, { v -> set { it.copy(dailyGoalMinutes = v) } })
            Gap(10.dp)
            PassSplit(s.dailyGoalMinutes)
            T("목표의 4분의 1을 채울 때마다 오늘의 판화가 한 판씩 찍혀요.", Type.caption, modifier = Modifier.padding(top = 6.dp))
        }

        Section("타이머") {
            SettingRow("처음 멈출 수 없는 시간") {
                Choice(listOf(10 to "10분", 15 to "15분", 20 to "20분"), s.anchorMinutes, { v -> set { it.copy(anchorMinutes = v) } })
            }
            SettingRow("가장 짧은 쉬는 시간") {
                Choice(listOf(5 to "5분", 10 to "10분", 15 to "15분"), s.breakMinutes, { v -> set { it.copy(breakMinutes = v) } })
            }
            T("포모도로는 5분, 네 번째마다 15분 쉬어요. 다른 모드는 집중 시간의 6분의 1만큼 쉬어요.", Type.caption, modifier = Modifier.padding(top = 6.dp))
            SettingRow("딴짓하고 싶을 때 기다릴 시간") {
                Choice(listOf(10 to "10분", 15 to "15분"), s.urgeMinutes, { v -> set { it.copy(urgeMinutes = v) } })
            }
        }

        Section("챌린지") {
            SettingRow("기간") {
                Choice(listOf(7 to "7일", 14 to "14일", 21 to "21일", 30 to "30일"), s.challengeDays, { v -> set { it.copy(challengeDays = v) } })
            }
            SettingRow("하루에 채울 시간") {
                Choice(listOf(30 to "30분", 60 to "60분", 90 to "90분", 120 to "120분"), s.challengeMinutes, { v -> set { it.copy(challengeMinutes = v) } })
            }
            T(
                s.challengeStart?.let { "${Dates.pretty(it)}에 시작한 챌린지에 바로 반영돼요." }
                    ?: "습관 탭에서 챌린지를 시작할 수 있어요.",
                Type.caption, modifier = Modifier.padding(top = 6.dp),
            )
        }

        Section("하루") {
            TimeRow("하루 마무리", review, "21:30") { review = it }
            TimeRow("휴대폰 내려놓기", sunset, "22:30") { sunset = it }
            if (review != s.reviewTime || sunset != s.sunsetTime) {
                val ok = validTime(review) && validTime(sunset)
                Gap(10.dp)
                InkButton(if (ok) "시간 저장하기" else "21:30처럼 적어 주세요", {
                    set { it.copy(reviewTime = review.trim(), sunsetTime = sunset.trim()) }
                }, Modifier.fillMaxWidth(), enabled = ok, filled = ok)
            }
            Gap(6.dp)
            InkRow({ set { it.copy(remindersEnabled = !it.remindersEnabled) } }) { dark ->
                Square(s.remindersEnabled)
                Spacer(Modifier.width(14.dp))
                T("두 시각에 알림 받기", Type.body, color = if (dark) Paper else Ink)
            }
            SettingRow("회의와 통화를 몰아 두는 요일") {
                Row(Modifier.fillMaxWidth().border(2.dp, Ink)) {
                    DOW.forEachIndexed { i, label ->
                        val v = i + 1
                        val on = v in s.managerDays
                        val (source, pressed) = rememberPress()
                        val dark = on xor pressed
                        if (i > 0) Box(Modifier.width(2.dp).height(48.dp).background(Ink))
                        Box(
                            Modifier.weight(1f).heightIn(min = 48.dp).background(if (dark) Ink else Paper)
                                .inkClick(source) { set { it.copy(managerDays = if (v in it.managerDays) it.managerDays - v else (it.managerDays + v).sorted()) } },
                            contentAlignment = Alignment.Center,
                        ) { T(label, Type.bodyBold, color = if (dark) Paper else Ink) }
                    }
                }
            }
        }

        Section("아이폰 연결") {
            InkRow({ set { it.copy(syncEnabled = !it.syncEnabled) } }) { dark ->
                Square(s.syncEnabled)
                Spacer(Modifier.width(14.dp))
                T("아이폰 단축어와 연결하기", Type.body, color = if (dark) Paper else Ink)
            }
            T("토픽 이름 · 단축어에 그대로 넣고, 다른 사람에게는 알려 주지 않아요.", Type.caption, modifier = Modifier.padding(top = 10.dp))
            Gap(6.dp)
            Box(Modifier.fillMaxWidth().border(2.dp, Ink).background(ditherBrush(2)).padding(10.dp)) {
                T(s.syncTopic, Type.bodyBold, modifier = Modifier.background(Paper).padding(horizontal = 4.dp))
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("복사하기", { clipboard.setText(AnnotatedString(s.syncTopic)) }, Modifier.weight(1f), height = 48.dp)
                ConfirmButton("새로 만들기", "단축어도 바꿔야 해요", {
                    set { it.copy(syncTopic = Ids.topic(), lastSyncId = null) }
                }, Modifier.weight(1f))
            }
            Gap(6.dp)
            InkField(server, { server = it }, "서버 · https://ntfy.sh", onDone = {
                if (server.startsWith("http")) set { it.copy(syncServer = server.trim().trimEnd('/')) }
            })
            Gap(10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("지금 가져오기", { act.syncNow() }, Modifier.weight(1f), enabled = s.syncEnabled, height = 48.dp)
                InkButton("시험 메시지 보내기", { act.publish("Focus Ink 연결을 확인했어요.", force = true) }, Modifier.weight(1f), height = 48.dp)
            }
            Gap(6.dp)
            T(syncStatus, Type.caption)
        }

        Section("백업") {
            Gap(4.dp)
            InkButton("백업 파일 내보내기", { act.shareBackup(context) }, Modifier.fillMaxWidth())
            Gap(4.dp)
            InkField(importText, { importText = it }, "백업 내용을 붙여 넣으면 복원할 수 있어요", singleLine = false)
            if (importText.isNotBlank()) {
                Gap(8.dp)
                ConfirmButton("복원하기 (지금 기록은 지워져요)", "한 번 더 누르면 복원해요", {
                    importMsg = if (act.importBackup(importText)) "복원했어요." else "백업 내용을 읽지 못했어요. 내보낸 글 전체를 붙여 넣어 주세요."
                    importText = ""
                }, Modifier.fillMaxWidth())
            }
            if (importMsg.isNotEmpty()) T(importMsg, Type.caption)
        }

        Section("정보") {
            Gap(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("coach", pixel = 4.dp)
                Spacer(Modifier.width(12.dp))
                DotText("FOCUS INK", dot = 3.dp, gap = 1.dp, ghost = false, square = true)
            }
            Gap(10.dp)
            T("기록은 이 기기에만 저장돼요. 아이폰과 연결하면 짧은 메시지만 ntfy 서버를 거쳐요.", Type.caption)
            T("글꼴은 갈무리(Galmuri, SIL Open Font License 1.1)예요. 앱에 들어 있어서 따로 설치하지 않아도 돼요.", Type.caption, modifier = Modifier.padding(top = 6.dp))
            T("github.com/hmmdyn/focus-ink", Type.caption, modifier = Modifier.padding(top = 6.dp))
        }
        Gap(24.dp)
    }
}

/** 목표를 네 판으로 나눈 모습. 판마다 망점 농도가 한 단계씩 진해진다 */
@Composable
private fun PassSplit(goal: Int) {
    val per = goal / Print.PASSES
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Print.PASS_NAMES.forEachIndexed { i, name ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(28.dp).border(1.5.dp, Ink).background(ditherBrush((i + 1) * 4)))
                Gap(4.dp)
                T(name, Type.small)
                T("${per}분", Type.bodyBold)
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    T(label, Type.body, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    content()
}

@Composable
private fun TimeRow(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(label, Type.body, modifier = Modifier.width(150.dp))
        InkField(value, onChange, placeholder, Modifier.weight(1f))
    }
}
