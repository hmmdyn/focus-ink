package com.dain.focusink.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.*
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.dain.focusink.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * e-ink 디자인 규칙 (실크스크린 · 비트맵 · 하프톤)
 * - 검정과 흰색 두 가지만 쓴다. 회색 면 대신 망점·디더링 패턴과 점선으로 구분한다.
 *   16단계 그레이 e-ink 에서도 회색은 뿌옇게 번지지만, 패턴은 경계가 그대로 남는다.
 * - 글꼴은 앱에 들어 있는 갈무리(OFL)만 쓴다. 기기에 한글 글꼴이 없어도 똑같이 보인다.
 *   픽셀 글꼴이라 원래 픽셀 크기(갈무리11 = 12px, 갈무리14 = 15px)의 정수배로만 키운다.
 * - 큰 숫자는 글꼴 대신 5×7 점 행렬로 직접 찍는다.
 * - 모서리는 둥글리지 않는다. 그림자와 애니메이션은 쓰지 않는다.
 * - 버튼은 글자가 중심이 되게 만들고, 화면마다 검은 버튼은 하나만 둔다.
 * - 누르는 동안에는 흑백을 반전해서 눌렸다는 것을 보여 준다.
 */
val Ink = Color(0xFF000000)
val Paper = Color(0xFFFFFFFF)

private val G11 = FontFamily(
    Font(R.font.galmuri11, FontWeight.Normal),
    Font(R.font.galmuri11_bold, FontWeight.Bold),
)
private val G14 = FontFamily(Font(R.font.galmuri14, FontWeight.Normal))

/**
 * 글자 크기. InkTheme 이 화면 밀도를 보고 한 번 정한다.
 * 목표 크기(dp)에 가장 가까우면서 글꼴 격자(12px, 15px)의 정수배인 픽셀 크기를 고른다.
 */
object Type {
    var hero = TextStyle.Default
        private set
    var display = TextStyle.Default
        private set
    var title = TextStyle.Default
        private set
    var lead = TextStyle.Default
        private set
    var heading = TextStyle.Default
        private set
    var body = TextStyle.Default
        private set
    var bodyBold = TextStyle.Default
        private set
    var caption = TextStyle.Default
        private set
    var label = TextStyle.Default
        private set
    var small = TextStyle.Default
        private set
    private var key = -1f

    fun init(density: Float, fontScale: Float) {
        val k = density * 1000 + fontScale
        if (k == key) return
        key = k
        val scale = density * fontScale
        fun size(grid: Int, dp: Float): TextUnit {
            val n = (dp * scale / grid).roundToInt().coerceAtLeast(1)
            return (n * grid / scale).sp
        }
        fun style(family: FontFamily, grid: Int, dp: Float, bold: Boolean = false, line: Float = 1.5f, spacing: Float = 0f): TextStyle {
            val s = size(grid, dp)
            return TextStyle(
                fontFamily = family,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                fontSize = s,
                lineHeight = (s.value * line).sp,
                letterSpacing = spacing.sp,
                color = Ink,
            )
        }
        hero = style(G11, 12, 72f, bold = true, line = 1.1f)
        display = style(G11, 12, 28f, bold = true, line = 1.35f)
        title = style(G11, 12, 22f, bold = true, line = 1.4f)
        lead = style(G11, 12, 18f)
        heading = style(G11, 12, 18f, bold = true)
        body = style(G11, 12, 18f)
        bodyBold = style(G11, 12, 18f, bold = true)
        caption = style(G14, 15, 15f)
        label = style(G11, 12, 12f, bold = true, spacing = 1f)
        small = style(G11, 12, 12f)
    }
}

@Composable
fun InkTheme(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    Type.init(d.density, d.fontScale)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Paper, background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink, secondary = Ink, onSecondary = Paper,
        ),
        content = content,
    )
}

@Composable
fun rememberPress(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return source to pressed
}

fun Modifier.inkClick(source: MutableInteractionSource, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)

/** 점선 테두리. 아직 고를 수 없거나 비어 있는 것을 회색 대신 점선으로 보여 준다 */
fun Modifier.dashedBorder(width: Dp = 1.5.dp, dash: Dp = 4.dp): Modifier = drawBehind {
    val w = width.toPx()
    val d = dash.toPx()
    drawRect(
        Ink, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(d, d))),
    )
}

@Composable
fun T(text: String, style: TextStyle = Type.body, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null, strike: Boolean = false) {
    Text(
        text = text,
        style = if (strike) style.copy(textDecoration = TextDecoration.LineThrough) else style,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

/** 기본 버튼. filled = 화면의 주된 동작(검은 버튼). 고를 수 없을 때는 점선 테두리 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    textSize: TextUnit = TextUnit.Unspecified,
) {
    val (source, pressed) = rememberPress()
    val dark = enabled && (filled xor pressed)
    Box(
        modifier
            .heightIn(min = height)
            .then(if (enabled) Modifier.border(2.dp, Ink) else Modifier.dashedBorder(2.dp))
            .background(if (dark) Ink else Paper)
            .inkClick(source, enabled, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Type.bodyBold.let { if (textSize != TextUnit.Unspecified) it.copy(fontSize = textSize) else it },
            color = if (dark) Paper else Ink,
            textAlign = TextAlign.Center,
        )
    }
}

/** 글자만 있는 버튼. 부가 동작에 쓴다 */
@Composable
fun InkLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: TextStyle = Type.body, underline: Boolean = true) {
    val (source, pressed) = rememberPress()
    Box(
        modifier
            .heightIn(min = 48.dp)
            .background(if (pressed) Ink else Color.Transparent)
            .inkClick(source, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = style, color = if (pressed) Paper else Ink, textDecoration = if (underline) TextDecoration.Underline else null)
    }
}

/** 누를 수 있는 목록 한 줄 */
@Composable
fun InkRow(onClick: (() -> Unit)?, modifier: Modifier = Modifier, minHeight: Dp = 52.dp, content: @Composable RowScope.(dark: Boolean) -> Unit) {
    val (source, pressed) = rememberPress()
    val dark = pressed && onClick != null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(if (dark) Ink else Paper)
            .then(if (onClick != null) Modifier.inkClick(source, onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(dark) }
}

/** 작은 제목 + 선. 화면 안의 구역을 나눌 때만 쓴다 */
@Composable
fun Section(label: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 26.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.label, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Rule(Modifier.padding(top = 6.dp, bottom = 4.dp), strong = true)
        content()
    }
}

/** 구분선. strong = 2dp 실선, 아니면 점선(회색 실선 대신) */
@Composable
fun Rule(modifier: Modifier = Modifier, strong: Boolean = false) {
    if (strong) {
        Box(modifier.fillMaxWidth().height(2.dp).background(Ink))
    } else {
        Canvas(modifier.fillMaxWidth().height(2.dp)) {
            val dot = 2.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawRect(Ink, Offset(x, 0f), androidx.compose.ui.geometry.Size(dot, dot))
                x += dot * 2.5f
            }
        }
    }
}

/** 테두리 상자. 지금 해야 할 일처럼 눈에 띄어야 하는 곳에만 쓴다 */
@Composable
fun Boxed(modifier: Modifier = Modifier, inverted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(2.dp, Ink)
            .background(if (inverted) Ink else Paper)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/** 체크 칸: 채움 = 함, 빈칸 = 안 함, 빗금 = 쉬어 간 날, 작은 점 = 해당 없음 */
@Composable
fun Square(state: Boolean?, size: Dp = 22.dp, today: Boolean = false, rest: Boolean = false) {
    if (rest) {
        Box(Modifier.size(size).border(1.5.dp, Ink).background(ditherBrush(4)))
        return
    }
    when (state) {
        true -> Box(Modifier.size(size).background(Ink))
        false -> Box(Modifier.size(size).border(if (today) 3.dp else 1.5.dp, Ink))
        null -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { Box(Modifier.size(3.dp).background(Ink)) }
    }
}

/** 입력 칸. 비어 있으면 아래 선이 점선, 채우면 실선 */
@Composable
fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onDone: (() -> Unit)? = null,
    style: TextStyle = Type.body,
) {
    // 완료를 누르면 키보드를 닫는다. e-ink 에서는 키보드가 화면 절반을 가리고 다시 그리는 데도 오래 걸린다.
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style,
        singleLine = singleLine,
        cursorBrush = SolidColor(Ink),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = {
            onDone?.invoke()
            if (singleLine) keyboard?.hide()
        }),
        decorationBox = { inner ->
            Column {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    // 자리 표시 글은 다른 글꼴(갈무리14)과 앞의 › 표시로 입력한 글과 구분한다
                    if (value.isEmpty()) Text("› $placeholder", style = Type.caption)
                    inner()
                }
                if (value.isEmpty()) Rule() else Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
            }
        },
    )
}

/** 선택지 중 하나. 고른 칸만 검게 */
@Composable
fun <V> Choice(options: List<Pair<V, String>>, selected: V, onSelect: (V) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().border(2.dp, Ink)) {
        options.forEachIndexed { i, (value, label) ->
            val (source, pressed) = rememberPress()
            val dark = (value == selected) xor pressed
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (dark) Ink else Paper)
                    .inkClick(source) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.bodyBold, color = if (dark) Paper else Ink, textAlign = TextAlign.Center)
            }
            if (i < options.lastIndex) Box(Modifier.width(2.dp).height(48.dp).background(Ink))
        }
    }
}

/** 진행 막대: 바탕은 성긴 망점, 찬 부분은 검정 */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(modifier.fillMaxWidth().height(height).border(1.5.dp, Ink).background(ditherBrush(2))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(Ink))
    }
}

/** 스크롤 대신 페이지 넘김 */
@Composable
fun <T> Paged(items: List<T>, pageSize: Int = 8, resetKey: Any? = null, row: @Composable (T) -> Unit) {
    var page by remember(resetKey) { mutableIntStateOf(0) }
    val pages = ((items.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val p = page.coerceIn(0, pages - 1)
    items.drop(p * pageSize).take(pageSize).forEach { row(it) }
    if (pages > 1) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            InkLink("‹ 이전", { if (p > 0) page = p - 1 }, underline = false)
            Text("${p + 1} / $pages", style = Type.caption, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            InkLink("다음 ›", { if (p < pages - 1) page = p + 1 }, underline = false)
        }
    }
}

@Composable
fun Screen(modifier: Modifier = Modifier, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxSize().background(Paper)
    Column(
        (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

/** 겹쳐 뜨는 화면의 머리글: 왼쪽 닫기, 아래에 굵은 제목 */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, closeLabel: String = "닫기") {
    if (onBack != null) {
        InkLink("‹ $closeLabel", onBack, style = Type.body, underline = false)
        Spacer(Modifier.height(4.dp))
    }
    Text(title, style = Type.display)
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

/** 집중 시작·종료 때 검은 화면을 한 번 띄웠다가 지워서 e-ink 잔상을 없앤다 */
@Composable
fun FlashOverlay(trigger: Int) {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        phase = 1
        delay(180)
        phase = 2
        delay(180)
        phase = 0
    }
    if (phase != 0) Box(Modifier.fillMaxSize().background(if (phase == 1) Ink else Paper))
}

/** 분이 바뀔 때마다 갱신되는 현재 시각 */
@Composable
fun rememberNow(tick: Int): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tick) {
        now = System.currentTimeMillis()
        while (true) {
            val t = System.currentTimeMillis()
            delay(60_000 - t % 60_000 + 100)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** 실수로 누르지 않도록 두 번 눌러야 실행되는 버튼 */
@Composable
fun ConfirmButton(text: String, confirmText: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }
    InkButton(if (armed) confirmText else text, { if (armed) { armed = false; onConfirm() } else armed = true }, modifier, filled = armed)
}
