package com.dain.focusink.core

/** 불렛저널 규칙: 한 권(한 목록)에 다 적고, 미룬 일은 직접 다시 옮기거나 지운다. */
object Journal {

    /** 접두어 해석: "- 내용" = 메모, "* 내용" 또는 "! 내용" = 중요 할 일, 나머지 = 할 일 */
    fun parse(raw: String): Triple<EntryKind, Boolean, String>? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        return when {
            t.startsWith("-") -> Triple(EntryKind.NOTE, false, t.drop(1).trim())
            t.startsWith("*") || t.startsWith("!") -> Triple(EntryKind.TASK, true, t.drop(1).trim())
            t.startsWith("•") -> Triple(EntryKind.TASK, false, t.drop(1).trim())
            else -> Triple(EntryKind.TASK, false, t)
        }.takeIf { it.third.isNotEmpty() }
    }

    fun add(state: AppState, raw: String, date: String, now: Long, source: Source = Source.APP): AppState {
        val (kind, priority, text) = parse(raw) ?: return state
        val entry = Entry(
            id = Ids.new(), text = text, date = date, kind = kind,
            priority = priority, createdAt = now, source = source,
        )
        return state.copy(entries = state.entries + entry)
    }

    fun forDate(state: AppState, date: String): List<Entry> =
        state.entries.filter { it.date == date }
            .sortedWith(compareBy<Entry>({ it.kind != EntryKind.TASK }, { !it.priority }, { it.createdAt }))

    /** 오늘 Top 3 = 오늘 날짜의 중요(*) 할 일 중 앞의 3개 */
    fun topThree(state: AppState, date: String): List<Entry> =
        state.entries.filter { it.date == date && it.kind == EntryKind.TASK && it.priority && it.status != EntryStatus.CANCELLED }
            .sortedBy { it.createdAt }
            .take(3)

    /** 지난 날짜의 미완료 할 일. 오래된 것부터. */
    fun pendingMigration(state: AppState, today: String): List<Entry> =
        state.entries.filter { it.kind == EntryKind.TASK && it.status == EntryStatus.OPEN && it.date < today }
            .sortedWith(compareBy({ it.date }, { it.createdAt }))

    private fun update(state: AppState, id: String, f: (Entry) -> Entry): AppState =
        state.copy(entries = state.entries.map { if (it.id == id) f(it) else it })

    fun toggleDone(state: AppState, id: String, now: Long): AppState = update(state, id) {
        if (it.status == EntryStatus.DONE) it.copy(status = EntryStatus.OPEN, doneAt = null)
        else it.copy(status = EntryStatus.DONE, doneAt = now)
    }

    fun markDone(state: AppState, id: String, now: Long): AppState = update(state, id) {
        it.copy(status = EntryStatus.DONE, doneAt = now)
    }

    fun cancel(state: AppState, id: String): AppState = update(state, id) { it.copy(status = EntryStatus.CANCELLED) }

    fun togglePriority(state: AppState, id: String): AppState = update(state, id) { it.copy(priority = !it.priority) }

    fun edit(state: AppState, id: String, text: String): AppState {
        val t = text.trim()
        if (t.isEmpty()) return state
        return update(state, id) { it.copy(text = t) }
    }

    fun delete(state: AppState, id: String): AppState = state.copy(entries = state.entries.filterNot { it.id == id })

    /** 옮겨 적기: 오늘로 가져오고 횟수를 센다. 다시 적은 문장으로 바꿀 수 있다. */
    fun migrate(state: AppState, id: String, today: String, rewrittenText: String? = null): AppState = update(state, id) {
        it.copy(
            date = today,
            migrations = it.migrations + 1,
            text = rewrittenText?.trim()?.takeIf { s -> s.isNotEmpty() } ?: it.text,
        )
    }

    /** 저녁 리뷰에서 내일 Top 3 를 중요 할 일로 등록. 빈 줄은 건너뛴다. */
    fun setTopThree(state: AppState, date: String, texts: List<String>, now: Long): AppState {
        var s = state
        texts.map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEachIndexed { i, t ->
            s = s.copy(
                entries = s.entries + Entry(
                    id = Ids.new(), text = t, date = date, priority = true, createdAt = now + i,
                ),
            )
        }
        return s
    }
}
