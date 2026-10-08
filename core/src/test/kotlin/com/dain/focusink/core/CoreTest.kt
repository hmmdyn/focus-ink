package com.dain.focusink.core

import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi).atZone(SEOUL).toInstant().toEpochMilli()
private const val MIN = 60_000L

class JournalTest {
    @Test fun parsesPrefixes() {
        assertEquals(Triple(EntryKind.NOTE, false, "아이디어"), Journal.parse("- 아이디어"))
        assertEquals(Triple(EntryKind.TASK, true, "논문"), Journal.parse("* 논문"))
        assertEquals(Triple(EntryKind.TASK, false, "빨래"), Journal.parse("빨래"))
        assertNull(Journal.parse("   "))
        assertNull(Journal.parse("-"))
    }

    @Test fun migrationCountsAndMoves() {
        var s = Journal.add(AppState(), "보고서", "2026-10-06", 1)
        s = Journal.add(s, "* 이미 끝남", "2026-10-06", 2)
        val doneId = s.entries[1].id
        s = Journal.markDone(s, doneId, 3)
        val pending = Journal.pendingMigration(s, "2026-10-08")
        assertEquals(1, pending.size)
        s = Journal.migrate(s, pending[0].id, "2026-10-08", "보고서 1절만")
        val moved = s.entries.first { it.id == pending[0].id }
        assertEquals("2026-10-08", moved.date)
        assertEquals(1, moved.migrations)
        assertEquals("보고서 1절만", moved.text)
        assertTrue(Journal.pendingMigration(s, "2026-10-08").isEmpty())
    }

    @Test fun topThreeLimitsToThreePriorityTasks() {
        val s = Journal.setTopThree(AppState(), "2026-10-09", listOf("a", "", "b", "c", "d"), 100)
        assertEquals(listOf("a", "b", "c"), Journal.topThree(s, "2026-10-09").map { it.text })
    }
}

class HabitTest {
    private fun habitState(): Pair<AppState, Habit> {
        val s = Habits.add(AppState(), "독서", "책 펴기", "커피 내린 후", true, "2026-10-01")
        return s to s.habits.first()
    }

    @Test fun streakCountsUntilYesterdayWhenTodayPending() {
        var (s, h) = habitState()
        listOf("2026-10-05", "2026-10-06", "2026-10-07").forEach { s = Habits.toggle(s, h.id, it) }
        assertEquals(3, Habits.streak(s, h.id, "2026-10-08"))
        s = Habits.toggle(s, h.id, "2026-10-08")
        assertEquals(4, Habits.streak(s, h.id, "2026-10-08"))
    }

    @Test fun neverMissTwiceWarning() {
        var (s, h) = habitState()
        s = Habits.toggle(s, h.id, "2026-10-06")
        assertEquals(HabitStatus.MUST_TODAY, Habits.status(s, h, "2026-10-08"))
        assertEquals(HabitStatus.PENDING, Habits.status(s, h, "2026-10-07"))
        s = Habits.toggle(s, h.id, "2026-10-08")
        assertEquals(HabitStatus.DONE, Habits.status(s, h, "2026-10-08"))
    }

    @Test fun newHabitIsNotWarnedOnFirstDay() {
        val s = Habits.add(AppState(), "명상", "", "", false, "2026-10-08")
        assertEquals(HabitStatus.PENDING, Habits.status(s, s.habits.first(), "2026-10-08"))
    }

    @Test fun countsDoubleMisses() {
        var (s, h) = habitState() // 10-01 생성
        s = Habits.toggle(s, h.id, "2026-10-01")
        s = Habits.toggle(s, h.id, "2026-10-04")
        // 10-02, 10-03 연속 놓침 = 1번, 10-05~10-07 연속 놓침 = 1번
        assertEquals(2, Habits.doubleMisses(s, h, "2026-10-08"))
    }
}

class FocusTest {
    @Test fun phasesAndRemaining() {
        val t0 = at(2026, 10, 8, 9)
        val s = Focus.start(AppState(), "SLAM", 90, t0, "1절 초안")
        val a = assertNotNull(s.active)
        assertEquals(15, a.anchorMinutes)
        assertEquals(Phase.ANCHOR, Focus.phase(a, t0 + 10 * MIN))
        assertEquals(5, Focus.anchorRemainingMinutes(a, t0 + 10 * MIN))
        assertEquals(Phase.DEEP, Focus.phase(a, t0 + 15 * MIN))
        assertEquals(43, Focus.remainingMinutes(a, t0 + 47 * MIN))
        assertEquals(44, Focus.remainingMinutes(a, t0 + 47 * MIN - 1))
        assertEquals(Phase.OVERTIME, Focus.phase(a, t0 + 90 * MIN))
        assertEquals(0, Focus.remainingMinutes(a, t0 + 95 * MIN))
    }

    @Test fun staleClockNeverShowsMoreThanPlanned() {
        val t0 = at(2026, 10, 8, 9)
        val a = Focus.start(AppState(), "A", 90, t0).active!!
        assertEquals(90, Focus.remainingMinutes(a, t0 - 30_000))
        assertEquals(15, Focus.anchorRemainingMinutes(a, t0 - 30_000))
    }

    @Test fun cannotStartTwice() {
        val s = Focus.start(AppState(), "A", 90, 0)
        assertEquals(s, Focus.start(s, "B", 50, 10))
    }

    @Test fun outcomeResolvedFromElapsed() {
        val t0 = at(2026, 10, 8, 9)
        val base = Focus.start(AppState(), "A", 90, t0)
        assertEquals(Outcome.ABANDONED, Focus.finish(base, t0 + 5 * MIN).sessions.last().outcome)
        assertEquals(Outcome.ENDED_EARLY, Focus.finish(base, t0 + 60 * MIN).sessions.last().outcome)
        assertEquals(Outcome.COMPLETED, Focus.finish(base, t0 + 91 * MIN).sessions.last().outcome)
        assertNull(Focus.finish(base, t0 + 91 * MIN).active)
    }

    @Test fun urgeResolvedCountsOnlyFailures() {
        val t0 = at(2026, 10, 8, 9)
        var s = Focus.start(AppState(), "A", 90, t0)
        s = Focus.startUrge(s, t0 + 20 * MIN)
        assertEquals(10, Focus.urgeRemainingMinutes(s.active!!, t0 + 20 * MIN, 10))
        s = Focus.resolveUrge(s, t0 + 30 * MIN, passed = true, category = DistractionCategory.SNS)
        assertNull(s.active!!.urgeStartedAt)
        s = Distractions.log(s, DistractionCategory.MESSAGE, t0 + 40 * MIN)
        s = Focus.finish(s, t0 + 90 * MIN)
        assertEquals(1, s.sessions.last().distractions)
        val day = Stats.day(s, "2026-10-08", SEOUL)
        assertEquals(1, day.distractions)
        assertEquals(1, day.urgesResisted)
        assertEquals(90, day.deepMinutes)
        assertEquals(1, day.blocks)
    }
}

class CommandTest {
    @Test fun parsesKoreanAndEnglish() {
        assertEquals(Command.Distract("Instagram"), Commands.parse("방해 Instagram"))
        assertEquals(Command.Distract("YouTube"), Commands.parse("distract YouTube"))
        assertEquals(Command.Task("논문 3장 요약"), Commands.parse("할일 논문 3장 요약"))
        assertEquals(Command.Start(50, "SLAM 코드"), Commands.parse("시작 50 SLAM 코드"))
        assertEquals(Command.Start(null, "독서"), Commands.parse("START 독서"))
        assertEquals(Command.Stop, Commands.parse("종료"))
        assertNull(Commands.parse("안녕"))
        assertNull(Commands.parse("할일"))
    }

    @Test fun categorizesApps() {
        assertEquals(DistractionCategory.SNS, Commands.categoryFor("Instagram"))
        assertEquals(DistractionCategory.MESSAGE, Commands.categoryFor("카카오톡"))
        assertEquals(DistractionCategory.PHONE, Commands.categoryFor("Safari"))
    }

    @Test fun appliesIphoneDistractionInsideSession() {
        val t0 = at(2026, 10, 8, 9)
        var s = Focus.start(AppState(), "A", 90, t0)
        s = Commands.apply(s, Command.Distract("Instagram"), t0 + 30 * MIN, t0 + 31 * MIN, SEOUL)
        val d = s.distractions.single()
        assertEquals(Source.IPHONE, d.source)
        assertEquals(s.active!!.id, d.sessionId)
        assertEquals(DistractionCategory.SNS, d.category)
    }
}

class GuideTest {
    @Test fun walksThroughTheDay() {
        val morning = at(2026, 10, 8, 8)
        var s = AppState(settings = Settings(onboarded = true))
        s = Journal.add(s, "어제 일", "2026-10-07", 1)
        assertEquals(NextKind.MIGRATE, Guide.next(s, morning, SEOUL).kind)
        s = Journal.migrate(s, s.entries.first().id, "2026-10-08")
        assertEquals(NextKind.PLAN_TOP3, Guide.next(s, morning, SEOUL).kind)
        s = Journal.setTopThree(s, "2026-10-08", listOf("논문"), 2)
        assertEquals(NextKind.FIRST_BLOCK, Guide.next(s, morning, SEOUL).kind)
        s = Focus.start(s, "논문", 90, morning)
        assertEquals(NextKind.IN_FOCUS, Guide.next(s, morning + MIN, SEOUL).kind)
        s = Focus.finish(s, morning + 90 * MIN)
        assertEquals(NextKind.NEXT_BLOCK, Guide.next(s, morning + 95 * MIN, SEOUL).kind)
        assertEquals(NextKind.REVIEW, Guide.next(s, at(2026, 10, 8, 21, 40), SEOUL).kind)
        s = Reviews.complete(s, "2026-10-08", "", at(2026, 10, 8, 21, 50))
        assertEquals(NextKind.DONE_FOR_TODAY, Guide.next(s, at(2026, 10, 8, 22, 0), SEOUL).kind)
        assertEquals(NextKind.SUNSET, Guide.next(s, at(2026, 10, 8, 23, 0), SEOUL).kind)
    }
}

class StoreTest {
    @Test fun roundTripsAndSurvivesCorruption() {
        val dir = createTempDir()
        val f = File(dir, "state.json")
        var s = Journal.add(AppState(), "* 테스트", "2026-10-08", 1)
        s = Habits.add(s, "독서", "", "", false, "2026-10-08")
        Store.save(f, s)
        assertEquals(s, Store.load(f))
        f.writeText("{not json")
        assertEquals(AppState(), Store.load(f))
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("state.json.broken") })
        dir.deleteRecursively()
    }

    @Test fun topicLooksRandom() {
        val t = Ids.topic()
        assertTrue(t.startsWith("focusink-"))
        assertEquals(23, t.length)
        assertFalse(t == Ids.topic())
    }
}

class SyncTest {
    @Test fun parsesNtfyStreamAndSkipsOwnMessages() {
        val body = """
            {"id":"a1","time":1791450000,"event":"open","topic":"t"}
            {"id":"a2","time":1791450060,"event":"message","topic":"t","message":"방해 Instagram"}
            {"id":"a3","time":1791450120,"event":"message","topic":"t","message":"집중 시작 · A","tags":["focusink"]}
            {"id":"a4","time":1791450180,"event":"message","topic":"t","message":"할일 우유 사기"}
            {"id":"a5","time":1791450240,"event":"message","topic":"t","message":"그냥 잡담"}
        """.trimIndent()
        val msgs = SyncCodec.parseStream(body)
        assertEquals(listOf("a2", "a3", "a4", "a5"), msgs.map { it.id })
        val (s, applied) = SyncCodec.applyAll(AppState(), msgs, 1791450300_000L, SEOUL)
        assertEquals(2, applied)
        assertEquals(1, s.distractions.size)
        assertEquals(1791450060_000L, s.distractions.single().at)
        assertEquals("우유 사기", s.entries.single().text)
        assertEquals("a5", s.settings.lastSyncId)
    }
}
