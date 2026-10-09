package com.dain.focusink.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Ids
import com.dain.focusink.core.Settings
import java.time.LocalTime

private val DOW = listOf("월", "화", "수", "목", "금", "토", "일")

private fun validTime(t: String): Boolean = runCatching { LocalTime.parse(t.trim()) }.isSuccess

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

        Section("집중") {
            SettingLabel("기본 집중 시간")
            Choice(listOf(90 to "90분", 50 to "50분", 25 to "25분"), s.deepMinutes, { v -> set { it.copy(deepMinutes = v) } })
            SettingLabel("중간에 멈출 수 없는 처음 시간")
            Choice(listOf(10 to "10분", 15 to "15분", 20 to "20분"), s.anchorMinutes, { v -> set { it.copy(anchorMinutes = v) } })
            SettingLabel("가장 짧은 쉬는 시간")
            Choice(listOf(5 to "5분", 10 to "10분", 15 to "15분"), s.breakMinutes, { v -> set { it.copy(breakMinutes = v) } })
            T("오래 집중한 뒤에는 더 쉬어요. 90분 뒤에는 15분, 50분 뒤에는 8분이에요.", Type.caption, modifier = Modifier.padding(top = 6.dp))
            SettingLabel("딴짓하고 싶을 때 기다릴 시간")
            Choice(listOf(10 to "10분", 15 to "15분"), s.urgeMinutes, { v -> set { it.copy(urgeMinutes = v) } })
        }

        Section("하루") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("하루 마무리", Type.body, modifier = Modifier.width(150.dp))
                InkField(review, { review = it }, "21:30", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("휴대폰 내려놓기", Type.body, modifier = Modifier.width(150.dp))
                InkField(sunset, { sunset = it }, "22:30", Modifier.weight(1f))
            }
            if (review != s.reviewTime || sunset != s.sunsetTime) {
                val ok = validTime(review) && validTime(sunset)
                Gap(10.dp)
                InkButton(if (ok) "시간 저장하기" else "21:30 처럼 적어 주세요", {
                    set { it.copy(reviewTime = review.trim(), sunsetTime = sunset.trim()) }
                }, Modifier.fillMaxWidth(), enabled = ok, filled = ok)
            }
            Gap(6.dp)
            InkRow({ set { it.copy(remindersEnabled = !it.remindersEnabled) } }) { dark ->
                Square(s.remindersEnabled)
                Spacer(Modifier.width(14.dp))
                T("두 시간에 알림 받기", Type.body, color = if (dark) Paper else Ink)
            }
            SettingLabel("회의와 통화를 몰아 두는 요일")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DOW.forEachIndexed { i, label ->
                    val v = i + 1
                    InkButton(label, {
                        set { it.copy(managerDays = if (v in it.managerDays) it.managerDays - v else (it.managerDays + v).sorted()) }
                    }, Modifier.weight(1f), filled = v in s.managerDays, height = 44.dp, textSize = 15.sp)
                }
            }
        }

        Section("아이폰 연결") {
            InkRow({ set { it.copy(syncEnabled = !it.syncEnabled) } }) { dark ->
                Square(s.syncEnabled)
                Spacer(Modifier.width(14.dp))
                T("아이폰 단축어와 연결하기", Type.body, color = if (dark) Paper else Ink)
            }
            SettingLabel("토픽 이름 (단축어에 그대로 입력하고, 다른 사람에게는 알려 주지 마세요)")
            T(s.syncTopic, Type.heading)
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton("복사하기", { clipboard.setText(AnnotatedString(s.syncTopic)) }, Modifier.weight(1f), height = 46.dp, textSize = 16.sp)
                ConfirmButton("새로 만들기", "단축어도 바꿔야 해요", {
                    set { it.copy(syncTopic = Ids.topic(), lastSyncId = null) }
                }, Modifier.weight(1f))
            }
            SettingLabel("서버")
            InkField(server, { server = it }, "https://ntfy.sh", onDone = {
                if (server.startsWith("http")) set { it.copy(syncServer = server.trim().trimEnd('/')) }
            })
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton("지금 가져오기", { act.syncNow() }, Modifier.weight(1f), enabled = s.syncEnabled, height = 46.dp, textSize = 16.sp)
                InkButton("시험 메시지 보내기", { act.publish("Focus Ink 연결을 확인했어요.", force = true) }, Modifier.weight(1f), height = 46.dp, textSize = 16.sp)
            }
            Gap(6.dp)
            T(syncStatus, Type.caption)
        }

        Section("백업") {
            InkButton("백업 파일 내보내기", { act.shareBackup(context) }, Modifier.fillMaxWidth())
            Gap(8.dp)
            InkField(importText, { importText = it }, "복원하려면 백업 내용을 붙여 넣으세요", singleLine = false)
            if (importText.isNotBlank()) {
                Gap(8.dp)
                ConfirmButton("복원하기 (지금 기록은 지워져요)", "한 번 더 누르면 복원해요", {
                    importMsg = if (act.importBackup(importText)) "복원했어요." else "백업 형식이 맞지 않아요."
                    importText = ""
                }, Modifier.fillMaxWidth())
            }
            if (importMsg.isNotEmpty()) T(importMsg, Type.caption)
        }

        Gap(28.dp)
        T("기록은 이 기기에만 저장돼요. 아이폰과 연결하면 짧은 메시지만 ntfy 서버를 거쳐요.", Type.caption)
        T("github.com/hmmdyn/focus-ink", Type.caption)
        Gap(24.dp)
    }
}

@Composable
private fun SettingLabel(text: String) {
    T(text, Type.body, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}
