package com.dain.focusink

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 첫 실행부터 하루 흐름 전체를 실제로 눌러 보고 화면마다 스크린샷을 남긴다.
 * CI 에서 Palma 2 해상도(824x1648) 에뮬레이터로 실행된다.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(
            if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
            else GrantPermissionRule.grant(),
        )
        .around(compose)

    private val outDir: File by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.getExternalFilesDir(null), "screens").apply { mkdirs() }
    }

    private fun shot(name: String) {
        // 테스트의 가상 시계를 진행시켜 세션 시작/종료 플래시(흑→백)가 끝난 화면을 찍는다
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        Thread.sleep(700)
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(t: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onAllNodesWithText(t, substring = substring)[0]

    private fun tap(t: String, substring: Boolean = false) {
        val n = text(t, substring)
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    private fun field(i: Int): SemanticsNodeInteraction = compose.onAllNodes(hasSetTextAction())[i]

    private fun type(i: Int, value: String) {
        val f = field(i)
        runCatching { f.performScrollTo() }
        f.performTextInput(value)
        compose.waitForIdle()
    }

    @Test
    fun fullDay() {
        // 1. 온보딩
        shot("01_onboarding_principles")
        tap("다음")
        type(0, "매일 독서")
        type(1, "책 펴기")
        type(2, "아침 커피 내린 후")
        shot("02_onboarding_habit")
        tap("다음")
        shot("03_onboarding_rhythm")
        tap("다음")
        type(0, "논문 3장 초안")
        type(1, "SLAM 코드 리뷰")
        type(2, "메일 정리")
        tap("시작하기")

        // 2. 오늘
        compose.onNodeWithText("오늘 TOP 3").assertExists()
        shot("04_today")

        // 3. 저널: 어제 일을 적고 오늘로 옮겨 적기
        tap("저널")
        tap("◀")
        type(0, "어제 못 끝낸 실험 정리")
        tap("적기")
        tap("오늘로")
        type(0, "- 아이디어: 지도 이미지 토큰화")
        tap("적기")
        shot("05_journal")
        tap("옮겨 적기")
        shot("06_migrate")
        type(0, "실험 정리 중 그래프 1개만")
        tap("오늘로 옮기기")
        shot("07_migrate_done")
        tap("닫기")

        // 4. 집중
        tap("집중")
        tap("논문 3장 초안", substring = true)
        type(1, "3장 1절 초안 1쪽")
        tap("폰은 다른 방", substring = true)
        shot("08_focus_setup")
        tap("시작 · ", substring = true)
        shot("09_focus_anchor")
        tap("흔들림 기록")
        shot("10_focus_pick")
        tap("SNS·영상")
        tap("충동이 왔다")
        shot("11_focus_urge")
        tap("지나갔다")
        tap("긴급 중단")
        tap("한 번 더 누르면 중단")
        shot("12_reflect")
        tap("몰입")
        type(0, "1쪽 끝, 참고문헌 정리 남음")
        tap("저장하고 쉬기")
        shot("13_break")
        tap("다음 블록 준비")

        // 5. 습관
        tap("습관")
        tap("오늘 했다")
        shot("14_habits")

        // 6. 기록
        tap("기록")
        shot("15_stats")

        // 7. 설정: 리뷰 시각을 00:00 으로 바꿔 리뷰를 바로 띄운다
        tap("오늘")
        tap("설정")
        field(0).performTextReplacement("00:00")
        compose.waitForIdle()
        tap("시간 저장")
        shot("16_settings")
        tap("◀ 닫기")
        shot("17_today_review_due")

        // 8. 저녁 리뷰
        tap("리뷰 시작")
        shot("18_review")
        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        type(fields - 3, "논문 3장 1절 마무리")
        type(fields - 2, "실험 그래프")
        type(fields - 1, "운동 20분")
        tap("리뷰 완료")
        shot("19_review_done")
        tap("닫기")
        shot("20_today_end")
    }
}
