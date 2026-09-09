package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

/** Mirrors the host's hardware outline clip when recording a RemoteViews software capture. */
internal fun drawWidget(view: View, bitmap: Bitmap) {
    val canvas = Canvas(bitmap)
    if (view.clipToOutline) {
        // View initializes its background bounds during drawing on a hardware host.
        view.background?.setBounds(0, 0, view.width, view.height)
        val outline = Outline()
        view.outlineProvider.getOutline(view, outline)
        val bounds = Rect()
        if (outline.getRect(bounds)) canvas.clipPath(Path().apply {
            addRoundRect(RectF(bounds), outline.radius, outline.radius, Path.Direction.CW)
        })
    }
    view.draw(canvas)
}
