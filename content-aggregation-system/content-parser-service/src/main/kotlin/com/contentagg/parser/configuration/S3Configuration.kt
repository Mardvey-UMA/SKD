package com.contentagg.parser.configuration

import com.contentagg.parser.configuration.properties.S3Properties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration as AwsS3Configuration
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import java.net.URI
import java.time.Duration

/**
 * Configuration for S3-compatible storage client (SeaweedFS, AWS S3, etc.).
 * Provides bean for S3 operations (image uploads).
 */
@Configuration
class S3Configuration(
    private val s3Properties: S3Properties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(S3Configuration::class.java)
    }

    @Bean
    fun s3Client(): S3Client {
        log.info(
            "Initializing S3 client for endpoint: {}, bucket: {}",
            s3Properties.endpoint,
            s3Properties.bucket,
        )

        // Enable path-style access for S3-compatible storage (SeaweedFS, MinIO, etc.).
        // Without this, AWS SDK uses virtual-hosted style (bucket.endpoint) which fails DNS resolution.
        val s3Config = AwsS3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .build()

        val client = S3Client.builder()
            .endpointOverride(URI.create(s3Properties.endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Properties.accessKey, s3Properties.secretKey),
                ),
            )
            .region(Region.of(s3Properties.region))
            .serviceConfiguration(s3Config)
            .httpClientBuilder(
                ApacheHttpClient.builder()
                    .maxConnections(50)
                    .connectionTimeout(Duration.ofSeconds(10))
                    .socketTimeout(Duration.ofSeconds(30)),
            )
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofSeconds(60))
                    .apiCallAttemptTimeout(Duration.ofSeconds(30))
                    .build(),
            )
            .build()

        // Verify bucket exists and is accessible
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(s3Properties.bucket).build())
            log.info("Successfully connected to S3 bucket: {}", s3Properties.bucket)
        } catch (e: Exception) {
            log.warn("S3 bucket check failed for {}: {}", s3Properties.bucket, e.message)
            log.warn("Continuing anyway - bucket will be created on first upload if needed")
        }

        return client
    }
}
