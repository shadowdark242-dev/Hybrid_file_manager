package org.fossify.filemanager

import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.helpers.applyHybridTheme

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

        applyHybridTheme(true)
        config.conceptThemeApplied = true
    }
}
