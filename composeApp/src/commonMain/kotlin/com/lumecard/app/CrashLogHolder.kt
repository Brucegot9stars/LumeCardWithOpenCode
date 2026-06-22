package com.lumecard.app

object CrashLogHolder {
    @Volatile
    var lastCrashLog: String? = null
}
