package com.dain.focusink.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.*

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dain.focusink.FocusInkApp
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Focus
import com.dain.focusink.core.Outcome

enum class Tab(val label: String, val icon: String) { TODAY("오늘", "today"), FOCUS("타이머", "timer"), RECORD("기록", "record"), HABITS("습관", "habits") }

enum class Overlay { NONE, SETTINGS, REVIEW, MIGRATE, TOP3 }

/** 화면들이 공통으로 쓰는 동작 */
class Actions(private val app: FocusInkApp, private val flash: () -> Unit) {
    val state: AppState get() = app.repo.state.value
    fun now(): Long = System.currentTimeMillis()
    fun today(): String = Dates.today(now())
    fun update(f: (AppState) -> AppState) = app.repo.update(f)
    fun flash() = flash.invoke()

    fun startFocus(label: String, minutes: Int, intention: String, entryId: String?, ifThen: String = "", preset: Preset = Preset.of(state.settings.lastPreset)) {
        val anchor = Preset.anchorMinutes(minutes, state.settings.anchorMinutes)
        update {
            val s = it.copy(settings = it.settings.copy(lastPreset = preset.id))
            Focus.start(s, label, minutes, now(), intention, entryId, anchorMinutes = anchor, ifThen = ifThen, preset = preset.id)
        }
        flash()
    }

    /** 끝낸 세션 ID 반환 */
    fun finishFocus(outcome: Outcome? = null): String? {
        val id = state.active?.id ?: return null
        update { Focus.finish(it, now(), outcome) }
        flash()
        return id
    }

    fun syncNow() = app.sync.pullAsync()
    fun publish(text: String, force: Boolean = false) = app.sync.publishAsync(text, force)
    val syncStatus get() = app.sync.status

    fun shareBackup(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "focus-ink-backup-${today()}.json")
            putExtra(Intent.EXTRA_TEXT, app.repo.exportJson())
        }
        context.startActivity(Intent.createChooser(send, "백업 내보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun importBackup(text: String): Boolean = app.repo.importJson(text)
}

@Composable
fun Root(app: FocusInkApp, onRequestNotifications: () -> Unit) {
    val state by app.repo.state.collectAsState()
    val tick by app.resumeTick.collectAsState()
    val minuteTick = rememberNow(tick)
    // 상태가 바뀔 때마다(세션 시작·종료 등) 현재 시각을 새로 읽는다. 분 단위 갱신만으로는 시작 직후가 1분 어긋난다.
    val now = remember(state, minuteTick) { System.currentTimeMillis() }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var reflectId by rememberSaveable { mutableStateOf<String?>(null) }
    var flash by remember { mutableIntStateOf(0) }
    val act = remember { Actions(app) { flash++ } }

    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    BackHandler(enabled = overlay == Overlay.NONE && state.active == null && reflectId == null && tab != Tab.TODAY) { tab = Tab.TODAY }

    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        when {
            !state.settings.onboarded -> OnboardingScreen(state, act, onRequestNotifications)
            overlay == Overlay.SETTINGS -> SettingsScreen(state, act) { overlay = Overlay.NONE }
            overlay == Overlay.REVIEW -> ReviewScreen(state, now, act) { overlay = Overlay.NONE }
            overlay == Overlay.MIGRATE -> MigrateScreen(state, act) { overlay = Overlay.NONE }
            overlay == Overlay.TOP3 -> Top3Screen(state, act) { overlay = Overlay.NONE }
            state.active != null -> FocusRunScreen(state, now, act) { id ->
                reflectId = id
                tab = Tab.FOCUS
            }
            reflectId != null -> ReflectScreen(state, now, reflectId!!, act) { reflectId = null }
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.TODAY -> TodayScreen(state, now, act, open = { overlay = it }, goTab = { tab = it })
                        Tab.FOCUS -> FocusSetupScreen(state, now, act)
                        Tab.RECORD -> RecordScreen(state, now, act)
                        Tab.HABITS -> HabitsScreen(state, now, act)
                    }
                }
                TabBar(tab) { tab = it }
            }
        }
        FlashOverlay(flash)
    }
}

/** 아래 탭: 픽셀 아이콘 + 글자. 고른 탭은 흑백을 뒤집는다 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Paper)) {
        Rule(strong = true)
        Row(Modifier.fillMaxWidth()) {
            Tab.entries.forEach { t ->
                val (source, pressed) = rememberPress()
                val dark = (t == selected) xor pressed
                val c = if (dark) Paper else Ink
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .background(if (dark) Ink else Paper)
                        .inkClick(source) { onSelect(t) }
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PixelIcon(t.icon, pixel = 3.dp, color = c)
                    Spacer(Modifier.height(5.dp))
                    Text(t.label, style = Type.small, color = c, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
