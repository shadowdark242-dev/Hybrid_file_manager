package org.fossify.filemanager.views

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.graphics.PathParser
import org.fossify.filemanager.helpers.FOLDER_COLOR
import org.xmlpull.v1.XmlPullParser

/**
 * The amber folder icon. It ignores colour filters on purpose, so folders keep their colour
 * in the copy/move picker and other places that tint the folder icon with the theme colour.
 */
class AmberFolderDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FOLDER_COLOR }
    private val source: Path = PathParser.createPathFromPathData(PATH_DATA)
    private val scaled = Path()
    private var size = 0

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        size = (48 * r.displayMetrics.density).toInt()
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) {
            return
        }

        val matrix = Matrix()
        matrix.setScale(b.width() / 48f, b.height() / 48f)
        matrix.postTranslate(b.left.toFloat(), b.top.toFloat())
        source.transform(matrix, scaled)
        canvas.drawPath(scaled, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    // The theme tint is ignored deliberately.
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    private companion object {
        const val PATH_DATA = "M7.05 40q-1.2 0-2.1-0.925Q4.05 38.15 4.05 37V11q0-1.15 0.9-2.075Q5.85 8 7.05 8H19.8q0.6 0 1.175 0.25Q21.55 8.5 21.95 8.9l2.1 2.1h17q1.15 0 2.075 0.925Q44.05 12.85 44.05 14v23q0 1.15-0.925 2.075Q42.2 40 41.05 40z"
    }
}
