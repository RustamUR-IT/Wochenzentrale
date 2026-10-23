package com.rustam.woche

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

fun alphaOf(c: Long, a: Float): Int {
    val x = c.toInt()
    return Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(x), Color.green(x), Color.blue(x))
}

fun mixOf(a: Long, b: Long, t: Float): Int {
    val x = a.toInt(); val y = b.toInt()
    fun m(p: Int, q: Int) = (p * (1 - t) + q * t).toInt().coerceIn(0, 255)
    return Color.rgb(m(Color.red(x), Color.red(y)), m(Color.green(x), Color.green(y)), m(Color.blue(x), Color.blue(y)))
}

private fun hmOf(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}

/** Zeichnet Hintergrund, Raster und Blöcke. Wird von App, Hintergrundbild und Vorschau gemeinsam benutzt. */
object Renderer {
    private val DN = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    fun typeface(l: Look, bold: Boolean): Typeface {
        val base = when (l.font) {
            FontStyle.SANS -> Typeface.SANS_SERIF
            FontStyle.SERIF -> Typeface.SERIF
            FontStyle.MONO -> Typeface.MONOSPACE
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun background(c: Canvas, w: Float, h: Float, l: Look, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h, l.theme.bgTop.toInt(), l.theme.bgBottom.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val minute = (System.currentTimeMillis() / 60_000L).toInt()
        val rnd = java.util.Random(7)
        val acc = l.theme.accent
        when (l.fx) {
            Fx.NONE -> {}
            Fx.STARS -> repeat(110) { i ->
                val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h
                val base = 0.15f + 0.45f * rnd.nextFloat()
                val twinkle = 0.6f + 0.4f * (((minute + i) % 5) / 4f)
                p.color = alphaOf(if (i % 4 == 0) acc else 0xFFFFFFFF, base * twinkle)
                c.drawCircle(x, y, (0.8f + rnd.nextFloat() * 2.2f) * s, p)
            }
            Fx.EMBERS -> repeat(70) { i ->
                val x = rnd.nextFloat() * w; val speed = 0.3f + rnd.nextFloat()
                val y = ((rnd.nextFloat() * h - minute * speed * 6f * s) % h + h) % h
                p.color = alphaOf(if (i % 3 == 0) 0xFFFFD27A else acc, 0.25f + 0.5f * rnd.nextFloat())
                c.drawCircle(x, y, (1f + rnd.nextFloat() * 2.5f) * s, p)
            }
            Fx.SNOW -> repeat(80) {
                val x = rnd.nextFloat() * w; val speed = 0.3f + rnd.nextFloat()
                val y = ((rnd.nextFloat() * h + minute * speed * 5f * s) % h + h) % h
                p.color = alphaOf(0xFFFFFFFF, 0.15f + 0.45f * rnd.nextFloat())
                c.drawCircle(x, y, (1f + rnd.nextFloat() * 2.5f) * s, p)
            }
            Fx.CIRCLE -> magicCircle(c, w * 0.5f, h * 0.58f, minOf(w, h) * 0.46f, alphaOf(acc, 0.11f), s, minute)
        }
        p.shader = RadialGradient(w / 2, h / 2, maxOf(w, h) * 0.75f, intArrayOf(0x00000000, 0x99000000.toInt()),
            floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    private fun magicCircle(c: Canvas, cx: Float, cy: Float, r: Float, col: Int, s: Float, minute: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = col; strokeWidth = 2f * s }
        c.drawCircle(cx, cy, r, p)
        c.drawCircle(cx, cy, r * 0.92f, p)
        c.drawCircle(cx, cy, r * 0.55f, p)
        val rot = (minute % 360) * 0.5
        for (i in 0 until 36) {
            val a = Math.toRadians(i * 10.0 + rot)
            val r1 = if (i % 3 == 0) r * 0.86f else r * 0.92f
            c.drawLine(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1, cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r, p)
        }
        val path = Path()
        for (k in 0..1) {
            for (i in 0..3) {
                val a = Math.toRadians(-90.0 + k * 60 + i * 120 - rot)
                val x = cx + cos(a).toFloat() * r * 0.86f
                val y = cy + sin(a).toFloat() * r * 0.86f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        c.drawPath(path, p)
        for (i in 0 until 6) {
            val a = Math.toRadians(-90.0 + i * 60 - rot)
            c.drawCircle(cx + cos(a).toFloat() * r * 0.86f, cy + sin(a).toFloat() * r * 0.86f, r * 0.06f, p)
        }
    }

    /** Stundenbereich so wählen, dass die Termine die ganze Höhe nutzen. */
    fun hourRange(evs: List<Ev>, days: List<LocalDate>, skipSleep: Boolean): Pair<Int, Int> {
        val all = days.flatMap { d -> placeDay(evs, d) }.filter { !skipSleep || it.ev.cat != Cat.SLEEP }
        val t0 = (all.minOfOrNull { it.fromMin / 60 } ?: 8).coerceIn(5, 8)
        val t1 = (all.maxOfOrNull { (it.toMin + 59) / 60 } ?: 21).coerceIn(21, 24)
        return t0 to t1
    }

    fun schedule(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        days: List<LocalDate>, evs: List<Ev>, t0: Int, t1: Int,
        l: Look, s: Float, compact: Boolean, headH: Float, skipSleep: Boolean,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelW = 54f * s
        val left = x + labelW; val right = x + w
        val top = y + headH; val bottom = y + h
        val ph = (bottom - top) / (t1 - t0)
        val today = LocalDate.now()
        p.typeface = typeface(l, false)
        for (hr in t0 until t1) {
            val yy = top + (hr - t0) * ph
            p.color = l.theme.line.toInt(); p.strokeWidth = 1f
            c.drawLine(left, yy, right, yy, p)
            p.color = l.theme.mute.toInt(); p.textSize = 24f * s
            c.drawText("%02d".format(hr), x + 8f * s, yy + 8f * s, p)
        }
        val colW = (right - left) / days.size
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        days.forEachIndexed { di, d ->
            val cx = left + di * colW
            if (headH > 0f) {
                var label = "${DN[d.dayOfWeek.value - 1]} ${d.dayOfMonth}"
                if (l.caps) label = label.uppercase()
                p.typeface = typeface(l, true)
                p.textSize = (if (compact) 26f else 32f) * s
                p.color = (if (d == today) l.theme.now else l.theme.text).toInt()
                c.drawText(label, cx + 6f * s, top - 18f * s, p)
                if (d == today) {
                    p.strokeWidth = 3f * s
                    c.drawLine(cx + 6f * s, top - 9f * s, cx + colW - 8f * s, top - 9f * s, p)
                }
            }
            for (pl in placeDay(evs, d)) {
                if (skipSleep && pl.ev.cat == Cat.SLEEP) continue
                val a2 = (pl.fromMin / 60f).coerceAtLeast(t0.toFloat())
                val b2 = (pl.toMin / 60f).coerceAtMost(t1.toFloat())
                if (b2 <= a2) continue
                val cw = colW / pl.cols
                val y0 = top + (a2 - t0) * ph + 1.5f
                val y1 = top + (b2 - t0) * ph - 1.5f
                val r = RectF(cx + pl.col * cw + 2f, y0, cx + (pl.col + 1) * cw - 2f, y1)
                block(c, r, pl.ev.title, "${hmOf(pl.ev.startMs)}–${hmOf(pl.ev.endMs)}", pl.ev.cat, l, s, compact, tp, p)
            }
        }
        val n = LocalDateTime.now(); val m = n.hour + n.minute / 60f
        val ti = days.indexOf(today)
        if (ti >= 0 && m >= t0 && m <= t1) {
            val yy = top + (m - t0) * ph
            p.style = Paint.Style.FILL
            p.color = l.theme.now.toInt(); p.strokeWidth = 3.5f * s
            if (l.glow) { p.color = alphaOf(l.theme.now, 0.3f); p.strokeWidth = 9f * s; c.drawLine(left + ti * colW, yy, left + (ti + 1) * colW, yy, p) }
            p.color = l.theme.now.toInt(); p.strokeWidth = 3.5f * s
            c.drawLine(left + ti * colW, yy, left + (ti + 1) * colW, yy, p)
            c.drawCircle(left + ti * colW, yy, 8f * s, p)
        }
    }

    /** Ein Termin-Block im gewählten Rahmenstil. Wächst bei Bedarf auf eine Textzeile Mindesthöhe. */
    fun block(c: Canvas, r0: RectF, rawTitle: String, time: String?, cat: Cat, l: Look, s: Float, compact: Boolean,
              tp: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG), p: Paint = Paint(Paint.ANTI_ALIAS_FLAG)) {
        val fg = l.fg(cat)
        val padX = 9f * s; val padY = 6f * s
        val tw = (r0.width() - padX - 6f * s).toInt()
        if (tw < 12) return
        var title = shortTitle(rawTitle, compact)
        if (l.caps) title = title.uppercase()
        tp.typeface = typeface(l, true)
        tp.color = fg.toInt()
        var size = (if (compact) 25f else 32f) * s
        val minSize = (if (compact) 17f else 22f) * s
        val longest = title.split(Regex("\\s+")).maxByOrNull { it.length } ?: title
        tp.textSize = size
        while (size > minSize && tp.measureText(longest) > tw) { size -= 1f * s; tp.textSize = size }
        val lineH = tp.textSize * 1.15f
        val r = RectF(r0.left, r0.top, r0.right, maxOf(r0.bottom, r0.top + lineH + 2 * padY))
        val rad = when (l.frame) { Frame.ROUND -> 10f * s; Frame.BRACKETS -> 3f * s; Frame.ENGRAVED -> 5f * s }

        if (l.glow) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 8f * s; p.color = alphaOf(fg, 0.08f); c.drawRoundRect(r, rad, rad, p)
            p.strokeWidth = 4f * s; p.color = alphaOf(fg, 0.16f); c.drawRoundRect(r, rad, rad, p)
        }
        p.style = Paint.Style.FILL
        p.color = mixOf(l.theme.panel, fg, l.fill)
        c.drawRoundRect(r, rad, rad, p)
        when (l.frame) {
            Frame.ROUND -> {
                p.color = fg.toInt()
                c.drawRect(r.left, r.top + 4f * s, r.left + 4f * s, r.bottom - 4f * s, p)
            }
            Frame.BRACKETS -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f * s; p.color = alphaOf(fg, 0.35f); c.drawRect(r, p)
                p.strokeWidth = 2.5f * s; p.color = fg.toInt()
                val k = minOf(14f * s, r.width() / 3, r.height() / 3)
                c.drawLine(r.left, r.top, r.left + k, r.top, p); c.drawLine(r.left, r.top, r.left, r.top + k, p)
                c.drawLine(r.right, r.top, r.right - k, r.top, p); c.drawLine(r.right, r.top, r.right, r.top + k, p)
                c.drawLine(r.left, r.bottom, r.left + k, r.bottom, p); c.drawLine(r.left, r.bottom, r.left, r.bottom - k, p)
                c.drawLine(r.right, r.bottom, r.right - k, r.bottom, p); c.drawLine(r.right, r.bottom, r.right, r.bottom - k, p)
            }
            Frame.ENGRAVED -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f * s; p.color = alphaOf(l.theme.accent, 0.85f); c.drawRoundRect(r, rad, rad, p)
                val i = 3.5f * s
                if (r.height() > 4 * i && r.width() > 4 * i) {
                    p.strokeWidth = 1f * s; p.color = alphaOf(fg, 0.5f)
                    c.drawRoundRect(RectF(r.left + i, r.top + i, r.right - i, r.bottom - i), rad, rad, p)
                }
                p.style = Paint.Style.FILL; p.color = l.theme.accent.toInt()
                val d = 4f * s
                val path = Path().apply { moveTo(r.centerX(), r.top - d); lineTo(r.centerX() + d, r.top); lineTo(r.centerX(), r.top + d); lineTo(r.centerX() - d, r.top); close() }
                c.drawPath(path, p)
            }
        }
        p.style = Paint.Style.FILL

        val text = title + if (time != null && r.height() > lineH * 4) "\n$time" else ""
        val maxLines = ((r.height() - padY) / lineH).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, tw)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1f).setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        c.save(); c.clipRect(r); c.translate(r.left + padX, r.top + padY)
        layout.draw(c); c.restore()
    }

    /** Drei Beispielblöcke für die Stil-Vorschau im Aussehen-Menü. */
    fun preview(c: Canvas, w: Float, h: Float, l: Look, s: Float) {
        val pad = 14f * s
        val cw = (w - 4 * pad) / 3
        val samples = listOf(Triple("Vorlesung", Cat.FH, 0.15f), Triple("Training", Cat.GYM, 0.35f), Triple("Lernen: Kapitel 3", Cat.STUDY, 0.2f))
        samples.forEachIndexed { i, (t, cat, off) ->
            val x = pad + i * (cw + pad)
            block(c, RectF(x, pad + off * h * 0.5f, x + cw, h - pad), t, "10:00–11:30", cat, l, s, true)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.theme.now.toInt(); strokeWidth = 3f * s }
        c.drawLine(pad, h * 0.62f, w - pad, h * 0.62f, p)
        c.drawCircle(pad, h * 0.62f, 6f * s, p)
    }
}
