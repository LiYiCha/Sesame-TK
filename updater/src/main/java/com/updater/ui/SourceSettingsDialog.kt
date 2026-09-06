package com.updater.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.updater.config.UpdaterConfigManager
import com.updater.model.UpdateSource
import com.updater.model.UpdateSourceType
import java.util.*

object SourceSettingsDialog {

    fun show(context: Context, onSourceChanged: (() -> Unit)? = null) {
        val configManager = UpdaterConfigManager(context)
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val brandColor = if (isNight) Color.parseColor("#4CAF50") else Color.parseColor("#2D5A27")
        val colorTextPrimary = if (isNight) Color.parseColor("#FFFFFF") else Color.parseColor("#212529")
        val colorTextSecondary = if (isNight) Color.parseColor("#AAAAAA") else Color.parseColor("#6C757D")
        val colorBorder = if (isNight) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
        val colorItemBg = if (isNight) Color.parseColor("#262626") else Color.parseColor("#F8F9FA")

        var dialog: AlertDialog? = null

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 20), dpToPx(context, 16), dpToPx(context, 20), dpToPx(context, 16))
        }

        // 1. 更新检测方式设置项（支持手动更新与自动更新两种模式，默认手动更新）
        val txtModeSectionTitle = TextView(context).apply {
            text = "更新检测方式"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            setPadding(0, 0, 0, dpToPx(context, 8))
        }
        rootLayout.addView(txtModeSectionTitle)

        val radioGroupMode = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 0, 0, dpToPx(context, 10))
        }

        val rbManual = RadioButton(context).apply {
            id = View.generateViewId()
            text = "手动更新 (默认)\n仅在点击检查更新时联网，日常无后台检测"
            textSize = 13f
            setTextColor(colorTextPrimary)
            setLineSpacing(dpToPx(context, 2).toFloat(), 1.0f)
            setPadding(dpToPx(context, 6), dpToPx(context, 4), 0, dpToPx(context, 8))
        }

        val rbAuto = RadioButton(context).apply {
            id = View.generateViewId()
            text = "自动更新\n应用每次启动时后台静默检测，有新版本主动弹窗"
            textSize = 13f
            setTextColor(colorTextPrimary)
            setLineSpacing(dpToPx(context, 2).toFloat(), 1.0f)
            setPadding(dpToPx(context, 6), dpToPx(context, 4), 0, dpToPx(context, 8))
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
                Toast.makeText(context, "已设为：手动更新 (仅点击时检查)", Toast.LENGTH_SHORT).show()
            } else if (checkedId == rbAuto.id) {
                configManager.updateMode = UpdaterConfigManager.UPDATE_MODE_AUTO
                Toast.makeText(context, "已设为：自动更新 (启动时静默检测)", Toast.LENGTH_SHORT).show()
            }
        }
        rootLayout.addView(radioGroupMode)

        // 分割线
        val divider = View(context).apply {
            setBackgroundColor(colorBorder)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 1)).apply {
                bottomMargin = dpToPx(context, 14)
            }
        }
        rootLayout.addView(divider)

        // 2. 更新源选择列表容器
        val txtSourceTitle = TextView(context).apply {
            text = "选择当前生效的更新源 (单选)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            setPadding(0, 0, 0, dpToPx(context, 8))
        }
        rootLayout.addView(txtSourceTitle)

        val scrollSources = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 180))
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
                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(context, 4), dpToPx(context, 6), dpToPx(context, 4), dpToPx(context, 6))
                    background = createItemSelector(colorItemBg)
                }

                val rb = RadioButton(context).apply {
                    isChecked = (source.id == selectedId)
                    setOnClickListener {
                        configManager.selectedSourceId = source.id
                        refreshSourcesUI()
                        onSourceChanged?.invoke()
                    }
                }

                val infoLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                        leftMargin = dpToPx(context, 8)
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
                    setTextColor(colorTextPrimary)
                }
                titleLayout.addView(txtName)

                val txtTag = TextView(context).apply {
                    text = if (source.type == UpdateSourceType.CLOUDFLARE_R2) " CF R2 " else " GitHub "
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    background = createBadgeBackground(if (source.type == UpdateSourceType.CLOUDFLARE_R2) "#F6821F" else "#24292E")
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
                    setTextColor(colorTextSecondary)
                    maxLines = 1
                }
                infoLayout.addView(txtUrl)
                itemLayout.addView(rb)
                itemLayout.addView(infoLayout)

                // 如果不是预设内置源，显示删除按钮
                if (!source.isPreset) {
                    val btnDelete = Button(context).apply {
                        text = "删除"
                        textSize = 12f
                        setTextColor(Color.parseColor("#DC3545"))
                        val delBg = GradientDrawable().apply {
                            setColor(if (isNight) Color.parseColor("#2A1C1C") else Color.parseColor("#FFF0F0"))
                            cornerRadius = dpToPx(context, 6).toFloat()
                        }
                        background = delBg
                        val lp = LinearLayout.LayoutParams(dpToPx(context, 56), dpToPx(context, 32)).apply {
                            leftMargin = dpToPx(context, 8)
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

                sourcesListLayout.addView(itemLayout)
            }
        }

        refreshSourcesUI()

        // 3. 添加自定义更新源按钮（使用项目主题调色板）
        val btnAddSource = Button(context).apply {
            text = "+ 添加自定义更新源"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(brandColor)
            val btnBg = GradientDrawable().apply {
                setColor(if (isNight) Color.parseColor("#1B3320") else Color.parseColor("#E8F5E9"))
                setStroke(dpToPx(context, 1), brandColor)
                cornerRadius = dpToPx(context, 8).toFloat()
            }
            background = btnBg
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 42)).apply {
                topMargin = dpToPx(context, 12)
            }
            layoutParams = lp
            setOnClickListener {
                showAddSourceDialog(context, configManager, brandColor, colorTextPrimary) {
                    refreshSourcesUI()
                    onSourceChanged?.invoke()
                }
            }
        }
        rootLayout.addView(btnAddSource)

        dialog = AlertDialog.Builder(context)
            .setTitle("更新模式与源设置")
            .setView(rootLayout)
            .setPositiveButton("完成") { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.show()

        // 按钮样式动态对齐项目品牌色
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.apply {
            setTextColor(brandColor)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
        }
    }

    private fun showAddSourceDialog(
        context: Context,
        configManager: UpdaterConfigManager,
        brandColor: Int,
        colorTextPrimary: Int,
        onAdded: () -> Unit
    ) {
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 20), dpToPx(context, 12), dpToPx(context, 20), dpToPx(context, 12))
        }

        val edtName = EditText(context).apply {
            hint = "更新源名称（如：我的备用镜像源）"
            textSize = 14f
            setTextColor(colorTextPrimary)
        }
        formLayout.addView(edtName)

        val edtUrl = EditText(context).apply {
            hint = "接口或仓库 URL (CF域名 或 GitHub仓库地址)"
            textSize = 14f
            setTextColor(colorTextPrimary)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(context, 8)
            }
            layoutParams = lp
        }
        formLayout.addView(edtUrl)

        val rgType = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(context, 8)
            }
            layoutParams = lp
        }

        val rbCf = RadioButton(context).apply {
            id = View.generateViewId()
            text = "Cloudflare Pages R2"
            setTextColor(colorTextPrimary)
        }
        val rbGh = RadioButton(context).apply {
            id = View.generateViewId()
            text = "GitHub Releases"
            setTextColor(colorTextPrimary)
        }
        rgType.addView(rbCf)
        rgType.addView(rbGh)
        rgType.check(rbCf.id)
        formLayout.addView(rgType)

        val addDialog = AlertDialog.Builder(context)
            .setTitle("添加自定义更新源")
            .setView(formLayout)
            .setPositiveButton("保存") { d, _ ->
                val name = edtName.text.toString().trim()
                val url = edtUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(context, "请填写完整的名称和地址", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "更新源已添加并生效", Toast.LENGTH_SHORT).show()
                onAdded()
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .create()

        addDialog.show()

        addDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.apply {
            setTextColor(brandColor)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
        }
    }

    private fun createBadgeBackground(colorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = 8f
        }
    }

    private fun createItemSelector(bgColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = 12f
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
