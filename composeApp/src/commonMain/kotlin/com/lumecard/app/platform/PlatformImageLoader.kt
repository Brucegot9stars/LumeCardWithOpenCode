package com.lumecard.app.platform

import androidx.compose.ui.graphics.painter.Painter

expect fun platformLoadImage(path: String): Painter?
