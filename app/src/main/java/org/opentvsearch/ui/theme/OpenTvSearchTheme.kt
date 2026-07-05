package org.opentvsearch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * TV Material 3 dark theme. Google TV surfaces are dark and viewed at distance, so instead of the
 * bare stock [darkColorScheme] we define an explicit palette: a brand violet [primary] (used for
 * accents/cursor/focus), readable `onSurface`/`onSurfaceVariant` text colors, and a `surface` that
 * is distinct from `surfaceVariant` so fields and cards separate from the background. Downstream
 * composables pull structural colors from this scheme rather than hard-coding `Color(...)`.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OpenTvSearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB69DF8),          // brand violet accent
            onPrimary = Color(0xFF1A1033),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF1A1033),
            background = Color(0xFF0E0E12),
            onBackground = Color(0xFFE6E1E5),
            surface = Color(0xFF17171C),          // page / card base
            onSurface = Color(0xFFECE6F0),         // primary readable text
            surfaceVariant = Color(0xFF2C2C36),    // field / placeholder-tile container
            onSurfaceVariant = Color(0xFFC9C4D0),  // placeholder / secondary text
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
        ),
        content = content,
    )
}
