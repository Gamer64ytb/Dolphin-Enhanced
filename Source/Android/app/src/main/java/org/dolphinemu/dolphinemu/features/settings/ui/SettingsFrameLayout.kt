package org.dolphinemu.dolphinemu.features.settings.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * FrameLayout subclass with few Properties added to simplify animations.
 */
class SettingsFrameLayout : FrameLayout {
    private val mVisibleness = 1.0f

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    var yFraction: Float
        get() = y / height
        set(yFraction) {
            val height = height
            y = if ((height > 0)) (yFraction * height) else -9999f
        }

    var visibleness: Float
        get() = mVisibleness
        set(visibleness) {
            scaleX = visibleness
            scaleY = visibleness
            alpha = visibleness
        }
}
