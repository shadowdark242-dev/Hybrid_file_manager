package org.fossify.filemanager.helpers

import org.fossify.filemanager.extensions.config
import android.content.Context

/** The app's own colour presets. */
fun Context.applyHybridTheme(dark: Boolean) {
    config.apply {
        isSystemThemeEnabled = false
        isGlobalThemeEnabled = false
        if (dark) {
            backgroundColor = 0xFF0E1116.toInt()
            textColor = 0xFFEDEFF4.toInt()
            primaryColor = 0xFF4D9DFF.toInt()
            accentColor = 0xFF4D9DFF.toInt()
        } else {
            backgroundColor = 0xFFF6F8FB.toInt()
            textColor = 0xFF14181F.toInt()
            primaryColor = 0xFF1565E0.toInt()
            accentColor = 0xFF1565E0.toInt()
        }
    }
}
