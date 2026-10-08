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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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

// e-ink 규칙: 흑백만, 애니메이션 없음, 누르는 동안 반전.
val Ink = Color(0xFF000000)
val Paper = Color(0xFFFFFFFF)
val Muted = Color(0xFF444444)

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
    val hero = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Black, lineHeight = 100.sp, color = Ink)
    val title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, color = Ink)
    val heading = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp, color = Ink)
    val body = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, color = Ink)
    val bodyBold = body.copy(fontWeight = FontWeight.Bold)
    val caption = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Muted)
    val label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Ink)
}

/** 리플 없이 누르는 동안의 상태만 돌려주는 클릭 */
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

@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    textSize: TextUnit = 18.sp,
) {
    val (source, pressed) = rememberPress()
    val dark = enabled && (filled xor pressed)
    Box(
        modifier
            .heightIn(min = height)
            .border(2.dp, if (enabled) Ink else Muted)
            .background(if (dark) Ink else Paper)
            .inkClick(source, enabled, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Type.bodyBold.copy(fontSize = textSize),
            color = if (!enabled) Muted else if (dark) Paper else Ink,
            textAlign = TextAlign.Center,
        )
    }
}

/** 테두리 없는 텍스트 링크형 버튼 */
@Composable
fun InkLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: TextStyle = Type.bodyBold) {
    val (source, pressed) = rememberPress()
    Box(
        modifier
            .heightIn(min = 44.dp)
            .background(if (pressed) Ink else Paper)
            .inkClick(source, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = style, color = if (pressed) Paper else Ink, textDecoration = TextDecoration.Underline)
    }
}

/** 누를 수 있는 한 줄(목록 행) */
@Composable
fun InkRow(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable RowScope.(dark: Boolean) -> Unit) {
    val (source, pressed) = rememberPress()
    val dark = pressed && onClick != null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(if (dark) Ink else Paper)
            .then(if (onClick != null) Modifier.inkClick(source, onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content(dark) }
}

@Composable
fun Section(label: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.label, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Rule(Modifier.padding(top = 4.dp, bottom = 6.dp), thick = true)
        content()
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier, thick: Boolean = false) {
    Box(modifier.fillMaxWidth().height(if (thick) 2.dp else 1.dp).background(Ink))
}

@Composable
fun Boxed(modifier: Modifier = Modifier, inverted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(2.dp, Ink)
            .background(if (inverted) Ink else Paper)
            .padding(14.dp),
        content = content,
    )
}

/** 체크 칸: 채움 = 함, 빈칸 = 안 함, 점선 느낌 = 해당 없음 */
@Composable
fun Square(state: Boolean?, size: Dp = 22.dp, today: Boolean = false) {
    val border = if (today) 3.dp else 1.5.dp
    when (state) {
        true -> Box(Modifier.size(size).background(Ink))
        false -> Box(Modifier.size(size).border(border, Ink))
        null -> Box(Modifier.size(size).padding(size / 2 - 1.dp).background(Muted))
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
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style,
        singleLine = singleLine,
        cursorBrush = SolidColor(Ink),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Column {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = Muted)
                    inner()
                }
                Rule()
            }
        },
    )
}

/** 여러 선택지 중 하나. 선택된 칸은 반전 */
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
                Text(label, style = Type.bodyBold.copy(fontSize = 16.sp), color = if (dark) Paper else Ink, textAlign = TextAlign.Center)
            }
            if (i < options.lastIndex) Box(Modifier.width(2.dp).height(48.dp).background(Ink))
        }
    }
}

@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Box(modifier.fillMaxWidth().height(height).border(1.5.dp, Ink)) {
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
            InkButton("◀ 이전", { page = p - 1 }, Modifier.weight(1f), enabled = p > 0, height = 44.dp, textSize = 16.sp)
            Text("${p + 1} / $pages", style = Type.bodyBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            InkButton("다음 ▶", { page = p + 1 }, Modifier.weight(1f), enabled = p < pages - 1, height = 44.dp, textSize = 16.sp)
        }
    }
}

/** 화면 전체 스크롤 컨테이너(긴 폼용). 짧은 화면은 그냥 Column */
@Composable
fun Screen(modifier: Modifier = Modifier, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxSize().background(Paper)
    Column(
        (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            InkLink("◀ 닫기", onBack)
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = Type.title, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

/** 세션 시작·종료 때 흑→백 한 번 깜빡여 e-ink 잔상을 지운다 */
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

/** 분 경계마다 갱신되는 현재 시각. tick 이 바뀌면 즉시 갱신 */
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

/** 두 번 눌러야 실행되는 위험 버튼(실수 방지) */
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
