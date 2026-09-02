package com.contentplatform.feed.application

import com.contentplatform.feed.api.exception.DuplicateSpaceNameException
import com.contentplatform.feed.api.exception.NotFoundException
import com.contentplatform.feed.api.exception.SpaceLimitReachedException
import com.contentplatform.feed.application.dto.CreateSpaceRequest
import com.contentplatform.feed.application.dto.SpaceListResponse
import com.contentplatform.feed.application.dto.SpaceResponse
import com.contentplatform.feed.application.dto.UpdateSpaceRequest
import com.contentplatform.feed.db.repository.SpaceRepository
import com.contentplatform.feed.db.repository.SpaceSourceRepository
import com.contentplatform.feed.db.repository.model.SpaceEntity
import com.contentplatform.feed.domain.SpaceColor
import com.contentplatform.feed.infrastructure.cache.FeedCacheService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SpaceService(
    private val spaceRepository: SpaceRepository,
    private val spaceSourceRepository: SpaceSourceRepository,
    private val feedCacheService: FeedCacheService,
    @Value("\${feed.spaces.max-per-user:10}") private val maxSpacesPerUser: Int = 10
) {

    companion object {
        private val log = LoggerFactory.getLogger(SpaceService::class.java)
        private const val MAX_NAME_LENGTH = 100
    }

    fun list(userId: UUID): SpaceListResponse {
        val spaces = spaceRepository.findAllByUserId(userId)
        val items = spaces.map { buildResponse(it) }
        return SpaceListResponse(items = items, count = items.size, limit = maxSpacesPerUser)
    }

    @Transactional
    fun create(userId: UUID, request: CreateSpaceRequest): SpaceResponse {
        val name = validateName(request.name)
        val color = parseColor(request.color)
        val sourceIds = (request.sourceIds ?: emptyList()).distinct()

        val existing = spaceRepository.countByUserIdForUpdate(userId)
        if (existing >= maxSpacesPerUser) {
            throw SpaceLimitReachedException()
        }
        if (spaceRepository.existsByUserIdAndName(userId, name)) {
            throw DuplicateSpaceNameException()
        }

        val entity = try {
            spaceRepository.insert(userId, name, color)
        } catch (e: DuplicateKeyException) {
            throw DuplicateSpaceNameException()
        }
        spaceSourceRepository.insertAll(entity.id, sourceIds)
        log.info("Created space {} for user {} with {} sources", entity.id, userId, sourceIds.size)
        return buildResponse(entity, sourceIds)
    }

    fun getById(userId: UUID, spaceId: UUID): SpaceResponse {
        val entity = spaceRepository.findByIdAndUserId(spaceId, userId)
            ?: throw NotFoundException("Space not found: $spaceId")
        return buildResponse(entity)
    }

    @Transactional
    fun update(userId: UUID, spaceId: UUID, request: UpdateSpaceRequest): SpaceResponse {
        if (request.name == null && request.color == null && request.sourceIds == null) {
            throw IllegalArgumentException("At least one field must be provided")
        }

        val existing = spaceRepository.findByIdAndUserId(spaceId, userId)
            ?: throw NotFoundException("Space not found: $spaceId")

        val newName = request.name?.let { validateName(it) }
        val newColor = request.color?.let { parseColor(it) }

        if (newName != null && newName != existing.name) {
            if (spaceRepository.existsByUserIdAndNameExcludingId(userId, newName, spaceId)) {
                throw DuplicateSpaceNameException()
            }
        }

        if (newName != null || newColor != null) {
            try {
                spaceRepository.update(spaceId, newName, newColor)
            } catch (e: DuplicateKeyException) {
                throw DuplicateSpaceNameException()
            }
        } else {
            spaceRepository.touchUpdatedAt(spaceId)
        }

        var sourcesChanged = false
        val finalSourceIds = if (request.sourceIds != null) {
            val distinct = request.sourceIds.distinct()
            spaceSourceRepository.replaceAll(spaceId, distinct)
            sourcesChanged = true
            distinct
        } else {
            spaceSourceRepository.findSourceIds(spaceId)
        }

        if (sourcesChanged) {
            feedCacheService.deleteFeed(cacheKey(spaceId, userId))
        }

        val updated = spaceRepository.findByIdAndUserId(spaceId, userId)!!
        return buildResponse(updated, finalSourceIds)
    }

    @Transactional
    fun delete(userId: UUID, spaceId: UUID) {
        val deleted = spaceRepository.deleteByIdAndUserId(spaceId, userId)
        if (deleted == 0) {
            throw NotFoundException("Space not found: $spaceId")
        }
        feedCacheService.deleteFeed(cacheKey(spaceId, userId))
    }

    private fun validateName(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("name must not be blank")
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            throw IllegalArgumentException("name exceeds $MAX_NAME_LENGTH characters")
        }
        return trimmed
    }

    private fun parseColor(color: String?): SpaceColor {
        if (color.isNullOrBlank()) {
            throw IllegalArgumentException("color is required")
        }
        return try {
            SpaceColor.fromString(color)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(e.message ?: "invalid color")
        }
    }

    private fun buildResponse(entity: SpaceEntity, sourceIds: List<UUID>? = null): SpaceResponse {
        val ids = sourceIds ?: spaceSourceRepository.findSourceIds(entity.id)
        return SpaceResponse(
            id = entity.id,
            name = entity.name,
            color = entity.color.name,
            sourceIds = ids,
            sourceCount = ids.size,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun cacheKey(spaceId: UUID, userId: UUID): String =
        "feed:space:$spaceId:user:$userId"
}
