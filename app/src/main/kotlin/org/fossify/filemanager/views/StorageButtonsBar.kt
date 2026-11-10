package org.fossify.filemanager.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/** Rounded storage buttons (internal, SD card, USB...), two per row; extra buttons wrap onto new rows. */
class StorageButtonsBar(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    class Item(val key: String, val path: String, val label: String, val iconRes: Int)

    private class ButtonViews(val item: Item, val root: LinearLayout, val icon: ImageView, val label: TextView)

    private val buttons = ArrayList<ButtonViews>()
    private var selectedKey = ""

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(4))
    }

    fun setItems(items: List<Item>, onPick: (Item) -> Unit) {
        removeAllViews()
        buttons.clear()
        items.chunked(PER_ROW).forEachIndexed { rowIndex, chunk ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (column in 0 until PER_ROW) {
                val item = chunk.getOrNull(column)
                val params = LayoutParams(0, dp(44), 1f)
                if (column < PER_ROW - 1) {
                    params.marginEnd = dp(8)
                }

                if (item == null) {
                    row.addView(android.view.View(context), params)
                } else {
                    row.addView(createButton(item, onPick), params)
                }
            }

            val rowParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (rowIndex > 0) {
                rowParams.topMargin = dp(8)
            }
            addView(row, rowParams)
        }
        restyle()
    }

    private fun createButton(item: Item, onPick: (Item) -> Unit): LinearLayout {
        val icon = ImageView(context).apply { setImageResource(item.iconRes) }
        val label = TextView(context).apply {
            text = item.label
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val root = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            addView(icon, LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(label, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { onPick(item) }
        }
        buttons.add(ButtonViews(item, root, icon, label))
        return root
    }

    fun setSelected(key: String) {
        if (key != selectedKey) {
            selectedKey = key
            restyle()
        }
    }

    /** Re-applies colors, e.g. after the theme changed. */
    fun restyle() {
        val primary = context.getProperPrimaryColor()
        val contrast = primary.getContrastColor()
        val text = context.getProperTextColor()
        buttons.forEach {
            val selected = it.item.key == selectedKey
            val foreground = if (selected) contrast else text
            it.label.setTextColor(foreground)
            it.icon.setColorFilter(foreground)
            it.root.background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                if (selected) {
                    setColor(primary)
                } else {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(dp(2), (text and 0x00FFFFFF) or 0x99000000.toInt())
                }
            }
        }
    }

    private companion object {
        const val PER_ROW = 2
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
