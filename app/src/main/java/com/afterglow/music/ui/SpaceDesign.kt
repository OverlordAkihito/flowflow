package com.afterglow.music.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/** Shared measurements for the app's calm, rounded space-console surfaces. */
internal object SpaceDesign {
    val cardRadius = 24.dp
    val sheetRadius = 30.dp
    val controlRadius = 18.dp
    val compactRadius = 14.dp
    val contentMaxWidth = 760.dp
    val dockMaxWidth = 700.dp
    val screenGutter = 20.dp
    val panelPadding = 16.dp
}

/** A lightweight translucent surface; intentionally avoids per-frame blur work. */
@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(SpaceDesign.cardRadius),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.border(
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = .34f),
                        colors.outline.copy(alpha = .38f),
                        colors.secondary.copy(alpha = .18f),
                    ),
                ),
            ),
            shape,
        ),
        shape = shape,
        color = colors.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}
