package com.xstar.guitar.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class MetronomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun configurePlayMuteAndRestoreDefaults() {
        compose.setContent { MetronomeScreen(onClose = {}) }
        compose.onNodeWithContentDescription("节拍器设置").performClick()
        compose.onNodeWithText("恢复默认设置").performClick()
        compose.onNodeWithText("拍号 ⌄").performClick()
        compose.onNodeWithText("6/8").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithContentDescription("第 4 拍，重音，点击切换").assertExists().performClick()
        compose.onNodeWithContentDescription("第 4 拍，轻音，点击切换").assertExists()
        compose.onNodeWithText("细分 ⌄").performClick()
        compose.onNodeWithText("3 连音 · 每拍3声").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("开始练习").performScrollTo().performClick()
        compose.onNodeWithText("停止练习").assertExists()
        compose.onNodeWithContentDescription("关闭声音").performScrollTo().performClick()
        compose.onNodeWithContentDescription("开启声音").assertExists()
        compose.onNodeWithText("停止练习").performScrollTo().performClick()
        compose.onNodeWithContentDescription("速度 BPM").performScrollTo().performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("节拍器设置").performScrollTo().performClick()
        compose.onNodeWithText("恢复默认设置").performClick()
        compose.onNodeWithContentDescription("120 BPM，点击输入速度").assertExists()
        compose.onNodeWithText("4/4").assertExists()
        compose.onNodeWithContentDescription("关闭声音").assertExists()
        compose.onNodeWithText("已恢复默认设置").assertDoesNotExist()
        compose.onNodeWithText("点击节拍，切换重音 / 轻音 / 静音").assertDoesNotExist()
    }
}
