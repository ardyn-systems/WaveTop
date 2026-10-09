package com.ardyn.wavetop

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ardyn.wavetop.engine.SurveyEngine
import com.ardyn.wavetop.prefs.AppSettings
import com.ardyn.wavetop.ui.AppRoot
import com.ardyn.wavetop.update.Updater
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val settings = AppSettings.get(this)
        applySystemBars(settings.current.theme.colors.isLight)

        // Ask GitHub about new versions at most once a day, if the user left that on.
        Updater.get(this).autoCheckIfDue()

        // System bars follow the theme; the screen stays awake while a wardrive records (if enabled).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(settings.state, SurveyEngine.get(this@MainActivity).state) { s, e ->
                    Triple(s.theme.colors.isLight, s.keepScreenOnWhileDriving, e.wardrive != null)
                }.distinctUntilChanged().collect { (light, keepOn, driving) ->
                    applySystemBars(light)
                    if (keepOn && driving) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        setContent { AppRoot() }
    }

    private fun applySystemBars(light: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (light) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
