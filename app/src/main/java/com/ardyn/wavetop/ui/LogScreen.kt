package com.ardyn.wavetop.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ardyn.wavetop.engine.SurveyMessage
import com.ardyn.wavetop.prefs.Settings
import com.ardyn.wavetop.ui.theme.MonoStyle
import com.ardyn.wavetop.ui.theme.Wt

/** What WaveTop has noticed, newest first: new devices, radio changes, drives, problems. */
@Composable
fun LogScreen(messages: List<SurveyMessage>, settings: Settings) {
    val c = Wt.colors
    if (messages.isEmpty()) {
        EmptyState(Icons.Outlined.Notes, "Nothing logged yet", "New devices, radio changes, wardrives and scan problems are listed here.")
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(messages) { msg ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(timeText(msg.timeMs, settings.clock), style = MonoStyle, color = c.faint, modifier = Modifier.width(84.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (msg.warning) c.weak else c.text,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(color = c.line, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}

