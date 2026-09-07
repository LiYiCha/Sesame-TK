package com.updater.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors

/**
 * Material 3 动态主题取色工具
 * 遵循 Android M3 设计规范，直接从当前 Context 所在的主题属性中解析语义化颜色，
 * 避免在业务代码中硬编码颜色值或手动根据夜间模式判断色值。
 */
object ThemeUtils {

    /**
     * 解析主题属性颜色。优先使用 MaterialColors 工具类，若解析失败则回退至原生 TypedValue 解析。
     */
    @ColorInt
    fun getColor(context: Context, @AttrRes attr: Int, @ColorInt defaultColor: Int = Color.TRANSPARENT): Int {
        return try {
            MaterialColors.getColor(context, attr, defaultColor)
        } catch (_: Throwable) {
            try {
                val tv = TypedValue()
                if (context.theme.resolveAttribute(attr, tv, true)) {
                    if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        tv.data
                    } else if (tv.resourceId != 0) {
                        ContextCompat.getColor(context, tv.resourceId)
                    } else {
                        defaultColor
                    }
                } else {
                    defaultColor
                }
            } catch (_: Throwable) {
                defaultColor
            }
        }
    }

    /**
     * 创建基于指定颜色的单色 ColorStateList
     */
    fun toColorStateList(@ColorInt color: Int): ColorStateList {
        return ColorStateList.valueOf(color)
    }

    /**
     * M3 语义调色板
     * 自动从当前 Context（如 Activity 或 Application）主题获取 M3 核心槽位颜色。
     */
    class M3Palette(val context: Context) {
        // 主色与主要容器（用于主要按钮、高亮选中、品牌强调）
        val primary: Int = getColor(
            context,
            com.google.android.material.R.attr.colorPrimary,
            getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#2D5A27"))
        )
        val onPrimary: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnPrimary,
            Color.WHITE
        )
        val primaryContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorPrimaryContainer,
            primary
        )
        val onPrimaryContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            onPrimary
        )

        // 表面色与背景色（用于页面底色、卡片背景、主要与次要文字）
        val surface: Int = getColor(
            context,
            com.google.android.material.R.attr.colorSurface,
            getColor(context, android.R.attr.colorBackground, Color.WHITE)
        )
        val onSurface: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            getColor(context, android.R.attr.textColorPrimary, Color.BLACK)
        )
        val surfaceVariant: Int = getColor(
            context,
            com.google.android.material.R.attr.colorSurfaceVariant,
            surface
        )
        val onSurfaceVariant: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            getColor(context, android.R.attr.textColorSecondary, Color.DKGRAY)
        )

        // 边框与轮廓（用于分割线、描边卡片、未激活控件描边）
        val outline: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOutline,
            Color.parseColor("#79747E")
        )
        val outlineVariant: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOutlineVariant,
            outline
        )

        // 错误色（用于删除、失败提示、错误标红）
        val error: Int = getColor(
            context,
            com.google.android.material.R.attr.colorError,
            Color.parseColor("#B3261E")
        )
        val onError: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnError,
            Color.WHITE
        )
        val errorContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorErrorContainer,
            error
        )
        val onErrorContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnErrorContainer,
            onError
        )

        // 第三强调色与次强调色（用于次级标签、多源区分等）
        val secondary: Int = getColor(
            context,
            com.google.android.material.R.attr.colorSecondary,
            primary
        )
        val onSecondary: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnSecondary,
            onPrimary
        )
        val secondaryContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorSecondaryContainer,
            secondary
        )
        val onSecondaryContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            onSecondary
        )

        val tertiary: Int = getColor(
            context,
            com.google.android.material.R.attr.colorTertiary,
            primary
        )
        val onTertiary: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnTertiary,
            onPrimary
        )
        val tertiaryContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorTertiaryContainer,
            tertiary
        )
        val onTertiaryContainer: Int = getColor(
            context,
            com.google.android.material.R.attr.colorOnTertiaryContainer,
            onTertiary
        )
    }
}
