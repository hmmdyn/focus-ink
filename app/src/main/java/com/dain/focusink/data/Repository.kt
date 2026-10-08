package com.dain.focusink.data

import android.content.Context
import com.dain.focusink.core.AppState
import com.dain.focusink.core.Ids
import com.dain.focusink.core.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executors

/** 앱 상태의 단일 출처. 변경할 때마다 JSON 파일에 순서대로 저장한다. */
class Repository(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val io = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val _state = MutableStateFlow(initial(Store.load(file)))
    val state: StateFlow<AppState> = _state

    private fun initial(s: AppState): AppState =
        if (s.settings.syncTopic.isBlank()) s.copy(settings = s.settings.copy(syncTopic = Ids.topic())) else s

    fun update(f: (AppState) -> AppState) {
        synchronized(lock) {
            val old = _state.value
            val next = f(old)
            if (next == old) return
            _state.value = next
            io.execute { Store.save(file, next) }
        }
    }

    fun exportJson(): String = Store.encode(_state.value)

    /** 백업 JSON 으로 통째로 교체. 실패하면 false */
    fun importJson(text: String): Boolean = try {
        val s = Store.decode(text)
        update { s }
        true
    } catch (e: Exception) {
        false
    }
}
