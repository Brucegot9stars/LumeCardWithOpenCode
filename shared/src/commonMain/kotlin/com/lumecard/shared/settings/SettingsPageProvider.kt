package com.lumecard.shared.settings

interface SettingsPageProvider {
    val pageKey: String
    val entries: List<SettingsIndexEntry>
}
