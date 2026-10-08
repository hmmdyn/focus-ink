package com.dain.focusink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

        Section("블록") {
            T("기본 길이", Type.caption)
            Choice(listOf(90 to "90분", 50 to "50분", 25 to "25분"), s.deepMinutes, { v -> set { it.copy(deepMinutes = v) } })
            Gap(8.dp)
            T("닻 (종료 버튼이 없는 처음 구간)", Type.caption)
            Choice(listOf(10 to "10분", 15 to "15분", 20 to "20분"), s.anchorMinutes, { v -> set { it.copy(anchorMinutes = v) } })
            Gap(8.dp)
            T("휴식", Type.caption)
            Choice(listOf(5 to "5분", 10 to "10분", 15 to "15분"), s.breakMinutes, { v -> set { it.copy(breakMinutes = v) } })
            Gap(8.dp)
            T("충동 버티기", Type.caption)
            Choice(listOf(10 to "10분", 15 to "15분"), s.urgeMinutes, { v -> set { it.copy(urgeMinutes = v) } })
        }

        Section("하루 리듬") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("저녁 리뷰", Type.body, modifier = Modifier.width(110.dp))
                InkField(review, { review = it }, "21:30", Modifier.weight(1f), onDone = {
                    if (validTime(review)) set { it.copy(reviewTime = review.trim()) }
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("디지털 선셋", Type.body, modifier = Modifier.width(110.dp))
                InkField(sunset, { sunset = it }, "22:30", Modifier.weight(1f), onDone = {
                    if (validTime(sunset)) set { it.copy(sunsetTime = sunset.trim()) }
                })
            }
            Gap(6.dp)
            val dirty = review != s.reviewTime || sunset != s.sunsetTime
            if (dirty) {
                val ok = validTime(review) && validTime(sunset)
                InkButton(if (ok) "시간 저장" else "HH:mm 형식으로 적어 주세요", {
                    set { it.copy(reviewTime = review.trim(), sunsetTime = sunset.trim()) }
                }, Modifier.fillMaxWidth(), enabled = ok, filled = ok)
            }
            Gap(6.dp)
            InkRow({ set { it.copy(remindersEnabled = !it.remindersEnabled) } }) { dark ->
                Square(s.remindersEnabled)
                Spacer(Modifier.width(12.dp))
                T("리뷰·선셋 알림 받기", Type.body, color = if (dark) Paper else Ink)
            }
            Gap(8.dp)
            T("매니저 데이 (통화·회의를 몰아 두는 요일)", Type.caption)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DOW.forEachIndexed { i, label ->
                    val v = i + 1
                    InkButton(label, {
                        set { it.copy(managerDays = if (v in it.managerDays) it.managerDays - v else (it.managerDays + v).sorted()) }
                    }, Modifier.weight(1f), filled = v in s.managerDays, height = 44.dp, textSize = 15.sp)
                }
            }
        }

        Section("아이폰 연동 (ntfy)") {
            InkRow({ set { it.copy(syncEnabled = !it.syncEnabled) } }) { dark ->
                Square(s.syncEnabled)
                Spacer(Modifier.width(12.dp))
                T("연동 켜기", Type.bodyBold, color = if (dark) Paper else Ink)
            }
            T("토픽 (단축어에 그대로 입력, 비밀번호처럼 보관)", Type.caption)
            T(s.syncTopic, Type.heading)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("복사", { clipboard.setText(AnnotatedString(s.syncTopic)) }, Modifier.weight(1f), height = 44.dp, textSize = 15.sp)
                ConfirmButton("새 토픽", "단축어도 바꿔야 함", {
                    set { it.copy(syncTopic = Ids.topic(), lastSyncId = null) }
                }, Modifier.weight(1f))
            }
            Gap(6.dp)
            T("서버", Type.caption)
            InkField(server, { server = it }, "https://ntfy.sh", onDone = {
                if (server.startsWith("http")) set { it.copy(syncServer = server.trim().trimEnd('/')) }
            })
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkButton("지금 가져오기", { act.syncNow() }, Modifier.weight(1f), enabled = s.syncEnabled, height = 48.dp, textSize = 16.sp)
                InkButton("테스트 보내기", { act.publish("Focus Ink 연결 테스트 ✓", force = true) }, Modifier.weight(1f), height = 48.dp, textSize = 16.sp)
            }
            Gap(4.dp)
            T(syncStatus, Type.caption)
            T("단축어 URL: ${s.syncServer}/${s.syncTopic}", Type.caption)
        }

        Section("백업") {
            InkButton("백업 내보내기 (JSON 공유)", { act.shareBackup(context) }, Modifier.fillMaxWidth())
            Gap(8.dp)
            InkField(importText, { importText = it }, "복원: 백업 JSON 붙여넣기", singleLine = false)
            if (importText.isNotBlank()) {
                Gap(6.dp)
                ConfirmButton("복원 (현재 데이터 덮어씀)", "한 번 더 누르면 복원", {
                    importMsg = if (act.importBackup(importText)) "복원 완료" else "형식이 맞지 않습니다"
                    importText = ""
                }, Modifier.fillMaxWidth())
            }
            if (importMsg.isNotEmpty()) T(importMsg, Type.caption)
        }

        Section("정보") {
            T("Focus Ink · 데이터는 이 기기에만 저장됩니다(연동 시 ntfy 를 거치는 짧은 메시지 제외).", Type.caption)
            T("github.com/hmmdyn/focus-ink", Type.caption)
        }
        Gap(24.dp)
    }
}
