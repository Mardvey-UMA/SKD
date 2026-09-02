package com.contentagg.parser.sanitization

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist
import org.springframework.stereotype.Component

@Component
class HtmlSanitizer {

    private val safelist: Safelist = Safelist()
        .addTags(
            "p", "h2", "h3", "h4", "blockquote", "pre",
            "ul", "ol", "li", "hr",
            "strong", "em", "b", "i", "u", "s", "code", "br", "span",
            "a", "img",
            "table", "thead", "tbody", "tr", "td", "th"
        )
        .addAttributes("a", "href")
        .addAttributes("img", "src", "alt", "width", "height")
        .addProtocols("a", "href", "http", "https", "mailto")
        // img.src: allow http/https AND relative paths (starting with / for S3 Variant A paths like /content-media/...)
        // No addProtocols for img.src so that relative paths are not stripped by jsoup protocol check
        .addEnforcedAttribute("a", "rel", "nofollow noopener")
        .addEnforcedAttribute("a", "target", "_blank")
        .preserveRelativeLinks(true)

    fun sanitize(html: String?, sourceType: String): String {
        if (html.isNullOrBlank()) return ""
        var cleaned = Jsoup.clean(html, "", safelist, defaultOutputSettings())
        cleaned = when (sourceType.uppercase()) {
            "TELEGRAM" -> stripImagesFromTelegram(cleaned)
            "HABR" -> normalizeHabr(cleaned)
            "VCRU", "VCRU_USER", "VCRU_COMPANY" -> normalizeVcru(cleaned)
            else -> cleaned
        }
        return collapseEmpty(cleaned).trim()
    }

    private fun defaultOutputSettings() = Document.OutputSettings()
        .prettyPrint(false)

    private fun stripImagesFromTelegram(html: String): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("img").remove()
        doc.select("video").remove()
        return doc.body().html()
    }

    private fun normalizeHabr(html: String): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("p:has(a[href^='https://habr.com/ru/users/'])")
            .filter { it.text().length < 50 }
            .forEach { it.remove() }
        return doc.body().html()
    }

    private fun normalizeVcru(html: String): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("[data-tracking-id]").removeAttr("data-tracking-id")
        return doc.body().html()
    }

    private fun collapseEmpty(html: String): String {
        return html
            .replace(Regex("(?i)<p>(\\s|&nbsp;)*</p>"), "")
            .replace(Regex("(<br\\s*/?>\\s*){3,}"), "<br><br>")
    }

    fun toPlainText(cleanHtml: String): String {
        if (cleanHtml.isBlank()) return ""
        val doc = Jsoup.parseBodyFragment(cleanHtml)
        doc.outputSettings().prettyPrint(false)
        val text = doc.body().wholeText()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
    }

    fun buildPreview(plainText: String, maxChars: Int = 300): String {
        if (plainText.length <= maxChars) return plainText
        val cut = plainText.substring(0, maxChars)
        val lastDot = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
        return if (lastDot > maxChars / 2) cut.substring(0, lastDot + 1) else "$cut..."
    }
}
