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

class HangulTest {
    @Test fun picksParticleByBatchim() {
        assertEquals("휴대폰이에요", Hangul.josa("휴대폰", "이에요", "예요"))
        assertEquals("메시지예요", Hangul.josa("메시지", "이에요", "예요"))
        assertEquals("SNS·영상이에요", Hangul.josa("SNS·영상", "이에요", "예요"))
        assertEquals("집중 1시간 30분", "집중 " + Stats.duration(90))
        assertEquals("2시간", Stats.duration(120))
    }
}

/** 참고 앱·연구 반영분 (docs/RESEARCH.md) */
class EvidenceFeaturesTest {
    private fun habit(created: String = "2026-10-01"): Pair<AppState, Habit> {
        val s = Habits.add(AppState(), "독서", "책 펴기", "커피 내린 후", true, created)
        return s to s.habits.first()
    }

    @Test fun ifThenAndResumeNoteCarryOver() {
        val t0 = at(2026, 10, 8, 9)
        var s = Journal.add(AppState(), "논문 3장", "2026-10-08", 1)
        val e = s.entries.first()
        s = Focus.start(s, e.text, 50, t0, entryId = e.id, ifThen = " 메모에 적고 돌아오기 ")
        assertEquals("메모에 적고 돌아오기", s.active!!.ifThen)
        s = Focus.finish(s, t0 + 51 * MIN)
        val id = s.sessions.last().id
        assertEquals("메모에 적고 돌아오기", s.sessions.last().ifThen)
        s = Focus.reflect(s, id, 3, "1절 끝", " 2절 첫 문단부터 ")
        assertEquals("2절 첫 문단부터", Focus.resumeNote(s, e.id, e.text)?.nextStep)
        assertEquals(listOf("메모에 적고 돌아오기"), Focus.recentIfThens(s))
        // 할 일을 끝내면 이어서 할 일은 더 보여 주지 않는다
        s = Journal.markDone(s, e.id, t0 + 60 * MIN)
        assertNull(Focus.resumeNote(s, e.id, e.text))
        // 할 일 없이 이름으로 시작한 세션은 이름으로 찾는다
        s = Focus.start(s, "코드 리뷰", 25, t0 + 2 * 60 * MIN)
        s = Focus.finish(s, t0 + 2 * 60 * MIN + 26 * MIN)
        s = Focus.reflect(s, s.sessions.last().id, 2, "", "PR 2개 남음")
        assertEquals("PR 2개 남음", Focus.resumeNote(s, null, "코드 리뷰")?.nextStep)
        assertNull(Focus.resumeNote(s, null, "다른 일"))
    }

    @Test fun breakScalesWithSessionLength() {
        assertEquals(15, Focus.breakMinutes(90, 5))
        assertEquals(8, Focus.breakMinutes(50, 5))
        assertEquals(5, Focus.breakMinutes(25, 5))
        assertEquals(10, Focus.breakMinutes(25, 10))
    }

    @Test fun restDayKeepsStreakAndIsLimitedToOncePerWeek() {
        var (s, h) = habit()
        // 10-05(월) ~ 10-07(수): 월 했음, 화 쉼, 수 했음
        s = Habits.toggle(s, h.id, "2026-10-05")
        s = Habits.toggleRest(s, h.id, "2026-10-06")
        s = Habits.toggle(s, h.id, "2026-10-07")
        assertTrue(Habits.isResting(s, h.id, "2026-10-06"))
        assertEquals(2, Habits.streak(s, h.id, "2026-10-08"))
        assertEquals(HabitStatus.PENDING, Habits.status(s, h, "2026-10-08"))
        // 같은 주에는 한 번만
        assertFalse(Habits.canRest(s, h.id, "2026-10-08"))
        assertEquals(s, Habits.toggleRest(s, h.id, "2026-10-08"))
        // 다음 주에는 다시 쓸 수 있다
        assertTrue(Habits.canRest(s, h.id, "2026-10-12"))
        // 쉬는 날을 했음으로 바꾸면 쉬어 가기는 지워진다
        s = Habits.toggle(s, h.id, "2026-10-06")
        assertFalse(Habits.isResting(s, h.id, "2026-10-06"))
        assertEquals(0, Habits.restsUsed(s, h.id, "2026-10-06"))
    }

    @Test fun restDayIsNotAMiss() {
        var (s, h) = habit("2026-10-05")
        s = Habits.toggle(s, h.id, "2026-10-05")
        s = Habits.toggleRest(s, h.id, "2026-10-06")
        assertEquals(HabitStatus.PENDING, Habits.status(s, h, "2026-10-07"))
        assertEquals(0, Habits.doubleMisses(s, h, "2026-10-08"))
        assertEquals(100, Habits.rate(s, h, "2026-10-06"))
    }

    @Test fun statusDistinguishesOneMissFromLapse() {
        var (s, h) = habit("2026-10-01")
        s = Habits.toggle(s, h.id, "2026-10-05")
        assertEquals(HabitStatus.MUST_TODAY, Habits.status(s, h, "2026-10-07"))
        assertEquals(HabitStatus.LAPSED, Habits.status(s, h, "2026-10-08"))
        assertEquals(2, Habits.missedRun(s, h, "2026-10-08"))
        s = Habits.toggle(s, h.id, "2026-10-08")
        assertTrue(Habits.cameBack(s, h, "2026-10-08"))
        assertFalse(Habits.cameBack(s, h, "2026-10-06"))
    }

    @Test fun freshStartDays() {
        assertTrue(Habits.isFreshStart("2026-10-12")) // 월요일
        assertTrue(Habits.isFreshStart("2026-11-01")) // 1일
        assertFalse(Habits.isFreshStart("2026-10-09"))
    }

    @Test fun strengthRisesSlowlyAndDropsGently() {
        var (s, h) = habit("2026-01-01")
        var d = "2026-01-01"
        repeat(66) {
            s = Habits.toggle(s, h.id, d)
            d = Dates.plusDays(d, 1)
        }
        val day66 = Dates.plusDays("2026-01-01", 65)
        val after66 = Habits.strength(s, h, day66)
        assertTrue(after66 in 96..98, "66일 매일 하면 약 97%: $after66")
        assertTrue(Habits.strength(s, h, "2026-01-07") in 25..35, "일주일이면 약 3할")
        // 하루 놓치면 조금만 내려간다 (오늘 안 한 것은 아직 반영하지 않음)
        val miss = Dates.plusDays(day66, 1)
        val next = Dates.plusDays(day66, 2)
        assertEquals(after66, Habits.strength(s, h, miss))
        val dropped = Habits.strength(s, h, next)
        assertTrue(after66 - dropped in 4..6, "한 번 놓치면 약 5%p: $after66 → $dropped")
        // 쉬는 날은 그대로
        s = Habits.toggleRest(s, h.id, miss)
        assertEquals(after66, Habits.strength(s, h, next))
    }

    @Test fun firstBlockPlanAppearsInGuide() {
        val today = "2026-10-09"
        var s = AppState(settings = Settings(onboarded = true))
        s = Journal.setTopThree(s, today, listOf("논문 3장"), 1)
        s = Plans.setFirstBlock(s, today, "09:00")
        assertEquals("09:00", Plans.firstBlock(s, today))
        val early = Guide.next(s, at(2026, 10, 9, 8), SEOUL)
        assertEquals(NextKind.FIRST_BLOCK, early.kind)
        assertEquals("09:00에 첫 집중을 시작해요", early.title)
        assertEquals("논문 3장", early.detail)
        assertEquals("09:00에 시작하기로 했어요", Guide.next(s, at(2026, 10, 9, 10), SEOUL).title)
        // 잘못된 시각은 지운다
        s = Plans.setFirstBlock(s, today, "아홉시")
        assertNull(Plans.firstBlock(s, today))
    }

    @Test fun planningShowsPastAverage() {
        val t = at(2026, 10, 8, 9)
        var s = Focus.start(AppState(), "A", 90, t)
        s = Focus.finish(s, t + 91 * MIN)
        assertEquals(91 / 7, Stats.dailyAverageMinutes(s, "2026-10-09", 7, SEOUL))
        // 오늘 기록은 평균에 넣지 않는다
        assertNull(Stats.dailyAverageMinutes(s, "2026-10-08", 7, SEOUL))
    }

    @Test fun oldStateWithoutNewFieldsStillLoads() {
        val json = """{"version":1,"habits":[],"sessions":[{"id":"a","label":"x","startedAt":0,"endedAt":60000,"plannedMinutes":25,"outcome":"COMPLETED"}]}"""
        val s = Store.decode(json)
        assertEquals("", s.sessions.first().nextStep)
        assertTrue(s.habitRests.isEmpty())
        assertTrue(s.plans.isEmpty())
    }
}

class PresetTest {
    @Test fun presetsResolveMinutesAndAnchor() {
        assertEquals(25, Preset.plannedMinutes(Preset.POMODORO, 40))
        assertEquals(40, Preset.plannedMinutes(Preset.CUSTOM, 40))
        assertEquals(180, Preset.plannedMinutes(Preset.CUSTOM, 999))
        assertEquals(Preset.FLOW_CAP, Preset.plannedMinutes(Preset.FLOW, 40))
        assertEquals(Preset.DEEP90, Preset.of("unknown"))
        // 15분 스프린트에서 처음 15분을 못 멈추면 끝까지 못 멈춘다 → 절반까지만
        assertEquals(7, Preset.anchorMinutes(15, 15))
        assertEquals(15, Preset.anchorMinutes(90, 15))
    }

    @Test fun flowEndsAsCompletedAfterAnchor() {
        val t0 = at(2026, 10, 9, 9)
        var s = Focus.start(AppState(), "읽기", Preset.FLOW_CAP, t0, anchorMinutes = 15, preset = Preset.FLOW.id)
        s = Focus.finish(s, t0 + 40 * MIN, Outcome.ENDED_EARLY)
        assertEquals(Outcome.COMPLETED, s.sessions.single().outcome)
        assertEquals(Preset.FLOW.id, s.sessions.single().preset)
    }

    @Test fun pomodoroLongBreakEveryFourth() {
        var s = AppState()
        var t = at(2026, 10, 9, 9)
        repeat(4) {
            s = Focus.start(s, "과제", 25, t, anchorMinutes = 10, preset = Preset.POMODORO.id)
            s = Focus.finish(s, t + 25 * MIN, Outcome.COMPLETED)
            t += 30 * MIN
        }
        assertEquals(5, Breaks.minutes(s, s.sessions[2], 5, SEOUL))
        assertEquals(15, Breaks.minutes(s, s.sessions[3], 5, SEOUL))
    }
}

class PrintAndScoreTest {
    @Test fun passesFollowQuarterOfGoal() {
        assertEquals(0, Print.passes(0, 120))
        assertEquals(1, Print.passes(30, 120))
        assertEquals(2, Print.passes(75, 120))
        assertEquals(4, Print.passes(500, 120))
        assertEquals(15, Print.minutesToNextPass(75, 120))
        assertEquals(30, Print.minutesToNextPass(0, 120))
        assertEquals(0, Print.minutesToNextPass(120, 120))
    }

    @Test fun scoreCapsVolumeAndRewardsQuality() {
        val t0 = at(2026, 10, 9, 9)
        var s = AppState()
        s = Focus.start(s, "a", 90, t0, anchorMinutes = 15)
        s = Focus.finish(s, t0 + 90 * MIN, Outcome.COMPLETED)
        s = Focus.reflect(s, s.sessions[0].id, 3, "")
        // 90/120 → 45, 집중도 3 → 25, 딴짓 0 → 15
        assertEquals(85, Score.day(s, "2026-10-09", SEOUL))
        assertEquals(0, Score.day(s, "2026-10-10", SEOUL))
    }

    @Test fun challengeMarksPassedAndMissedDays() {
        var s = Challenge.start(AppState(), "2026-10-07")
        val t = at(2026, 10, 7, 9)
        s = Focus.start(s, "a", 90, t, anchorMinutes = 15)
        s = Focus.finish(s, t + 70 * MIN, Outcome.ENDED_EARLY)
        val days = Challenge.days(s, "2026-10-09", SEOUL)
        assertEquals(21, days.size)
        assertEquals(Challenge.Day.PASSED, days[0].second)
        assertEquals(Challenge.Day.MISSED, days[1].second)
        assertEquals(Challenge.Day.TODAY, days[2].second)
        assertEquals(Challenge.Day.FUTURE, days[3].second)
        assertEquals(1, Challenge.passedCount(days))
    }
}
