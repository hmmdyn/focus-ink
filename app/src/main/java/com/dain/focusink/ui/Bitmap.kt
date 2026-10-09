package com.dain.focusink.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dain.focusink.core.*
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 비트맵 · 하프톤 그림 도구. 모두 검정 점과 흰 바탕만으로 그린다.
 */

/** 4×4 베이어 행렬. 순서대로 켜면 고르게 퍼진 디더링이 된다 */
private val BAYER = arrayOf(
    intArrayOf(0, 8, 2, 10),
    intArrayOf(12, 4, 14, 6),
    intArrayOf(3, 11, 1, 9),
    intArrayOf(15, 7, 13, 5),
)

private val brushCache = HashMap<Long, Brush>()

/**
 * 디더링 패턴 붓. level 0~16 = 4×4 칸 중 검게 칠할 칸 수.
 * 한 칸은 cell(기본 2dp) 크기라서 e-ink 에서도 회색으로 뭉개지지 않고 점으로 보인다.
 */
@Composable
fun ditherBrush(level: Int, cell: Dp = 2.dp): Brush {
    val px = with(LocalDensity.current) { cell.roundToPx() }.coerceAtLeast(1)
    val lv = level.coerceIn(0, 16)
    val key = lv * 1000L + px
    return brushCache.getOrPut(key) {
        val bmp = Bitmap.createBitmap(4 * px, 4 * px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        val p = Paint().apply { color = android.graphics.Color.BLACK }
        for (y in 0 until 4) for (x in 0 until 4) {
            if (BAYER[y][x] < lv) c.drawRect((x * px).toFloat(), (y * px).toFloat(), ((x + 1) * px).toFloat(), ((y + 1) * px).toFloat(), p)
        }
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

/** 하루 집중 분 → 달력 칸의 디더링 농도 (열품타처럼 많이 할수록 진하게) */
fun minutesLevel(m: Int, goal: Int): Int = when {
    m <= 0 -> 0
    m < goal / 4 -> 3
    m < goal / 2 -> 6
    m < goal * 3 / 4 -> 9
    m < goal -> 12
    else -> 16
}

// ---------------- 5×7 점 행렬 글자 ----------------

private val GLYPHS: Map<Char, String> = mapOf(
    '0' to "01110100011001110101110011000101110", '1' to "00100011000010000100001000010001110",
    '2' to "01110100010000100010001000100011111", '3' to "11111000100010000010000011000101110",
    '4' to "00010001100101010010111110001000010", '5' to "11111100001111000001000011000101110",
    '6' to "00110010001000011110100011000101110", '7' to "11111000010001000100010000100001000",
    '8' to "01110100011000101110100011000101110", '9' to "01110100011000101111000010001001100",
    ':' to "00000011000110000000011000110000000", '.' to "00000000000000000000000000110001100",
    '+' to "00000001000010011111001000010000000", '-' to "00000000000000011111000000000000000",
    '/' to "00001000100001000100010000100010000", ' ' to "00000000000000000000000000000000000",
    'A' to "01110100011000111111100011000110001", 'B' to "11110100011000111110100011000111110",
    'C' to "01110100011000010000100001000101110", 'D' to "11110100011000110001100011000111110",
    'E' to "11111100001000011110100001000011111", 'F' to "11111100001000011110100001000010000",
    'G' to "01110100011000010111100011000101111", 'H' to "10001100011000111111100011000110001",
    'I' to "01110001000010000100001000010001110", 'J' to "00111000100001000010000101001001100",
    'K' to "10001100101010011000101001001010001", 'L' to "10000100001000010000100001000011111",
    'M' to "10001110111010110101100011000110001", 'N' to "10001100011100110101100111000110001",
    'O' to "01110100011000110001100011000101110", 'P' to "11110100011000111110100001000010000",
    'R' to "11110100011000111110101001001010001", 'S' to "01111100001000001110000010000111110",
    'T' to "11111001000010000100001000010000100", 'U' to "10001100011000110001100011000101110",
    'V' to "10001100011000110001100010101000100", 'W' to "10001100011000110101101011010101010",
    'X' to "10001100010101000100010101000110001", 'Y' to "10001100010101000100001000010000100",
    'Z' to "11111000010001000100010001000011111",
)

/**
 * 점 행렬 글자. 켜진 점은 검은 원, 꺼진 점은 아주 작은 점으로 찍어 LCD 처럼 칸이 보이게 한다.
 * 회색 없이 점 크기만으로 켜짐/꺼짐을 나눈다.
 */
@Composable
fun DotText(text: String, modifier: Modifier = Modifier, dot: Dp = 8.dp, gap: Dp = 2.dp, ghost: Boolean = true, square: Boolean = false, color: Color = Ink) {
    val chars = text.uppercase()
    val cols = chars.length * 6 - 1
    val w = dot * cols + gap * (cols - 1)
    val h = dot * 7 + gap * 6
    Canvas(modifier.size(w, h)) {
        val d = dot.toPx()
        val g = gap.toPx()
        val cell = d + g
        chars.forEachIndexed { k, ch ->
            val glyph = GLYPHS[ch] ?: GLYPHS.getValue(' ')
            for (r in 0 until 7) for (q in 0 until 5) {
                val on = glyph[r * 5 + q] == '1'
                if (!on && !ghost) continue
                val cx = (k * 6 + q) * cell + d / 2
                val cy = r * cell + d / 2
                if (on) {
                    if (square) drawRect(color, Offset(cx - d / 2, cy - d / 2), Size(d, d))
                    else drawCircle(color, d / 2, Offset(cx, cy))
                } else {
                    drawCircle(color, (d * 0.1f).coerceAtLeast(1f), Offset(cx, cy))
                }
            }
        }
    }
}

// ---------------- 픽셀 아이콘 ----------------

val PIXEL_ICONS: Map<String, List<String>> = mapOf(
    "today" to listOf("..#.#..", ".#####.", "##.#.##", ".#####.", "..#.#..", "...#...", ".#.#...", "..##...", "...#..."),
    "timer" to listOf("#######", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#.#.#.", "#######"),
    "record" to listOf(".....#.", ".....#.", "...#.#.", "...#.#.", ".#.#.#.", ".#.#.#.", "#######"),
    "habits" to listOf("#######", "#.....#", "#....##", "##..#.#", "#.##..#", "#..#..#", "#######"),
    "coach" to listOf(".#.#.#.", "#######", ".#...#.", "##.#.##", ".#...#.", "#######", ".#.#.#."),
    "tick" to listOf("......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."),
)

@Composable
fun PixelIcon(name: String, modifier: Modifier = Modifier, pixel: Dp = 3.dp, color: Color = Ink) {
    val rows = PIXEL_ICONS[name] ?: return
    val w = rows.maxOf { it.length }
    Canvas(modifier.size(pixel * w, pixel * rows.size)) {
        val p = pixel.toPx()
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c == '#') drawRect(color, Offset(x * p, y * p), Size(p, p)) }
        }
    }
}

// ---------------- 오늘의 판화 ----------------

private const val MAP_W = 210
private const val MAP_H = 180

/** 판 하나의 농도 지도 (0 = 흰색, 1 = 검정) */
private class Layers(val maps: List<FloatArray>)

private val layerCache = object : LinkedHashMap<Long, Layers>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Layers>?) = size > 40
}

private class Rand(seed: Long) {
    private var a = (seed xor 0x5DEECE66DL).toInt()
    fun next(): Float {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (1 or t)
        t = t xor (t + ((t xor (t ushr 7)) * (61 or t)))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296f
    }
}

/**
 * 날짜 씨앗으로 꽃 한 송이를 네 판(줄기 · 잎 · 꽃잎 · 꽃술)으로 나눠 그린다.
 * 안드로이드 Canvas 에 그레이디언트로 그린 뒤 픽셀 농도만 읽어 둔다. 화면에는 망점으로만 옮긴다.
 */
private fun layersFor(seed: Long): Layers = synchronized(layerCache) {
    layerCache.getOrPut(seed) {
        val r = Rand(seed)
        val bmp = Bitmap.createBitmap(MAP_W, MAP_H, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val pixels = IntArray(MAP_W * MAP_H)
        fun clear() = c.drawColor(android.graphics.Color.WHITE)
        fun read(): FloatArray {
            bmp.getPixels(pixels, 0, MAP_W, 0, 0, MAP_W, MAP_H)
            return FloatArray(pixels.size) { 1f - (pixels[it] and 0xFF) / 255f }
        }
        fun black(a: Float) = android.graphics.Color.argb((a * 255).toInt().coerceIn(0, 255), 0, 0, 0)

        val cx = MAP_W * (0.54f + r.next() * 0.08f)
        val cy = MAP_H * (0.36f + r.next() * 0.05f)
        val p0x = MAP_W * (0.22f + r.next() * 0.08f); val p0y = MAP_H.toFloat()
        val p1x = MAP_W * (0.28f + r.next() * 0.1f); val p1y = MAP_H * 0.66f
        val p2x = MAP_W * (0.42f + r.next() * 0.08f); val p2y = MAP_H * (0.56f + r.next() * 0.06f)
        fun bez(t: Float): Pair<Float, Float> {
            val u = 1 - t
            return (u * u * u * p0x + 3 * u * u * t * p1x + 3 * u * t * t * p2x + t * t * t * cx) to
                (u * u * u * p0y + 3 * u * u * t * p1y + 3 * u * t * t * p2y + t * t * t * cy)
        }
        val maps = ArrayList<FloatArray>(4)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1 줄기
        clear()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val stem = android.graphics.Path().apply { moveTo(p0x, p0y); cubicTo(p1x, p1y, p2x, p2y, cx, cy) }
        listOf(10f to 0.3f, 7f to 0.5f, 4f to 0.9f).forEach { (w, a) -> paint.strokeWidth = w; paint.color = black(a); c.drawPath(stem, paint) }
        val (tx, ty) = bez(0.35f)
        paint.strokeWidth = 3f; paint.color = black(0.8f)
        c.drawPath(android.graphics.Path().apply { moveTo(tx, ty); quadTo(tx - 14, ty - 6, tx - 22, ty - 24) }, paint)
        maps += read()

        // 2 잎
        clear()
        paint.style = Paint.Style.FILL
        val leaves = 2 + (r.next() * 2).toInt()
        for (k in 0 until leaves) {
            val t = 0.2f + k * 0.22f + r.next() * 0.06f
            val (x, y) = bez(t)
            val side = if (k % 2 == 1) 1f else -1f
            val len = 24f + r.next() * 10f
            c.save(); c.translate(x, y); c.rotate(((side * (0.6f + r.next() * 0.5f) - 0.4f) * 180f / PI.toFloat()))
            paint.shader = LinearGradient(0f, 0f, len * 2 * side, 0f, black(0.95f), black(0.35f), Shader.TileMode.CLAMP)
            val hw = 7f + r.next() * 3f
            c.drawOval(if (side > 0) 0f else -2 * len, -hw, if (side > 0) 2 * len else 0f, hw, paint)
            paint.shader = null
            paint.color = android.graphics.Color.WHITE; paint.strokeWidth = 1f; paint.style = Paint.Style.STROKE
            c.drawLine(0f, 0f, len * 1.8f * side, 0f, paint)
            paint.style = Paint.Style.FILL
            c.restore()
        }
        maps += read()

        // 3 꽃잎
        clear()
        val n = 8 + (r.next() * 9).toInt()
        val broad = n < 12
        val baseLen = if (broad) 30f else 34f
        val wid = if (broad) 11f else 6f
        for (ring in 0..1) for (k in 0 until n) {
            val a = k.toFloat() / n * 2 * PI.toFloat() + ring * PI.toFloat() / n + r.next() * 0.08f
            val len = (baseLen + r.next() * 6f) * (if (ring == 1) 0.7f else 1f)
            val hw = wid * (if (ring == 1) 0.8f else 1f)
            c.save(); c.translate(cx, cy); c.rotate(a * 180f / PI.toFloat())
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, len * 2, 0f, intArrayOf(black(0.9f), black(0.45f), black(0.2f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawOval(0f, -hw, len * 2, hw, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f; paint.color = android.graphics.Color.WHITE
            c.drawOval(0f, -hw, len * 2, hw, paint)
            c.restore()
        }
        maps += read()

        // 4 꽃술과 꽃가루
        clear()
        paint.style = Paint.Style.FILL; paint.shader = null
        paint.color = black(0.55f); c.drawCircle(cx, cy, 14f, paint)
        paint.color = black(1f); c.drawCircle(cx, cy, 9f, paint)
        repeat(40) {
            val a = r.next() * 2 * PI.toFloat()
            val d = 16f + r.next() * 48f
            c.drawRect(cx + cos(a) * d, cy + sin(a) * d, cx + cos(a) * d + 1.6f, cy + sin(a) * d + 1.6f, paint)
        }
        maps += read()
        bmp.recycle()
        Layers(maps)
    }
}

private fun hash2(i: Int, j: Int): Float {
    var h = i * 374761393 + j * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967296f
}

/**
 * 오늘의 판화. progress 0~1 이 네 판을 차례로 찍는다.
 * 판마다 망점이 다르다: 줄기 = 베이어 디더, 잎 = 선 스크린, 꽃잎 = 원형 망점, 꽃술 = 사각 망점.
 * 찍는 중인 판은 스퀴지를 왼쪽에서 오른쪽으로 민 만큼만 보인다.
 */
@Composable
fun HalftonePrint(seed: Long, progress: Float, modifier: Modifier = Modifier, cell: Dp = 4.dp, background: Boolean = true, aspect: Float = 1.2f) {
    val layers = remember(seed) { layersFor(seed) }
    Canvas(modifier.fillMaxWidth().aspectRatio(aspect)) { drawPrint(layers, progress, cell.toPx(), background) }
}

/** 크기를 정해 그리는 작은 판화(달력, 지난 기록) */
@Composable
fun MiniPrint(seed: Long, progress: Float, size: Dp, cell: Dp = 2.dp) {
    val layers = remember(seed) { layersFor(seed) }
    Canvas(Modifier.size(size, size / 1.15f)) { drawPrint(layers, progress, cell.toPx(), false) }
}

private fun DrawScope.drawPrint(layers: Layers, progress: Float, cellPx: Float, background: Boolean) {
    val cell = cellPx.coerceAtLeast(2f)
    val cols = (size.width / cell).toInt()
    val rows = (size.height / cell).toInt()
    if (cols <= 0 || rows <= 0) return
    // 한 가지 배율로 맞춰 넣어 꽃이 늘어나지 않게 한다
    val sc = min(cols.toFloat() / MAP_W, rows.toFloat() / MAP_H)
    val ox = (cols - MAP_W * sc) / 2f
    val oy = rows - MAP_H * sc
    if (background) {
        for (j in 0 until rows step 3) {
            var i = if ((j / 3) % 2 == 1) 1 else 0
            while (i < cols) {
                drawRect(Ink, Offset(i * cell + cell / 2 - 1f, j * cell + cell / 2 - 1f), Size(2f, 2f))
                i += 3
            }
        }
    }
    // 아직 찍지 않은 판은 밑그림처럼 성긴 점으로 보여 준다. 무엇이 찍힐지 미리 보여야 채우고 싶어진다
    val dot = (cell * 0.35f).coerceAtLeast(1.5f)
    for (k in 0 until 4) {
        if ((progress * 4 - k) >= 1f) continue
        val m = layers.maps[k]
        for (j in 0 until rows step 2) for (i in (j / 2) % 2 until cols step 2) {
            val mx = ((i - ox) / sc).toInt()
            val my = ((j - oy) / sc).toInt()
            if (mx < 0 || my < 0 || mx >= MAP_W || my >= MAP_H) continue
            if (m[my * MAP_W + mx] > 0.3f) drawRect(Ink, Offset(i * cell + (cell - dot) / 2, j * cell + (cell - dot) / 2), Size(dot, dot))
        }
    }
    for (k in 0 until 4) {
        val f = (progress * 4 - k).coerceIn(0f, 1f)
        if (f <= 0f) break
        val m = layers.maps[k]
        for (j in 0 until rows) for (i in 0 until cols) {
            val mx = ((i - ox) / sc).toInt()
            val my = ((j - oy) / sc).toInt()
            if (mx < 0 || my < 0 || mx >= MAP_W || my >= MAP_H) continue
            val d = m[my * MAP_W + mx]
            if (d < 0.05f) continue
            if (f < 1f && i.toFloat() / cols + (hash2(i + k * 97, j) - 0.5f) * 0.08f > f) continue
            val x = i * cell
            val y = j * cell
            when (k) {
                0 -> {
                    val sub = cell / 2
                    for (a in 0..1) for (b in 0..1) {
                        if (BAYER[(j * 2 + b) % 4][(i * 2 + a) % 4] / 16f < d) drawRect(Ink, Offset(x + a * sub, y + b * sub), Size(sub, sub))
                    }
                }
                1 -> {
                    val t = (d * cell * 0.95f).coerceAtLeast(1f)
                    drawRect(Ink, Offset(x, y + (cell - t) / 2), Size(cell, t))
                }
                2 -> {
                    val rr = cell * 0.62f * sqrt(d)
                    if (rr >= 0.6f) drawCircle(Ink, rr, Offset(x + cell / 2, y + cell / 2))
                }
                else -> {
                    val s = min(cell, cell * 1.05f * sqrt(d))
                    drawRect(Ink, Offset(x + (cell - s) / 2, y + (cell - s) / 2), Size(s, s))
                }
            }
        }
    }
}

// ---------------- 분 격자 ----------------

/**
 * 1분 = 한 칸. 지난 분은 검게, 지금 분은 체크무늬, 남은 분은 빈칸.
 * 10칸씩 줄을 바꾼다.
 */
@Composable
fun MinuteGrid(total: Int, done: Int, modifier: Modifier = Modifier, perRow: Int = 10) {
    val rows = ceil(total / perRow.toFloat()).toInt().coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().aspectRatio(perRow / (rows * 0.62f))) {
        val gap = 4.dp.toPx()
        val cw = (size.width - gap * (perRow - 1)) / perRow
        val ch = (size.height - gap * (rows - 1)) / rows
        val stroke = 1.5.dp.toPx()
        for (i in 0 until total) {
            val x = (i % perRow) * (cw + gap)
            val y = (i / perRow) * (ch + gap)
            drawRect(Ink, Offset(x, y), Size(cw, ch), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            val inset = stroke + 2.dp.toPx()
            when {
                i < done -> drawRect(Ink, Offset(x + inset, y + inset), Size(cw - 2 * inset, ch - 2 * inset))
                i == done -> {
                    val s = 2.dp.toPx()
                    var yy = y + inset
                    var row = 0
                    while (yy < y + ch - inset) {
                        var xx = x + inset + if (row % 2 == 1) s else 0f
                        while (xx < x + cw - inset) {
                            drawRect(Ink, Offset(xx, yy), Size(min(s, x + cw - inset - xx), min(s, y + ch - inset - yy)))
                            xx += 2 * s
                        }
                        yy += s; row++
                    }
                }
            }
        }
    }
}
