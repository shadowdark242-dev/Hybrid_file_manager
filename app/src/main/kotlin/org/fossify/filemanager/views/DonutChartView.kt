package org.fossify.filemanager.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Simple donut chart: a ring split into colored segments with two lines of text in the middle. */
class DonutChartView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()

    private var segments: List<Pair<Float, Int>> = emptyList()
    private var title = ""
    private var subtitle = ""

    fun setData(segments: List<Pair<Float, Int>>, title: String, subtitle: String, textColor: Int) {
        this.segments = segments
        this.title = title
        this.subtitle = subtitle
        titlePaint.color = textColor
        subtitlePaint.color = textColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val stroke = size * 0.16f
        arcPaint.strokeWidth = stroke

        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - stroke / 2f - 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val total = segments.sumOf { it.first.toDouble() }.toFloat()
        if (total > 0f) {
            var start = -90f
            segments.forEach { (value, color) ->
                val sweep = value / total * 360f
                arcPaint.color = color
                val gap = if (sweep > 3f) 1f else 0f
                canvas.drawArc(oval, start + gap / 2f, max(sweep - gap, 0.5f), false, arcPaint)
                start += sweep
            }
        }

        titlePaint.textSize = size * 0.14f
        subtitlePaint.textSize = size * 0.07f
        canvas.drawText(title, cx, cy, titlePaint)
        canvas.drawText(subtitle, cx, cy + size * 0.10f, subtitlePaint)
    }
}
