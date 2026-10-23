package com.music.spotui.ui.widget

import com.music.aalap.R
import com.music.aalap.ui.widget.AalapWidgetBase

// These two keep the app's old package name: Android remembers placed widgets by their
// class name, so renaming them would blank every widget already on a home screen.

/** Large widget: cover on the left; song, progress and like / previous / play / next on the right. */
class AalapWidgetLarge : AalapWidgetBase(R.layout.widget_large, large = true)

/** Small 2×2 widget: cover and song, then previous / play / next. */
class AalapWidgetCompact : AalapWidgetBase(R.layout.widget_compact, large = false)
