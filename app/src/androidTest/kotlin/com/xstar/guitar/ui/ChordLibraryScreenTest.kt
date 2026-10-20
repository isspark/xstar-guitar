package com.xstar.guitar.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ChordLibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filterSheetsAndDetailShowRightHandModes() {
        compose.setContent { ChordLibraryScreen(onClose = {}) }
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithText("下一页 →").performClick()
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithText("根音").performClick()
        compose.onNodeWithText("B♭").assertIsDisplayed().performClick()
        compose.onNodeWithText("类型").performClick()
        compose.onNodeWithText("强力和弦").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("全部类型").performScrollTo().performClick()
        compose.onNodeWithText("根音").performClick()
        compose.onNodeWithText("全部根音").performClick()
        compose.onNodeWithText("搜索 C、Am、Cmaj7").performTextInput("Cmaj7")
        compose.onNodeWithText("Cmaj7").performClick()
        compose.onNodeWithText("听听这个和弦").assertIsDisplayed()
        compose.onNodeWithText("P i m a 分解").performClick()
        compose.onNodeWithText("P 拇指 · i 食指 · m 中指 · a 无名指").assertIsDisplayed()
        compose.onNodeWithText("↗ 和弦组成").performClick()
        compose.onNodeWithText("Cmaj7 · 和弦组成").assertIsDisplayed()
    }
}
