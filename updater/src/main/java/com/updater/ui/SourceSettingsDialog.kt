package com.updater.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import com.updater.config.UpdaterConfigManager
import java.util.UUID

/**
 * 更新源配置与检测模式设置对话框
 * 全面遵循 Material 3 设计规范，通过 ThemeUtils 动态适配宿主主题与深色模式，杜绝硬编码颜色。
 */
object SourceSettingsDialog {

    fun show(context: Context, onSourceChanged: (() -> Unit)? = null) {
        val configManager = UpdaterConfigManager(context)
        val palette = ThemeUtils.M3Palette(context)

        val colorSurface = palette.surface
        val colorSurfaceVariant = palette.surfaceVariant
        val colorPrimary = palette.primary
        val colorOnSurface = palette.onSurface
        val colorOnSurfaceVariant = palette.onSurfaceVariant
        val colorOutline = palette.outline

        var dialog: AlertDialog? = null

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 20), dpToPx(context, 16), dpToPx(context, 20), dpToPx(context, 16))
        }

        // 1. 更新检测方式
        val txtModeSectionTitle = TextView(context).apply {
            text = "检测方式"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorOnSurface)
            setPadding(0, 0, 0, dpToPx(context, 8))
        }
        rootLayout.addView(txtModeSectionTitle)

        val radioGroupMode = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 0, 0, dpToPx(context, 8))
        }

        val rbManual = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = "手动检查\n点击时联网检测"
            textSize = 13f
            setTextColor(colorOnSurface)
            setLineSpacing(dpToPx(context, 2).toFloat(), 1.0f)
            setPadding(dpToPx(context, 6), dpToPx(context, 4), 0, dpToPx(context, 6))
        }

        val rbAuto = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = "自动检测\n启动时静默检查"
            textSize = 13f
            setTextColor(colorOnSurface)
            setLineSpacing(dpToPx(context, 2).toFloat(), 1.0f)
            setPadding(dpToPx(context, 6), dpToPx(context, 4), 0, dpToPx(context, 6))
        }

        radioGroupMode.addView(rbManual)
        radioGroupMode.addView(rbAuto)

        if (configManager.updateMode == UpdaterConfigManager.UPDATE_MODE_AUTO) {
            radioGroupMode.check(rbAuto.id)
        } else {
            radioGroupMode.check(rbManual.id)
        }

        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == rbManual.id) {
                configManager.updateMode = UpdaterConfigManager.UPDATE_MODE_MANUAL
                Toast.makeText(context, "已设为手动检查", Toast.LENGTH_SHORT).show()
            } else if (checkedId == rbAuto.id) {
                configManager.updateMode = UpdaterConfigManager.UPDATE_MODE_AUTO
                Toast.makeText(context, "已设为自动检测", Toast.LENGTH_SHORT).show()
            }
        }
        rootLayout.addView(radioGroupMode)

        // 分割线
        val divider = View(context).apply {
            setBackgroundColor(colorOutline)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 1)).apply {
                bottomMargin = dpToPx(context, 14)
            }
        }
        rootLayout.addView(divider)

        // 2. 更新源列表
        val txtSourceTitle = TextView(context).apply {
            text = "更新源"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorOnSurface)
            setPadding(0, 0, 0, dpToPx(context, 8))
        }
        rootLayout.addView(txtSourceTitle)

        val scrollSources = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 190))
        }
        val sourcesListLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollSources.addView(sourcesListLayout)
        rootLayout.addView(scrollSources)

        // 刷新源列表方法
        fun refreshSourcesUI() {
            sourcesListLayout.removeAllViews()
            val sources = configManager.getSources()
            val selectedId = configManager.selectedSourceId

            for (source in sources) {
                val isSelected = (source.id == selectedId)
                val cardItem = MaterialCardView(context).apply {
                    radius = dpToPx(context, 10).toFloat()
                    strokeWidth = dpToPx(context, 1)
                    strokeColor = if (isSelected) colorPrimary else colorOutline
                    setCardBackgroundColor(if (isSelected) palette.primaryContainer else colorSurface)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dpToPx(context, 8)
                    }
                    layoutParams = lp
                }

                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8))
                }

                val rb = MaterialRadioButton(context).apply {
                    isChecked = isSelected
                    setOnClickListener {
                        configManager.selectedSourceId = source.id
                        refreshSourcesUI()
                        onSourceChanged?.invoke()
                    }
                }

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                        leftMargin = dpToPx(context, 6)
                    }
                    setOnClickListener {
                        configManager.selectedSourceId = source.id
                        refreshSourcesUI()
                        onSourceChanged?.invoke()
                    }
                }

                val titleLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val txtName = TextView(context).apply {
                    text = source.name
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isSelected) palette.onPrimaryContainer else colorOnSurface)
                }
                titleLayout.addView(txtName)

                val txtTag = TextView(context).apply {
                    text = if (source.type == UpdateSourceType.CLOUDFLARE_R2) " CF R2 " else " GitHub "
                    textSize = 10f
                    setTextColor(if (source.type == UpdateSourceType.CLOUDFLARE_R2) palette.onTertiary else palette.onSecondary)
                    background = createBadgeBackground(if (source.type == UpdateSourceType.CLOUDFLARE_R2) palette.tertiary else palette.secondary)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        leftMargin = dpToPx(context, 6)
                    }
                    layoutParams = lp
                }
                titleLayout.addView(txtTag)
                infoLayout.addView(titleLayout)

                val txtUrl = TextView(context).apply {
                    text = source.url
                    textSize = 11f
                    setTextColor(if (isSelected) palette.onPrimaryContainer else colorOnSurfaceVariant)
                    maxLines = 1
                }
                infoLayout.addView(txtUrl)
                itemLayout.addView(rb)
                itemLayout.addView(infoLayout)

                // 如果不是预设内置源，显示删除按钮
                if (!source.isPreset) {
                    val btnDelete = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "删除"
                        textSize = 11f
                        cornerRadius = dpToPx(context, 8)
                        strokeWidth = dpToPx(context, 1)
                        strokeColor = ColorStateList.valueOf(palette.error)
                        setTextColor(palette.error)
                        setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0)
                        minWidth = dpToPx(context, 48)
                        insetTop = 0
                        insetBottom = 0
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(context, 32)).apply {
                            leftMargin = dpToPx(context, 6)
                        }
                        layoutParams = lp
                        setOnClickListener {
                            configManager.deleteSource(source.id)
                            refreshSourcesUI()
                            onSourceChanged?.invoke()
                        }
                    }
                    itemLayout.addView(btnDelete)
                }

                cardItem.addView(itemLayout)
                sourcesListLayout.addView(cardItem)
            }
        }

        refreshSourcesUI()

        // 3. 添加自定义更新源按钮
        val btnAddSource = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "+ 添加更新源"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            cornerRadius = dpToPx(context, 10)
            strokeWidth = dpToPx(context, 1)
            strokeColor = ColorStateList.valueOf(colorPrimary)
            setTextColor(colorPrimary)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44)).apply {
                topMargin = dpToPx(context, 10)
            }
            layoutParams = lp
            setOnClickListener {
                showAddSourceDialog(context, configManager, palette) {
                    refreshSourcesUI()
                    onSourceChanged?.invoke()
                }
            }
        }
        rootLayout.addView(btnAddSource)

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("更新设置")
            .setView(rootLayout)
            .setPositiveButton("完成") { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showAddSourceDialog(
        context: Context,
        configManager: UpdaterConfigManager,
        palette: ThemeUtils.M3Palette,
        onAdded: () -> Unit
    ) {
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 20), dpToPx(context, 12), dpToPx(context, 20), dpToPx(context, 12))
        }

        fun createInput(hintText: String): EditText {
            val normalBg = GradientDrawable().apply {
                cornerRadius = dpToPx(context, 8).toFloat()
                setStroke(dpToPx(context, 1), palette.outline)
                setColor(Color.TRANSPARENT)
            }
            val focusedBg = GradientDrawable().apply {
                cornerRadius = dpToPx(context, 8).toFloat()
                setStroke(dpToPx(context, 1.5f.toInt().coerceAtLeast(1)), palette.primary)
                setColor(Color.TRANSPARENT)
            }
            return EditText(context).apply {
                hint = hintText
                textSize = 14f
                setTextColor(palette.onSurface)
                setHintTextColor(palette.onSurfaceVariant)
                setPadding(dpToPx(context, 12), dpToPx(context, 10), dpToPx(context, 12), dpToPx(context, 10))
                background = normalBg
                setOnFocusChangeListener { _, hasFocus ->
                    background = if (hasFocus) focusedBg else normalBg
                }
            }
        }

        val edtName = createInput("源名称")
        formLayout.addView(edtName)

        val edtUrl = createInput("服务地址或仓库 URL").apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(context, 10)
            }
            layoutParams = lp
        }
        formLayout.addView(edtUrl)

        val rgType = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(context, 10)
            }
            layoutParams = lp
        }

        val rbCf = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = "Cloudflare Pages R2"
            setTextColor(palette.onSurface)
        }
        val rbGh = MaterialRadioButton(context).apply {
            id = View.generateViewId()
            text = "GitHub Releases"
            setTextColor(palette.onSurface)
        }
        rgType.addView(rbCf)
        rgType.addView(rbGh)
        rgType.check(rbCf.id)
        formLayout.addView(rgType)

        MaterialAlertDialogBuilder(context)
            .setTitle("添加更新源")
            .setView(formLayout)
            .setPositiveButton("保存") { d, _ ->
                val name = edtName.text.toString().trim()
                val url = edtUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(context, "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val type = if (rgType.checkedRadioButtonId == rbGh.id) UpdateSourceType.GITHUB_RELEASES else UpdateSourceType.CLOUDFLARE_R2
                val newSource = UpdateSource(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    type = type,
                    isPreset = false
                )
                configManager.addSource(newSource)
                Toast.makeText(context, "更新源已添加", Toast.LENGTH_SHORT).show()
                onAdded()
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createBadgeBackground(@ColorInt color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 8f
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
