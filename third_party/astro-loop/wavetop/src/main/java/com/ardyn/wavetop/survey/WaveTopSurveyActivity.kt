package com.ardyn.wavetop.survey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ardyn.wavetop.survey.ui.WaveTopSurveyApp

class WaveTopSurveyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WaveTopSurveyApp()
        }
    }
}
