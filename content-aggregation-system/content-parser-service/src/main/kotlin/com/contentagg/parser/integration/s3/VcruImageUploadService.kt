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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Service for processing VC.RU article images.
 * Features:
 * - Download images from leonardo.osnova.io CDN by UUID
 * - Try WebP format first, fallback to JPG
 * - Upload to S3/MinIO with vcru/{date}/{articleId}/{uuid}.{ext} key pattern
 * - Replace CDN URLs with S3 URLs in rendered HTML
 * - Parallel downloads using virtual threads
 */
@Service
class VcruImageUploadService(
    private val s3Client: S3Client,
    @Value("\${s3.bucket}") private val s3BucketName: String,
    @Value("\${s3.public-url}") private val s3PublicUrl: String,
    @Value("\${parser.vcru.image-cdn-url:https://leonardo.osnova.io}") private val imageCdnUrl: String,
    private val meterRegistry: MeterRegistry,
) {
    companion object {
        private val log = LoggerFactory.getLogger(VcruImageUploadService::class.java)

        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        private const val TOTAL_TIMEOUT_SECONDS = 60L
        private const val PER_IMAGE_TIMEOUT_SECONDS = 30L
    }

    data class ProcessedContent(
        val htmlContent: String,
        val urlMapping: Map<String, String>, // cdnUrl -> s3Url
    )

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val imageDownloadTimer: Timer = Timer.builder("vcru.image.download")
        .description("Time taken to download VC.RU images from CDN")
        .register(meterRegistry)
    private val imageUploadTimer: Timer = Timer.builder("vcru.image.upload")
        .description("Time taken to upload VC.RU images to S3")
        .register(meterRegistry)

    /**
     * Process images: download from CDN by UUID, upload to S3, replace CDN URLs in HTML.
     *
     * @param htmlContent HTML content with CDN image URLs rendered by VcruBlocksRenderer
     * @param imageUuids List of image UUIDs extracted by VcruBlocksRenderer
     * @param articleId Article ID for S3 path organization
     * @return ProcessedContent with S3 URLs substituted for CDN URLs
     */
    fun processImages(htmlContent: String, imageUuids: List<String>, articleId: String): ProcessedContent {
        if (htmlContent.isBlank() || imageUuids.isEmpty()) {
            return ProcessedContent(htmlContent, emptyMap())
        }

        log.debug("Processing {} images for vcru article: {}", imageUuids.size, articleId)

        val urlMapping = downloadAndUploadImages(imageUuids, articleId)
        val processedHtml = replaceImageUrls(htmlContent, urlMapping)

        log.info("Processed {} images for vcru article: {}", urlMapping.size, articleId)
        return ProcessedContent(processedHtml, urlMapping)
    }

    // ========== Private methods ==========

    private fun downloadAndUploadImages(uuids: List<String>, articleId: String): Map<String, String> {
        val urlMapping = ConcurrentHashMap<String, String>()

        try {
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = uuids.map { uuid ->
                    CompletableFuture.runAsync({
                        try {
                            val result = downloadAndUploadImage(uuid, articleId)
                            if (result != null) {
                                val (cdnUrl, s3Url) = result
                                urlMapping[cdnUrl] = s3Url
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to process image uuid={} for article={}: {}", uuid, articleId, e.message)
                        }
                    }, executor)
                }

                CompletableFuture.allOf(*futures.toTypedArray()).get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        } catch (e: TimeoutException) {
            log.error("Timeout while processing images for vcru article: {}", articleId)
        } catch (e: Exception) {
            log.error("Error processing images for vcru article {}: {}", articleId, e.message)
        }

        return urlMapping
    }

    /**
     * Download and upload a single image.
     * Tries WebP format first, fallback to JPG.
     * URL pattern: {imageCdnUrl}/{uuid}/-/format/{format}/
     *
     * @return Pair(cdnUrl, s3Url) or null if both formats fail
     */
    private fun downloadAndUploadImage(uuid: String, articleId: String): Pair<String, String>? {
        // Try WebP first, then JPG fallback
        val formatsToTry = listOf("webp" to "image/webp", "jpg" to "image/jpeg")

        for ((format, contentType) in formatsToTry) {
            val downloadUrl = "$imageCdnUrl/$uuid/-/format/$format/"
            log.debug("Trying to download vcru image uuid={} format={}", uuid, format)

            val downloadSample = Timer.start(meterRegistry)
            val imageBytes: ByteArray? = try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(PER_IMAGE_TIMEOUT_SECONDS))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

                if (response.statusCode() != 200) {
                    log.debug(
                        "CDN returned status={} for uuid={} format={}, trying next format",
                        response.statusCode(), uuid, format
                    )
                    null
                } else {
                    response.body().also { downloadSample.stop(imageDownloadTimer) }
                }
            } catch (e: Exception) {
                log.debug("Error downloading uuid={} format={}: {}", uuid, format, e.message)
                null
            }

            if (imageBytes == null) continue

            val s3Key = generateS3Key(uuid, articleId, format)
            val uploadSample = Timer.start(meterRegistry)

            return try {
                val putRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build()

                s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes))
                uploadSample.stop(imageUploadTimer)

                val s3Url = buildS3Url(s3Key)
                // CDN base URL (without format suffix) used as the key in HTML replacement
                val cdnBaseUrl = "$imageCdnUrl/$uuid/"
                log.debug("Uploaded vcru image uuid={} to S3 key={}", uuid, s3Key)
                Pair(cdnBaseUrl, s3Url)
            } catch (e: S3Exception) {
                log.error("Error uploading vcru image uuid={} to S3: {}", uuid, e.message)
                throw InternalException("Failed to upload vcru image uuid=$uuid to S3: ${e.message}", cause = e)
            }
        }

        log.warn("All formats failed for vcru image uuid={} article={}", uuid, articleId)
        return null
    }

    /**
     * Generate S3 key for VC.RU image.
     * Pattern: vcru/{date}/{articleId}/{uuid}.{ext}
     * Example: vcru/2026-03-29/123456/a29f5a04-1b95-5b5e-9f9b-585d40c4de96.webp
     */
    private fun generateS3Key(uuid: String, articleId: String, extension: String): String {
        val date = LocalDate.now().toString()
        return "vcru/$date/$articleId/$uuid.$extension"
    }

    private fun buildS3Url(key: String): String = "/$s3BucketName/$key"

    /**
     * Replace all CDN base URLs with S3 URLs in HTML.
     * CDN URL pattern in HTML: {imageCdnUrl}/{uuid}/
     */
    private fun replaceImageUrls(html: String, urlMapping: Map<String, String>): String {
        var result = html
        for ((cdnUrl, s3Url) in urlMapping) {
            result = result.replace(cdnUrl, s3Url)
        }
        return result
    }
}
