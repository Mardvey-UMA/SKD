package com.contentagg.config.db.service.source

import com.contentagg.config.db.repository.source.SourceRepository
import com.contentagg.config.db.repository.source.model.dto.SourceRequest
import com.contentagg.config.db.repository.source.model.dto.SourceResponse
import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import com.contentagg.config.exception.SourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Database-level service for Source entity operations.
 * Handles transactional DB access, caching, and entity mapping.
 * Does NOT contain business logic or Kafka publishing.
 */
@Service
class SourceService(
    private val repository: SourceRepository,
    private val mapper: SourceMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceService::class.java)
    }

    @Transactional(readOnly = true)
    @Cacheable("sources")
    fun findAll(): List<SourceResponse> {
        log.debug("Fetching all sources")
        return repository.findAll().toList().map { mapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun findActive(): List<SourceResponse> {
        log.debug("Fetching active sources")
        return repository.findByIsActiveTrue().map { mapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    @Cacheable("sources")
    fun findByType(type: SourceType): List<SourceResponse> {
        log.debug("Fetching sources by type: {}", type)
        return repository.findBySourceType(type.name).map { mapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun findActiveByType(type: SourceType): List<SourceResponse> {
        log.debug("Fetching active sources by type: {}", type)
        return repository.findActiveBySourceType(type.name).map { mapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): SourceResponse {
        log.debug("Fetching source by id: {}", id)
        val source = repository.findByIdOrNull(id) ?: throw SourceNotFoundException(id.toString())
        return mapper.toResponse(source)
    }

    /**
     * Legacy create — throws on duplicate. Kept for existing callers/tests that expect the old
     * behaviour. New code paths (premium user add flow) should use [createOrGetExisting].
     */
    @Transactional
    @CacheEvict(value = ["sources"], allEntries = true)
    fun create(request: SourceRequest): SourceResponse {
        log.info("Creating source: {} of type {}", request.name, request.sourceType)

        if (repository.existsByTypeAndName(request.sourceType.name, request.name)) {
            throw InvalidSourceException(
                "Source with type '${request.sourceType}' and name '${request.name}' already exists"
            )
        }

        val source = mapper.toEntity(request)
        val saved = repository.save(source)
        log.info("Created source: {}", saved.id)
        return mapper.toResponse(saved)
    }

    /**
     * Race-safe create with "was_existing" semantics (Phase 2):
     *   - if source already exists under (type, name) → return it with wasExisting=true
     *   - else INSERT; on unique-violation re-query and return as wasExisting=true
     */
    @Transactional
    @CacheEvict(value = ["sources"], allEntries = true)
    fun createOrGetExisting(request: SourceRequest): CreationResult {
        log.info("create-or-get source: {} of type {}", request.name, request.sourceType)

        repository.findByTypeAndName(request.sourceType.name, request.name)?.let {
            log.info("Source already exists: {} — returning wasExisting=true", it.id)
            return CreationResult(mapper.toResponse(it), wasExisting = true)
        }

        val source = mapper.toEntity(request)
        return try {
            val saved = repository.save(source)
            log.info("Created source: {}", saved.id)
            CreationResult(mapper.toResponse(saved), wasExisting = false)
        } catch (ex: DataIntegrityViolationException) {
            log.warn("Unique-violation on INSERT for ({}, {}) — falling back to existing row", request.sourceType, request.name)
            val existing = repository.findByTypeAndName(request.sourceType.name, request.name)
                ?: throw ex
            CreationResult(mapper.toResponse(existing), wasExisting = true)
        }
    }

    @Transactional
    @CacheEvict(value = ["sources"], allEntries = true)
    fun update(id: UUID, request: SourceRequest): SourceResponse {
        log.info("Updating source: {}", id)

        val existing = repository.findByIdOrNull(id) ?: throw SourceNotFoundException(id.toString())

        repository.findByTypeAndName(request.sourceType.name, request.name)?.let { source ->
            if (source.id != id) {
                throw InvalidSourceException(
                    "Source with type '${request.sourceType}' and name '${request.name}' already exists"
                )
            }
        }

        val updated = mapper.withUpdated(existing, request)
        val saved = repository.save(updated)
        log.info("Updated source: {}", saved.id)
        return mapper.toResponse(saved)
    }

    @Transactional
    @CacheEvict(value = ["sources"], allEntries = true)
    fun delete(id: UUID): SourceResponse {
        log.info("Deleting source: {}", id)

        val source = repository.findByIdOrNull(id) ?: throw SourceNotFoundException(id.toString())

        val response = mapper.toResponse(source)
        repository.deleteById(source.id!!)
        log.info("Deleted source: {}", id)
        return response
    }

    @Transactional(readOnly = true)
    fun findNeedingUpdate(): List<SourceResponse> {
        log.debug("Fetching sources needing update")
        return repository.findSourcesNeedingUpdate().map { mapper.toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun existsByTypeAndName(sourceTypeName: String, name: String): Boolean =
        repository.existsByTypeAndName(sourceTypeName, name)

}
