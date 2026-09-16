package br.com.ne3d.spbshellmodern.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.ne3d.spbshellmodern.model.WidgetRenderState

/**
 * Fase 3: tiny presentation helper shared by carousel previews only.
 *
 * The FULL_PANEL owns the real GL scene ([ShellPrototypeScreen]); the carousel
 * keeps lightweight Compose previews. This helper only centralizes preview
 * framing (padding / alignment / scale / aspect) so the four previews occupy
 * the card the same way instead of each drifting on its own.
 *
 * No data logic, no interaction and no GL animation live here.
 */
internal object SpbCarouselPreview {
    /** Matches the full-panel host rhythm: edge-to-edge content, centered. */
    val previewPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

    /** Full panels compose around the center; previews do the same. */
    val contentAlignment = Alignment.CenterHorizontally
    val contentArrangement = Arrangement.Center

    /** Previews render at host scale; artwork fills width like the GL scene. */
    const val previewScale = 1f

    /** Square artwork zone (globe / weather sun) mirrors the GL framing. */
    const val artworkAspectRatio = 1f
}

internal fun isCarouselPreview(state: WidgetRenderState, interactive: Boolean): Boolean =
    state == WidgetRenderState.CAROUSEL_PREVIEW || !interactive

@Composable
internal fun CarouselPreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SpbCarouselPreview.previewPadding),
        verticalArrangement = SpbCarouselPreview.contentArrangement,
        horizontalAlignment = SpbCarouselPreview.contentAlignment,
        content = content
    )
}
