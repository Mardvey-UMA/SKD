package com.contentagg.parser.integration.s3

import com.contentagg.parser.exception.InternalException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

/**
 * Service for processing Habr article images.
 * Features:
 * - Extract image URLs from HTML content
 * - Download images in parallel using virtual threads
 * - Upload to S3/MinIO
 * - Replace original URLs with S3 URLs in HTML
 */
@Service
class HabrImageUploadService(
    private val s3Client: S3Client,
    @Value("\${s3.bucket}") private val s3BucketName: String,
    @Value("\${s3.public-url}") private val s3PublicUrl: String,
    private val meterRegistry: MeterRegistry,
) {
    companion object {
        private val log = LoggerFactory.getLogger(HabrImageUploadService::class.java)

        private val IMG_TAG_PATTERN: Pattern =
            Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE)
        private val SRCSET_PATTERN: Pattern =
            Pattern.compile("srcset\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
        private val URL_PATTERN: Pattern =
            Pattern.compile("(https?://[^\\s'\"]+)")
    }

    data class ProcessedContent(
        val htmlContent: String,
        val urlMapping: Map<String, String>,
    )

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val imageDownloadTimer: Timer = Timer.builder("habr.image.download")
        .description("Time taken to download images")
        .register(meterRegistry)
    private val imageUploadTimer: Timer = Timer.builder("habr.image.upload")
        .description("Time taken to upload images")
        .register(meterRegistry)

    /**
     * Process all images in HTML content
     *
     * @param htmlContent Original HTML content
     * @param articleId Article ID for path organization
     * @return ProcessedContent with updated HTML and image mappings
     */
    fun processImages(htmlContent: String?, articleId: String): ProcessedContent {
        if (htmlContent.isNullOrBlank()) {
            return ProcessedContent(htmlContent ?: "", emptyMap())
        }

        log.debug("Processing images for article: {}", articleId)

        val imageUrls = extractImageUrls(htmlContent)
        log.debug("Found {} images in article: {}", imageUrls.size, articleId)

        if (imageUrls.isEmpty()) {
            return ProcessedContent(htmlContent, emptyMap())
        }

        val urlMapping = downloadAndUploadImages(imageUrls, articleId)
        val processedHtml = replaceImageUrls(htmlContent, urlMapping)

        log.info("Processed {} images for article: {}", urlMapping.size, articleId)
        return ProcessedContent(processedHtml, urlMapping)
    }

    /**
     * Extract lead image URL from article lead data
     */
    fun extractLeadImage(leadImageUrl: String?, articleId: String): String? {
        if (leadImageUrl.isNullOrBlank()) {
            return null
        }

        return try {
            val result = downloadAndUploadImages(setOf(leadImageUrl), articleId)
            result[leadImageUrl]
        } catch (e: Exception) {
            log.warn("Failed to process lead image for article {}: {}", articleId, e.message)
            null
        }
    }

    // ========== Private methods ==========

    private fun extractImageUrls(html: String): Set<String> {
        val urls = mutableSetOf<String>()

        val imgMatcher = IMG_TAG_PATTERN.matcher(html)
        while (imgMatcher.find()) {
            val url = imgMatcher.group(1)
            if (isValidImageUrl(url)) {
                urls.add(url)
            }
        }

        val srcsetMatcher = SRCSET_PATTERN.matcher(html)
        while (srcsetMatcher.find()) {
            val srcset = srcsetMatcher.group(1)
            val urlMatcher = URL_PATTERN.matcher(srcset)
            while (urlMatcher.find()) {
                val url = urlMatcher.group(1)
                if (isValidImageUrl(url)) {
                    urls.add(url)
                }
            }
        }

        return urls
    }

    private fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return false
        }
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun downloadAndUploadImages(urls: Set<String>, articleId: String): Map<String, String> {
        val urlMapping = ConcurrentHashMap<String, String>()

        try {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = urls.map { url ->
                    CompletableFuture.runAsync({
                        try {
                            val s3Url = downloadAndUploadImage(url, articleId)
                            if (s3Url != null) {
                                urlMapping[url] = s3Url
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to process image {}: {}", url, e.message)
                        }
                    }, executor)
                }

                CompletableFuture.allOf(*futures.toTypedArray()).get(60, TimeUnit.SECONDS)
            }
        } catch (e: TimeoutException) {
            log.error("Timeout while processing images for article: {}", articleId)
        } catch (e: Exception) {
            log.error("Error processing images for article {}: {}", articleId, e.message)
        }

        return urlMapping
    }

    private fun downloadAndUploadImage(imageUrl: String, articleId: String): String? {
        log.debug("Downloading image: {}", imageUrl)

        val downloadSample = Timer.start(meterRegistry)
        val imageBytes: ByteArray = try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

            if (response.statusCode() != 200) {
                log.warn("Failed to download image: {}, status: {}", imageUrl, response.statusCode())
                return null
            }

            response.body().also { downloadSample.stop(imageDownloadTimer) }
        } catch (e: Exception) {
            log.error("Error downloading image {}: {}", imageUrl, e.message)
            throw e
        }

        val s3Key = generateS3Key(imageUrl, articleId)

        val uploadSample = Timer.start(meterRegistry)
        return try {
            val putRequest = PutObjectRequest.builder()
                .bucket(s3BucketName)
                .key(s3Key)
                .contentType(guessContentType(imageUrl))
                .build()

            s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes))
            uploadSample.stop(imageUploadTimer)

            buildS3Url(s3Key).also { s3Url ->
                log.debug("Uploaded image to S3: {}", s3Url)
            }
        } catch (e: S3Exception) {
            log.error("Error uploading image to S3: {}", e.message)
            throw InternalException("Failed to upload image to S3: ${e.message}", cause = e)
        }
    }

    private fun generateS3Key(imageUrl: String, articleId: String): String {
        val date = LocalDate.now().toString()
        val extension = getFileExtension(imageUrl)
        val filename = UUID.randomUUID().toString()
        return "habr/$date/$articleId/$filename$extension"
    }

    private fun getFileExtension(url: String): String {
        val path = URI.create(url).path
        val lastDot = path.lastIndexOf('.')
        return if (lastDot > 0) path.substring(lastDot) else ".jpg"
    }

    private fun guessContentType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".png") -> "image/png"
            lower.contains(".gif") -> "image/gif"
            lower.contains(".webp") -> "image/webp"
            lower.contains(".svg") -> "image/svg+xml"
            else -> "image/jpeg"
        }
    }

    private fun buildS3Url(key: String): String {
        return "/$s3BucketName/$key"
    }

    private fun replaceImageUrls(html: String, urlMapping: Map<String, String>): String {
        var result = html
        for ((originalUrl, s3Url) in urlMapping) {
            val escapedUrl = Pattern.quote(originalUrl)
            result = result.replace(Regex(escapedUrl), s3Url)
        }
        return result
    }
}
