package com.lumecard.app.platform

expect fun formatDate(year: Int, month: Int, day: Int): String

expect fun formatTime(hour: Int, minute: Int): String

expect fun formatDuration(minutes: Int): String

expect fun formatNumber(value: Number): String

expect fun formatPercentage(value: Double): String
