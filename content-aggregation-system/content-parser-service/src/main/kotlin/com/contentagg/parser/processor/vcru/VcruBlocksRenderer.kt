package com.contentagg.parser.processor.vcru

import com.contentagg.parser.integration.rest.vcru.model.VcruBlockDataDto
import com.contentagg.parser.integration.rest.vcru.model.VcruBlockDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Converts VC.RU article blocks[] array to HTML string.
 * Also extracts image UUIDs from media/image blocks for downstream S3 upload.
 *
 * Block types supported: text, header, media, image, quote, list, code, embed.
 * Cover blocks (cover=true) are skipped — handled separately by caller.
 * Unknown block types produce a warning log and are skipped silently.
 */
@Component
class VcruBlocksRenderer(
    @Value("\${parser.vcru.image-cdn-url:https://leonardo.osnova.io}") private val imageCdnUrl: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(VcruBlocksRenderer::class.java)
    }

    /**
     * Render blocks to HTML string.
     * Skips cover blocks (cover=true).
     * Unknown block types are logged as warnings and skipped.
     *
     * @param blocks List of article blocks
     * @return rendered HTML string, blocks joined with newlines
     */
    fun renderToHtml(blocks: List<VcruBlockDto>): String {
        if (blocks.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()
        for (block in blocks) {
            // Skip cover blocks — handled separately
            if (block.cover == true) {
                log.debug("Skipping cover block type={}", block.type)
                continue
            }

            val data = block.data
            if (data == null) {
                log.warn("Block has null data, type={}, skipping", block.type)
                continue
            }

            val html = when (block.type) {
                "text"   -> renderTextBlock(data)
                "header" -> renderHeaderBlock(data)
                "media"  -> renderMediaBlock(data)
                "image"  -> renderMediaBlock(data)
                "quote"  -> renderQuoteBlock(data)
                "list"   -> renderListBlock(data)
                "code"   -> renderCodeBlock(data)
                "embed"  -> renderEmbedBlock(data)
                else     -> {
                    log.warn("Unknown block type='{}', skipping", block.type)
                    null
                }
            }

            if (html != null) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(html)
            }
        }

        return sb.toString()
    }

    /**
     * Extract image UUIDs from media and image type blocks.
     * Traverses blocks[].data.items[].image.data.uuid.
     *
     * @param blocks List of article blocks
     * @return List of image UUIDs (non-null, non-blank)
     */
    fun extractImageUuids(blocks: List<VcruBlockDto>): List<String> {
        return blocks
            .filter { it.type == "media" || it.type == "image" }
            .filter { it.cover != true }
            .flatMap { block ->
                block.data?.getMediaItems().orEmpty()
                    .mapNotNull { item -> item.image?.data?.uuid }
                    .filter { uuid -> uuid.isNotBlank() }
            }
    }

    /**
     * Build CDN URL for an image UUID.
     * Format: {imageCdnUrl}/{uuid}/  — trailing slash is required by leonardo.osnova.io.
     */
    fun buildCdnUrl(uuid: String): String = "$imageCdnUrl/$uuid/"

    // -------------------------------------------------------------------------
    // Private rendering methods per block type
    // -------------------------------------------------------------------------

    /**
     * text block — text may already contain HTML, pass through unescaped.
     */
    private fun renderTextBlock(data: VcruBlockDataDto): String {
        val text = data.text ?: ""
        return "<p>$text</p>"
    }

    /**
     * header block — level defaults to 2 if null.
     */
    private fun renderHeaderBlock(data: VcruBlockDataDto): String {
        val level = data.level?.coerceIn(1, 6) ?: 2
        val text = data.text ?: ""
        return "<h$level>$text</h$level>"
    }

    /**
     * media / image block — one <img> per item in data.items.
     * CDN URLs are used initially; VcruImageUploadService replaces them with S3 URLs later.
     */
    private fun renderMediaBlock(data: VcruBlockDataDto): String {
        val mediaItems = data.getMediaItems()
        if (mediaItems.isEmpty()) return ""
        val sb = StringBuilder()
        for (item in mediaItems) {
            val uuid = item.image?.data?.uuid ?: continue
            val title = (item.title ?: "").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            val cdnUrl = buildCdnUrl(uuid)
            sb.append("<img src=\"$cdnUrl\" alt=\"$title\" />")
        }
        return sb.toString()
    }

    /**
     * quote block.
     */
    private fun renderQuoteBlock(data: VcruBlockDataDto): String {
        val text = data.text ?: ""
        return "<blockquote>$text</blockquote>"
    }

    /**
     * list block — data.items contain text items (not media items).
     * Renders as <ul> with <li> for each item's title or text.
     */
    private fun renderListBlock(data: VcruBlockDataDto): String {
        val stringItems = data.getStringItems()
        if (stringItems.isEmpty()) return ""
        val sb = StringBuilder("<ul>")
        for (text in stringItems) {
            sb.append("<li>$text</li>")
        }
        sb.append("</ul>")
        return sb.toString()
    }

    /**
     * code block.
     */
    private fun renderCodeBlock(data: VcruBlockDataDto): String {
        val text = data.text ?: ""
        return "<pre><code>$text</code></pre>"
    }

    /**
     * embed block — placeholder comment with embed type.
     */
    private fun renderEmbedBlock(data: VcruBlockDataDto): String {
        val type = data.type ?: "unknown"
        return "<!-- embed: $type -->"
    }
}
