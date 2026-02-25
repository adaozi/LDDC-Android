package com.example.lddc.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI 设计常量
 *
 * 统一应用的视觉风格，确保横竖屏一致性
 */
object UiConstants {

    /**
     * 圆角半径
     */
    object CornerRadius {
        /** 小圆角 - 用于按钮、输入框等 */
        val Small = 8.dp

        /** 中等圆角 - 用于卡片 */
        val Medium = 12.dp

        /** 大圆角 - 用于主卡片 */
        val Large = 16.dp

        /** Logo 圆角 */
        val Logo = 24.dp
    }

    /**
     * 卡片阴影
     */
    object Elevation {

        /** 低阴影 - 列表项 */
        val Low = 1.dp

        /** 中等阴影 - 普通卡片 */
        val Medium = 2.dp

    }

    /**
     * 间距
     */
    object Spacing {
        /** 超小间距 */
        val XS = 4.dp

        /** 小间距 */
        val Small = 8.dp

        /** 中间距 */
        val Medium = 12.dp

        /** 大间距 */
        val Large = 16.dp

        /** 超大间距 */
        val XLarge = 20.dp

        /** 巨大间距 */
        val XXLarge = 24.dp

    }

    /**
     * 内边距
     */
    object Padding {
        /** 小内边距 */
        val Small = 8.dp

        /** 中等内边距 */
        val Medium = 12.dp

        /** 大内边距 */
        val Large = 16.dp

        /** 超大内边距 */
        val XLarge = 20.dp

        /** 巨大内边距 */
        val XXLarge = 24.dp
    }

    /**
     * 字体大小
     */
    object FontSize {
        /** 小字体 - 标签、说明 */
        val Small = 12.sp

        /** 正常字体 */
        val Normal = 14.sp

        /** 中等字体 */
        val Medium = 16.sp

        /** 大字体 - 标题 */
        val Large = 18.sp

        /** 超大字体 - 大标题 */
        val XLarge = 20.sp

        /** 巨大字体 - 应用名称 */
        val XXLarge = 64.sp
    }

    /**
     * 尺寸
     */
    object Size {
        /** Logo 尺寸 */
        val Logo = 120.dp

        /** 中等图标 */
        val IconMedium = 24.dp

        /** 大图标 */
        val IconLarge = 48.dp

    }

}
