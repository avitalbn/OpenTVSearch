package org.opentvsearch.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Minimal TV Material 3 dark theme wrapper. Google TV surfaces are dark by default and
 * viewed at distance, so we lean on the stock [darkColorScheme] rather than a bespoke
 * palette for M1.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OpenTvSearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content,
    )
}
