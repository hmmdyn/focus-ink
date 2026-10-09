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
import androidx.compose.ui.test.performImeAction
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
        // 1. 처음 설정
        shot("01_onboarding_intro")
        tap("다음")
        type(0, "매일 독서")
        type(1, "책 펴기")
        type(2, "아침 커피를 내린 뒤")
        shot("02_onboarding_habit")
        tap("다음")
        shot("03_onboarding_time")
        tap("다음")
        type(0, "논문 3장 초안")
        type(1, "SLAM 코드 리뷰")
        type(2, "메일 정리")
        tap("시작하기")

        // 2. 오늘 쪽
        compose.onNodeWithText("오늘 할 일").assertExists()
        compose.onNodeWithText("첫 집중을 시작할 차례예요").assertExists()
        shot("04_today")

        // 3. 어제 쪽에 적고, 오늘로 돌아와 지난 할 일 정리
        tap("‹")
        compose.onNodeWithText("이날 할 일").assertExists()
        tap("+ 할 일 추가")
        type(0, "실험 결과 정리")
        tap("추가")
        shot("05_yesterday_page")
        tap("오늘로 돌아가기")
        type(0, "- 지도 이미지를 토큰으로 나누는 아이디어")
        tap("추가")
        tap("닫기")
        compose.onNodeWithText("지난 할 일 1개가 남아 있어요").assertExists()
        shot("06_today_with_pending")
        tap("정리하기")
        shot("07_migrate")
        type(0, "실험 결과 중 그래프 1개만 정리")
        tap("오늘 할 일로 옮기기")
        shot("08_migrate_done")
        tap("닫기")

        // 4. 집중
        tap("집중")
        tap("논문 3장 초안", substring = true)
        type(1, "3장 1절 초안 한 쪽")
        tap("물 한 잔 마시고 자리로 돌아오기")
        tap("휴대폰을 다른 방에", substring = true)
        shot("09_focus_setup")
        tap("90분 집중 시작하기")
        compose.onNodeWithText("90").assertExists()
        compose.onNodeWithText("15분 뒤부터 멈출 수 있어요", substring = true).assertExists()
        shot("10_focus_running")
        tap("딴짓했어요")
        shot("11_focus_pick")
        tap("SNS·영상")
        tap("딴짓하고 싶어요")
        compose.onNodeWithText("10분만 기다려 볼까요?").assertExists()
        compose.onNodeWithText("시작할 때 정해 둔 대로 해 봐요.").assertExists()
        compose.onNodeWithText("물 한 잔 마시고 자리로 돌아오기").assertExists()
        shot("12_focus_urge")
        tap("괜찮아졌어요")
        tap("그래도 그만두기")
        tap("한 번 더 누르면 그만둬요")
        shot("13_reflect")
        compose.onNodeWithText("시작하고 바로 멈췄어요. 딴짓은 1번 했어요.").assertExists()
        tap("몰입했어요")
        type(0, "1절 한 쪽을 끝냈고, 참고문헌 정리가 남았어요")
        type(1, "참고문헌 정리부터 하기")
        tap("저장하기")
        compose.onNodeWithText("15").assertExists() // 90분 집중 뒤에는 15분 쉰다
        shot("14_break")
        tap("다음 집중 준비하기")

        // 같은 일을 다시 고르면 지난번에 남긴 "이어서 할 일"이 보인다
        tap("논문 3장 초안", substring = true)
        compose.onNodeWithText("지난번에 여기서 멈췄어요").assertExists()
        compose.onNodeWithText("참고문헌 정리부터 하기").assertExists()
        shot("14b_focus_resume")

        // 5. 기록
        tap("기록")
        assert(compose.onAllNodesWithText("SNS·영상").fetchSemanticsNodes().isNotEmpty()) // 집중 중 기록한 딴짓이 집계됨
        shot("15_record")

        // 6. 오늘 쪽에서 습관 체크
        tap("오늘")
        tap("매일 독서")
        shot("16_today_after_focus")

        // 7. 설정: 하루 마무리 시각을 00:00 으로 바꿔 바로 띄운다
        tap("설정")
        field(0).performTextReplacement("00:00")
        field(0).performImeAction() // 키보드 닫기
        compose.waitForIdle()
        tap("시간 저장하기")
        // 저장되면 저장 버튼이 사라진다
        compose.waitUntil(5_000) { compose.onAllNodesWithText("시간 저장하기").fetchSemanticsNodes().isEmpty() }
        shot("17_settings")
        tap("‹ 닫기")
        shot("17b_today_review_due")

        // 8. 하루 마무리
        compose.onNodeWithText("하루를 마무리할 시간이에요").assertExists()
        tap("마무리하기")
        shot("18_review")
        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        type(fields - 4, "논문 3장 1절 마무리")
        type(fields - 3, "실험 그래프")
        type(fields - 2, "운동 20분")
        type(fields - 1, "09:00")
        shot("18b_review_bottom")
        tap("하루 마무리하기")
        compose.onNodeWithText("내일은 09:00에 첫 집중을 시작해요", substring = true).assertExists()
        shot("19_review_done")
        tap("닫기")
        shot("20_today_end")
    }
}
