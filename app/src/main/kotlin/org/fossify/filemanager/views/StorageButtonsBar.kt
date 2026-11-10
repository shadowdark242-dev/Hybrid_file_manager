package org.fossify.filemanager.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/** A row of rounded storage buttons (internal, SD card, USB...). Scrolls sideways when there are many. */
class StorageButtonsBar(context: Context, attrs: AttributeSet) : HorizontalScrollView(context, attrs) {
    class Item(val key: String, val path: String, val label: String, val iconRes: Int)

    private class ButtonViews(val item: Item, val root: LinearLayout, val icon: ImageView, val label: TextView)

    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val buttons = ArrayList<ButtonViews>()
    private var selectedKey = ""

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        clipToPadding = false
        setPadding(dp(12), dp(8), dp(12), dp(4))
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun setItems(items: List<Item>, onPick: (Item) -> Unit) {
        row.removeAllViews()
        buttons.clear()
        items.forEachIndexed { index, item ->
            val icon = ImageView(context).apply { setImageResource(item.iconRes) }
            val label = TextView(context).apply {
                text = item.label
                maxLines = 1
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumWidth = dp(96)
                setPadding(dp(14), 0, dp(14), 0)
                addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                setOnClickListener { onPick(item) }
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), 1f)
            if (index < items.lastIndex) {
                params.marginEnd = dp(8)
            }
            row.addView(root, params)
            buttons.add(ButtonViews(item, root, icon, label))
        }
        restyle()
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
                    setStroke(dp(1), (text and 0x00FFFFFF) or 0x55000000)
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
