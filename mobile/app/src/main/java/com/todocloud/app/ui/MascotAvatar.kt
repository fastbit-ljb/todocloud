package com.todocloud.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.todocloud.app.branding.MascotSet
import com.todocloud.app.branding.MascotState
import kotlin.math.sqrt

/**
 * Settings → 账号 avatar: [set]'s picture for [state] on a plain white plate, in light and dark
 * mode alike. The plate takes the size of the letter avatar it replaces, so the account row
 * keeps its height and text position. The picture is never clipped: [MascotAvatarLayout] keeps
 * the whole picture inside the plate.
 */
@Composable
internal fun MascotAvatar(set: MascotSet, state: MascotState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MascotAvatarLayout.PLATE_DP.dp)
            .background(Color.White, RoundedCornerShape(MascotAvatarLayout.CORNER_DP.dp)),
    ) {
        Image(
            painter = painterResource(set.avatar(state)),
            // Decorative: the account name next to it already says whose card this is.
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(MascotAvatarLayout.INSET_DP.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Geometry of [MascotAvatar], in dp. */
internal object MascotAvatarLayout {
    /** The size of the letter avatar the account card used before. */
    const val PLATE_DP = 44f
    const val CORNER_DP = 12f
    /** Gap between the plate edge and the square picture canvas. */
    const val INSET_DP = 4f

    /**
     * Whether the whole square picture canvas lies inside the rounded plate, so no part of any
     * set's artwork (ears, hair) can fall outside the white plate, whatever the artwork. The
     * only place a canvas corner can leave a rounded square is the corner arc.
     */
    fun pictureInsidePlate(
        plate: Float = PLATE_DP,
        corner: Float = CORNER_DP,
        inset: Float = INSET_DP,
    ): Boolean {
        if (inset < 0f || 2 * inset >= plate || corner < 0f || 2 * corner > plate) return false
        if (inset >= corner) return true
        return (corner - inset) * sqrt(2f) <= corner
    }
}
