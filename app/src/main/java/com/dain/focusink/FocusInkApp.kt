package com.dain.focusink

import android.app.Application
import com.dain.focusink.core.ActiveSession
import com.dain.focusink.core.Dates
import com.dain.focusink.core.Focus
import com.dain.focusink.data.Repository
import com.dain.focusink.sync.SyncManager
import com.dain.focusink.system.Alarms
import com.dain.focusink.system.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class FocusInkApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var sync: SyncManager
        private set

    /** 화면이 다시 보일 때마다 증가 → 시계를 즉시 갱신 */
    val resumeTick = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        sync = SyncManager(repo)
        Notifications.createChannels(this)
        Alarms.rescheduleAll(this, repo.state.value)
        watchSideEffects()
    }

    /** 세션 시작/종료, 설정 변경에 따른 알람·아이폰 알림 */
    private fun watchSideEffects() {
        scope.launch {
            var prev: ActiveSession? = repo.state.value.active
            var prevSettings = repo.state.value.settings
            repo.state.collect { s ->
                val cur = s.active
                if (prev?.id != cur?.id) {
                    prev?.let { ended ->
                        Alarms.cancelSessionEnd(this@FocusInkApp)
                        s.sessions.lastOrNull { it.id == ended.id }?.let { done ->
                            sync.publishAsync("‘${done.label}’ 집중을 마쳤어요. ${done.actualMinutes}분 동안 했고 딴짓은 ${done.distractions}번이에요.")
                        }
                    }
                    cur?.let { started ->
                        val end = Focus.endsAt(started)
                        Alarms.scheduleSessionEnd(this@FocusInkApp, end)
                        sync.publishAsync("‘${started.label}’ 집중을 시작했어요. ${Dates.hhmm(end)}까지 해요.")
                    }
                    prev = cur
                }
                val st = s.settings
                if (st.reviewTime != prevSettings.reviewTime || st.sunsetTime != prevSettings.sunsetTime ||
                    st.remindersEnabled != prevSettings.remindersEnabled || st.onboarded != prevSettings.onboarded
                ) {
                    Alarms.scheduleDaily(this@FocusInkApp, st)
                }
                prevSettings = st
            }
        }
    }
}
