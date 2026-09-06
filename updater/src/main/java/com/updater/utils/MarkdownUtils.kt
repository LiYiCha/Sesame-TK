package com.updater.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import androidx.core.text.HtmlCompat

/**
 * 轻量级 Markdown 语法解析渲染工具类
 * 支持：
 * - 多级标题 (#, ##, ###)
 * - 粗体 (**text** / __text__)
 * - 斜体 (*text* / _text_)
 * - 删除线 (~~text~~)
 * - 行内代码 (`code`)
 * - 无序列表 (- / * / +)
 * - 有序列表 (1. 2.)
 * - 超链接 ([title](url))
 * - 引用块 (> quote)
 */
object MarkdownUtils {

    /**
     * 将 Markdown 文本渲染为 Spanned 富文本
     */
    fun renderMarkdown(context: Context, markdownText: String?): CharSequence {
        if (markdownText.isNullOrBlank()) {
            return ""
        }

        val html = markdownToHtml(markdownText)
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)

        // 增强 URLSpan，确保点击超链接时在系统浏览器打开
        val builder = SpannableStringBuilder(spanned)
        val urlSpans = builder.getSpans(0, builder.length, URLSpan::class.java)
        for (urlSpan in urlSpans) {
            val start = builder.getSpanStart(urlSpan)
            val end = builder.getSpanEnd(urlSpan)
            val flags = builder.getSpanFlags(urlSpan)
            val url = urlSpan.url

            builder.removeSpan(urlSpan)
            builder.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Throwable) {}
                }
            }, start, end, flags)
        }

        // 去除尾部多余换行
        var len = builder.length
        while (len > 0 && (builder[len - 1] == '\n' || builder[len - 1] == '\r')) {
            builder.delete(len - 1, len)
            len = builder.length
        }

        return builder
    }

    private fun markdownToHtml(md: String): String {
        val lines = md.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val htmlBuilder = StringBuilder()

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                htmlBuilder.append("<br/>")
                continue
            }

            var processedLine = escapeHtml(line)

            // 1. 标题识别
            val isHeading3 = processedLine.startsWith("### ")
            val isHeading2 = !isHeading3 && processedLine.startsWith("## ")
            val isHeading1 = !isHeading3 && !isHeading2 && processedLine.startsWith("# ")

            if (isHeading1) {
                val content = parseInline(processedLine.substring(2).trim())
                htmlBuilder.append("<p><big><b>").append(content).append("</b></big></p>")
                continue
            } else if (isHeading2) {
                val content = parseInline(processedLine.substring(3).trim())
                htmlBuilder.append("<p><b>").append(content).append("</b></p>")
                continue
            } else if (isHeading3) {
                val content = parseInline(processedLine.substring(4).trim())
                htmlBuilder.append("<p><b>").append(content).append("</b></p>")
                continue
            }

            // 2. 引用块
            if (processedLine.startsWith("&gt; ") || processedLine.startsWith("> ")) {
                val quoteContent = parseInline(processedLine.replaceFirst(Regex("^(&gt;|>)\\s*"), ""))
                htmlBuilder.append("<blockquote><i>").append(quoteContent).append("</i></blockquote>")
                continue
            }

            // 3. 无序列表 (- / * / +)
            val unorderedMatch = Regex("^[\\*\\-\\+]\\s+(.*)").find(processedLine)
            if (unorderedMatch != null) {
                val content = parseInline(unorderedMatch.groupValues[1])
                htmlBuilder.append("&bull;&nbsp;&nbsp;").append(content).append("<br/>")
                continue
            }

            // 4. 有序列表 (1. / 2.)
            val orderedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(processedLine)
            if (orderedMatch != null) {
                val num = orderedMatch.groupValues[1]
                val content = parseInline(orderedMatch.groupValues[2])
                htmlBuilder.append("<b>").append(num).append(".</b>&nbsp;").append(content).append("<br/>")
                continue
            }

            // 5. 普通文本行
            htmlBuilder.append(parseInline(processedLine)).append("<br/>")
        }

        return htmlBuilder.toString()
    }

    /**
     * 解析行内 Markdown 元素
     */
    private fun parseInline(text: String): String {
        var result = text

        // 超链接: [title](url)
        result = result.replace(Regex("\\[([^\\]]+)\\]\\((https?://[^\\s\\)]+)\\)")) { match ->
            val title = match.groupValues[1]
            val url = match.groupValues[2]
            "<a href=\"$url\">$title</a>"
        }

        // 行内代码: `code`
        result = result.replace(Regex("`([^`]+)`")) { match ->
            val code = match.groupValues[1]
            "<font color=\"#D81B60\"><code>$code</code></font>"
        }

        // 粗体: **text** 或 __text__
        result = result.replace(Regex("(\\*\\*|__)(.*?)\\1")) { match ->
            "<b>${match.groupValues[2]}</b>"
        }

        // 斜体: *text* 或 _text_
        result = result.replace(Regex("(\\*|_)(.*?)\\1")) { match ->
            "<i>${match.groupValues[2]}</i>"
        }

        // 删除线: ~~text~~
        result = result.replace(Regex("~~(.*?)~~")) { match ->
            "<strike>${match.groupValues[1]}</strike>"
        }

        return result
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
