package com.lumecard.app.ui.screens.settings

enum class AnswerDisplayMode(val displayName: String, val description: String) {
    FLIP("卡片翻转", "点击显示答案时执行卡片翻转动画"),
    SPLIT("上下对照", "问题和答案同屏展示，上方为问题，下方为答案")
}
