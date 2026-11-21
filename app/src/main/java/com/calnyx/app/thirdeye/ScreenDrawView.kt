// FILE: app/src/main/java/com/calnyx/app/ThirdEye/ScreenDrawView.kt
// NEW FILE - on-screen marks drawn DURING a screen recording
// (2026-11-20).
//
// The view lives in a transparent full-screen overlay window
// owned by ScreenRecordService. It draws every mark the user
// made (pen / circle / rectangle / triangle / arrow, in the
// chosen colour, plus emoji stickers), so the marks are visible on
// any screen content AND in the recorded video, because the
// overlay itself is recorded.
//
// Touch rule: the view only consumes touches while a drawing
// tool is active. With no tool (DrawTool.NONE) onTouchEvent
// returns false AND the window carries FLAG_NOT_TOUCHABLE (set
// by the service), so the phone works normally under it - a
// video playing underneath keeps playing, and no tap reaches
// it. While a tool IS active the overlay eats every touch, so
// nothing underneath reacts: only the pen / shape / sticker
// lands. Shapes are OUTLINE ONLY - no fill at all, so the area
// inside a circle or rectangle stays crystal clear, with no
// darkening and no colour effect on the background. The pen is
// a red stroke only. Clear wipes every mark at once.
package com.calnyx.app.thirdeye

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.calnyx.app.theme.CalNyxRed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class DrawTool {
    NONE, PEN, CIRCLE, RECT, TRIANGLE, ARROW, EMOJI
}

sealed class DrawMark {
    // Every mark remembers its own colour, so changing the
    // colour in the toolbar never repaints the marks that are
    // already on the screen. Emoji stickers are text - they do
    // not use a colour (their 0 is never drawn).
    abstract val color: Int
    // Freehand pen: the points the finger travelled through.
    data class Freehand(val points: MutableList<PointF>, override val color: Int) : DrawMark()
    // Shapes: drag from one corner to the opposite one.
    data class Circle(val start: PointF, var end: PointF, override val color: Int) : DrawMark()
    data class Rect(val start: PointF, var end: PointF, override val color: Int) : DrawMark()
    // Triangle: drag from one corner to the opposite one -
    // apex top-centre, base along the bottom.
    data class Triangle(val start: PointF, var end: PointF, override val color: Int) : DrawMark()
    // Arrow: drag from tail to tip - the head is drawn at end.
    data class Arrow(val start: PointF, var end: PointF, override val color: Int) : DrawMark()
    // Emoji sticker: one tap stamps it at that spot.
    data class Emoji(val position: PointF, val text: String) : DrawMark() {
        override val color: Int = 0
    }
}

class ScreenDrawView(context: Context) : View(context) {

    // The active tool, set by ScreenRecordService. NONE means
    // "not drawing" - touches pass straight through.
    var tool: DrawTool = DrawTool.NONE
    // The emoji picked in the toolbar, stamped when the EMOJI
    // tool is active.
    var stampText: String? = null

    private val marks = mutableListOf<DrawMark>()
    private var current: DrawMark? = null

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CalNyxRed.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // The colour new marks are drawn in, picked in the
    // toolbar (red by default). Setting it repaints the
    // stroke, but each mark already keeps its own colour, so
    // old marks never change.
    var markColor: Int = CalNyxRed.toArgb()
        set(value) {
            field = value
            strokePaint.color = value
        }

    // Emoji stickers are drawn as text, in the system's own
    // colour emoji font - no colour of ours on them.
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(56f)
    }

    fun clearMarks() {
        marks.clear()
        current = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tool == DrawTool.NONE) return false
        val point = PointF(event.x, event.y)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Emoji: one tap stamps one sticker at that spot.
                if (tool == DrawTool.EMOJI) {
                    marks.add(DrawMark.Emoji(point, stampText ?: "⭐"))
                    invalidate()
                    return true
                }
                current = when (tool) {
                    DrawTool.PEN -> DrawMark.Freehand(mutableListOf(point), markColor)
                    DrawTool.CIRCLE -> DrawMark.Circle(point, point, markColor)
                    DrawTool.RECT -> DrawMark.Rect(point, point, markColor)
                    DrawTool.TRIANGLE -> DrawMark.Triangle(point, point, markColor)
                    DrawTool.ARROW -> DrawMark.Arrow(point, point, markColor)
                    DrawTool.NONE, DrawTool.EMOJI -> null
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val mark = current ?: return true
                when (mark) {
                    is DrawMark.Freehand -> mark.points.add(point)
                    is DrawMark.Circle -> mark.end = point
                    is DrawMark.Rect -> mark.end = point
                    is DrawMark.Triangle -> mark.end = point
                    is DrawMark.Arrow -> mark.end = point
                    is DrawMark.Emoji -> Unit
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current?.let { marks.add(it) }
                current = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (mark in marks) drawMark(canvas, mark)
        current?.let { drawMark(canvas, it) }
    }

    private fun drawMark(canvas: Canvas, mark: DrawMark) {
        // Each mark is drawn in its own colour - the paint is
        // set per mark, so a colour change never repaints the
        // marks already on the screen. Emoji stickers are text,
        // they do not use this paint.
        if (mark !is DrawMark.Emoji) strokePaint.color = mark.color
        when (mark) {
            is DrawMark.Freehand -> {
                val points = mark.points
                if (points.isEmpty()) return
                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
                canvas.drawPath(path, strokePaint)
            }
            is DrawMark.Circle -> {
                val left = minOf(mark.start.x, mark.end.x)
                val right = maxOf(mark.start.x, mark.end.x)
                val top = minOf(mark.start.y, mark.end.y)
                val bottom = maxOf(mark.start.y, mark.end.y)
                canvas.drawOval(left, top, right, bottom, strokePaint)
            }
            is DrawMark.Rect -> {
                val left = minOf(mark.start.x, mark.end.x)
                val right = maxOf(mark.start.x, mark.end.x)
                val top = minOf(mark.start.y, mark.end.y)
                val bottom = maxOf(mark.start.y, mark.end.y)
                canvas.drawRect(left, top, right, bottom, strokePaint)
            }
            is DrawMark.Triangle -> {
                val left = minOf(mark.start.x, mark.end.x)
                val right = maxOf(mark.start.x, mark.end.x)
                val top = minOf(mark.start.y, mark.end.y)
                val bottom = maxOf(mark.start.y, mark.end.y)
                val path = Path()
                path.moveTo((left + right) / 2f, top)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close()
                canvas.drawPath(path, strokePaint)
            }
            is DrawMark.Arrow -> {
                // Shaft from tail to tip, plus a V head at the
                // tip: two short lines back at 30 degrees each
                // side. The head shrinks on a very short drag
                // so it never outgrows the shaft.
                val dx = mark.end.x - mark.start.x
                val dy = mark.end.y - mark.start.y
                val len = hypot(dx, dy)
                canvas.drawLine(mark.start.x, mark.start.y, mark.end.x, mark.end.y, strokePaint)
                if (len > 1f) {
                    val angle = atan2(dy, dx)
                    val headLen = minOf(len * 0.4f, dp(24f))
                    val spread = PI.toFloat() / 180f * 30f
                    val back = angle + PI.toFloat()
                    canvas.drawLine(
                        mark.end.x, mark.end.y,
                        mark.end.x + headLen * cos(back - spread),
                        mark.end.y + headLen * sin(back - spread),
                        strokePaint
                    )
                    canvas.drawLine(
                        mark.end.x, mark.end.y,
                        mark.end.x + headLen * cos(back + spread),
                        mark.end.y + headLen * sin(back + spread),
                        strokePaint
                    )
                }
            }
            is DrawMark.Emoji -> {
                // drawText takes the baseline - lift it so the
                // emoji sits centred on the tapped spot.
                canvas.drawText(
                    mark.text,
                    mark.position.x,
                    mark.position.y + emojiPaint.textSize * 0.35f,
                    emojiPaint
                )
            }
        }
    }
}
