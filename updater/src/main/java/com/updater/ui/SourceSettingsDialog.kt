package com.updater.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import java.util.UUID

/**
 * 更新源配置与检测模式设置对话框
 * 纯原生控件实现，彻底移除 Material 组件依赖，杜绝 colorPrimary 检查闪退。
 */
object SourceSettingsDialog {

    fun show(context: Context, onSourceChanged: (() -> Unit)? = null) {
        val configManager = UpdaterConfigManager(context)
        val palette = ThemeUtils.M3Palette(context)

        val colorSurface = palette.surface
        val colorSurfaceVariant = palette.surfaceVariant
        val colorOnSurface = palette.onSurface
        val colorOnSurfaceVariant = palette.onSurfaceVariant
        val colorPrimary = palette.primary
        val colorOutline = palette.outline

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 18), dpToPx(context, 14), dpToPx(context, 18), dpToPx(context, 14))
            setBackgroundColor(colorSurface)
        }

        // 1. 自动检测更新模式开关
        val modeTitle = TextView(context).apply {
            text = "更新检测模式"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorOnSurface)
            setPadding(0, 0, 0, dpToPx(context, 8))
        }
        rootLayout.addView(modeTitle)

        val modeGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 0, 0, dpToPx(context, 14))
        }

        val rbManual = RadioButton(context).apply {
            id = View.generateViewId()
            text = "仅手动检测（推荐，仅在主动点击时检查）"
            textSize = 13f
            setTextColor(colorOnSurface)
        }
        val rbAuto = RadioButton(context).apply {
            id = View.generateViewId()
            text = "启动时静默检测（打开应用时后台轻量检测）"
            textSize = 13f
            setTextColor(colorOnSurface)
        }

        modeGroup.addView(rbManual)
        modeGroup.addView(rbAuto)

        if (configManager.updateMode == UpdaterConfigManager.UPDATE_MODE_AUTO) {
            modeGroup.check(rbAuto.id)
        } else {
            modeGroup.check(rbManual.id)
        }

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == rbAuto.id) {
                configManager.updateMode = UpdaterConfigManager.UPDATE_MODE_AUTO
                Toast.makeText(context, "已开启启动时静默检测", Toast.LENGTH_SHORT).show()
            } else {
                configManager.updateMode = UpdaterConfigManager.UPDATE_MODE_MANUAL
                Toast.makeText(context, "已切换为仅手动检测", Toast.LENGTH_SHORT).show()
            }
        }
        rootLayout.addView(modeGroup)

        // 分割线
        val divider = View(context).apply {
            setBackgroundColor(colorOutline)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 1)).apply {
                bottomMargin = dpToPx(context, 12)
            }
            layoutParams = lp
        }
        rootLayout.addView(divider)

        // 2. 更新源选择列表
        val sourcesTitle = TextView(context).apply {
            text = "活跃更新源选择"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorOnSurface)
            setPadding(0, 0, 0, dpToPx(context, 8))
        }
        rootLayout.addView(sourcesTitle)

        val sourcesScroll = ScrollView(context).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 180))
            layoutParams = lp
        }
        val sourcesListLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        sourcesScroll.addView(sourcesListLayout)
        rootLayout.addView(sourcesScroll)

        var dialog: AlertDialog? = null

        fun refreshSourcesUI() {
            sourcesListLayout.removeAllViews()
            val sources = configManager.getSources()
            val selectedId = configManager.selectedSourceId

            for (source in sources) {
                val isSelected = (source.id == selectedId)
                val cardItem = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(context, 10).toFloat()
                        setStroke(dpToPx(context, 1), if (isSelected) colorPrimary else colorOutline)
                        setColor(if (isSelected) palette.primaryContainer else colorSurfaceVariant)
                    }
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

                val rb = RadioButton(context).apply {
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
                    val btnDelete = TextView(context).apply {
                        text = "删除"
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(palette.error)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            cornerRadius = dpToPx(context, 8).toFloat()
                            setStroke(dpToPx(context, 1), palette.error)
                            setColor(Color.TRANSPARENT)
                        }
                        setPadding(dpToPx(context, 8), 0, dpToPx(context, 8), 0)
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(context, 30)).apply {
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
        val btnAddSource = TextView(context).apply {
            text = "+ 添加更新源"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(context, 10).toFloat()
                setStroke(dpToPx(context, 1), colorPrimary)
                setColor(Color.TRANSPARENT)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 42)).apply {
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

        dialog = AlertDialog.Builder(context)
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

        val rbCf = RadioButton(context).apply {
            id = View.generateViewId()
            text = "Cloudflare Pages R2"
            setTextColor(palette.onSurface)
        }
        val rbGh = RadioButton(context).apply {
            id = View.generateViewId()
            text = "GitHub Releases"
            setTextColor(palette.onSurface)
        }
        rgType.addView(rbCf)
        rgType.addView(rbGh)
        rgType.check(rbCf.id)
        formLayout.addView(rgType)

        AlertDialog.Builder(context)
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
