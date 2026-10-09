package com.dain.focusink.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * e-ink 디자인 규칙 (Kindle, reMarkable, Light Phone 의 공통점을 따름)
 * - 흑백과 회색 한 단계만 쓴다. 그림자, 색, 애니메이션은 쓰지 않는다.
 * - 구분은 1dp 실선으로 하고, 상자는 꼭 필요한 곳에만 둔다.
 * - 제목과 숫자는 명조, 목록과 버튼은 고딕으로 쓴다.
 * - 버튼은 글자가 중심이 되게 만든다. 화면마다 검은 버튼은 하나만 둔다.
 * - 누르는 동안에는 흑백을 반전해서 눌렸다는 것을 보여 준다.
 */
val Ink = Color(0xFF111111)
val Paper = Color(0xFFFFFFFF)
val Muted = Color(0xFF5A5A5A)
val Faint = Color(0xFFBDBDBD)

private val Shape = RoundedCornerShape(6.dp)

@Composable
fun InkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Paper, background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink, secondary = Ink, onSecondary = Paper,
        ),
        content = content,
    )
}

object Type {
    private val serif = FontFamily.Serif
    val hero = TextStyle(fontFamily = serif, fontSize = 104.sp, fontWeight = FontWeight.Bold, lineHeight = 108.sp, color = Ink)
    val display = TextStyle(fontFamily = serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, color = Ink)
    val title = TextStyle(fontFamily = serif, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, color = Ink)
    val lead = TextStyle(fontFamily = serif, fontSize = 20.sp, lineHeight = 30.sp, color = Ink)
    val heading = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, color = Ink)
    val body = TextStyle(fontSize = 18.sp, lineHeight = 27.sp, color = Ink)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold)
    val caption = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Muted)
    val label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = Muted)
}

@Composable
fun rememberPress(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return source to pressed
}

fun Modifier.inkClick(source: MutableInteractionSource, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)

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

/** 기본 버튼. filled = 화면의 주된 동작(검은 버튼) */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    textSize: TextUnit = 17.sp,
) {
    val (source, pressed) = rememberPress()
    val dark = enabled && (filled xor pressed)
    Box(
        modifier
            .heightIn(min = height)
            .clip(Shape)
            .border(1.5.dp, if (enabled) Ink else Faint, Shape)
            .background(if (dark) Ink else Paper)
            .inkClick(source, enabled, onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Type.bodyBold.copy(fontSize = textSize),
            color = if (!enabled) Faint else if (dark) Paper else Ink,
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

/** 작은 회색 제목 + 실선. 화면 안의 구역을 나눌 때만 쓴다 */
@Composable
fun Section(label: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 28.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.label, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Rule(Modifier.padding(top = 6.dp, bottom = 4.dp))
        content()
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier, strong: Boolean = false) {
    Box(modifier.fillMaxWidth().height(if (strong) 2.dp else 1.dp).background(if (strong) Ink else Faint))
}

/** 테두리 상자. 지금 해야 할 일처럼 눈에 띄어야 하는 곳에만 쓴다 */
@Composable
fun Boxed(modifier: Modifier = Modifier, inverted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shape)
            .border(1.5.dp, Ink, Shape)
            .background(if (inverted) Ink else Paper)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

/** 체크 칸: 채움 = 함, 빈칸 = 안 함, 가로줄 = 쉬어 간 날, 작은 점 = 해당 없음 */
@Composable
fun Square(state: Boolean?, size: Dp = 22.dp, today: Boolean = false, rest: Boolean = false) {
    val shape = RoundedCornerShape(3.dp)
    if (rest) {
        Box(Modifier.size(size).clip(shape).border(1.5.dp, Faint, shape), contentAlignment = Alignment.Center) {
            Box(Modifier.width(size * 0.5f).height(2.dp).background(Ink))
        }
        return
    }
    when (state) {
        true -> Box(Modifier.size(size).clip(shape).background(Ink))
        false -> Box(Modifier.size(size).clip(shape).border(if (today) 2.5.dp else 1.5.dp, Ink, shape))
        null -> Box(Modifier.size(size), contentAlignment = Alignment.Center) { Box(Modifier.size(4.dp).background(Faint)) }
    }
}

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
                    if (value.isEmpty()) Text(placeholder, style = style, color = Faint)
                    inner()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink))
            }
        },
    )
}

/** 선택지 중 하나. 고른 칸만 검게 */
@Composable
fun <V> Choice(options: List<Pair<V, String>>, selected: V, onSelect: (V) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(Shape).border(1.5.dp, Ink, Shape)) {
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
                Text(label, style = Type.bodyBold.copy(fontSize = 16.sp), color = if (dark) Paper else Ink, textAlign = TextAlign.Center)
            }
            if (i < options.lastIndex) Box(Modifier.width(1.5.dp).height(48.dp).background(Ink))
        }
    }
}

@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).background(Faint)) {
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
        (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

/** 겹쳐 뜨는 화면의 머리글: 왼쪽 닫기, 가운데 제목 없이 아래에 명조 제목 */
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
