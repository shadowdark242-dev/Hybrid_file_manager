package org.fossify.filemanager

import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp
import org.fossify.filemanager.extensions.config

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        Reprint.initialize(this)
        applyConceptTheme()
    }

    /** Applies the dark concept palette once; later changes from the color settings are not overridden. */
    private fun applyConceptTheme() {
        if (config.conceptThemeApplied) {
            return
        }

        config.apply {
            isSystemThemeEnabled = false
            isGlobalThemeEnabled = false
            backgroundColor = 0xFF0E1116.toInt()
            textColor = 0xFFEDEFF4.toInt()
            primaryColor = 0xFF6AA9FF.toInt()
            accentColor = 0xFF6AA9FF.toInt()
            conceptThemeApplied = true
        }
    }
}
