package com.dain.focusink.core

import kotlinx.serialization.json.Json
import java.io.File

object Store {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)

    fun decode(text: String): AppState = json.decodeFromString(AppState.serializer(), text)

    /** 파일이 없거나 깨졌으면 빈 상태. 깨진 파일은 .broken 으로 보관한다. */
    fun load(file: File): AppState {
        if (!file.exists()) return AppState()
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            file.copyTo(File(file.parentFile, file.name + ".broken-" + System.currentTimeMillis()), overwrite = true)
            AppState()
        }
    }

    /** 임시 파일에 쓰고 이름을 바꿔 원자적으로 저장 */
    fun save(file: File, state: AppState) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(encode(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
