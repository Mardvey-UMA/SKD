package com.contentagg.config.api.controller

import com.contentagg.config.api.SourceApi
import com.contentagg.config.api.model.habr.createHabrSource.CreateHabrSourceRequest
import com.contentagg.config.api.model.habr.createHabrSource.CreateHabrSourceResponse
import com.contentagg.config.api.model.habr.listHabrSources.ListHabrSourcesResponse
import com.contentagg.config.api.model.habr.updateHabrSource.UpdateHabrSourceRequest
import com.contentagg.config.api.model.habr.updateHabrSource.UpdateHabrSourceResponse
import com.contentagg.config.api.model.source.createSource.CreateSourceEnvelope
import com.contentagg.config.api.model.source.createSource.CreateSourceRequest
import com.contentagg.config.api.model.telegram.createTelegramSource.CreateTelegramSourceRequest
import com.contentagg.config.api.model.telegram.createTelegramSource.CreateTelegramSourceResponse
import com.contentagg.config.api.model.telegram.getTelegramSource.GetTelegramSourceResponse
import com.contentagg.config.api.model.telegram.listTelegramSources.ListTelegramSourcesResponse
import com.contentagg.config.api.model.telegram.updateTelegramSource.UpdateTelegramSourceRequest
import com.contentagg.config.api.model.telegram.updateTelegramSource.UpdateTelegramSourceResponse
import com.contentagg.config.api.model.vcru.createVcruSource.CreateVcruSourceRequest
import com.contentagg.config.api.model.vcru.createVcruSource.CreateVcruSourceResponse
import com.contentagg.config.api.model.vcru.getVcruSource.GetVcruSourceResponse
import com.contentagg.config.api.model.vcru.listVcruSources.ListVcruSourcesResponse
import com.contentagg.config.api.model.vcru.updateVcruSource.UpdateVcruSourceRequest
import com.contentagg.config.api.model.vcru.updateVcruSource.UpdateVcruSourceResponse
import com.contentagg.config.api.model.source.createSource.CreateSourceResponse
import com.contentagg.config.api.model.source.getSource.GetSourceResponse
import com.contentagg.config.api.model.source.listSources.ListSourcesResponse
import com.contentagg.config.api.model.source.updateSource.UpdateSourceRequest
import com.contentagg.config.api.model.source.updateSource.UpdateSourceResponse
import com.contentagg.config.enums.SourceType
import com.contentagg.config.processor.habr.CreateHabrSourceProcessor
import com.contentagg.config.processor.habr.DeleteHabrSourceProcessor
import com.contentagg.config.processor.habr.GetHabrSourceProcessor
import com.contentagg.config.processor.habr.UpdateHabrSourceProcessor
import com.contentagg.config.processor.source.CreateSourceProcessor
import com.contentagg.config.processor.telegram.CreateTelegramSourceProcessor
import com.contentagg.config.processor.telegram.DeleteTelegramSourceProcessor
import com.contentagg.config.processor.telegram.GetTelegramSourceProcessor
import com.contentagg.config.processor.telegram.UpdateTelegramSourceProcessor
import com.contentagg.config.processor.vcru.CreateVcruSourceProcessor
import com.contentagg.config.processor.vcru.DeleteVcruSourceProcessor
import com.contentagg.config.processor.vcru.GetVcruSourceProcessor
import com.contentagg.config.processor.vcru.UpdateVcruSourceProcessor
import com.contentagg.config.processor.source.DeleteSourceProcessor
import com.contentagg.config.processor.source.GetSourceProcessor
import com.contentagg.config.processor.source.UpdateSourceProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for managing content source configurations.
 * Implements SourceApi and delegates to processors.
 */
@RestController
class SourceController(
    private val createSourceProcessor: CreateSourceProcessor,
    private val updateSourceProcessor: UpdateSourceProcessor,
    private val getSourceProcessor: GetSourceProcessor,
    private val deleteSourceProcessor: DeleteSourceProcessor,
    private val createHabrSourceProcessor: CreateHabrSourceProcessor,
    private val updateHabrSourceProcessor: UpdateHabrSourceProcessor,
    private val deleteHabrSourceProcessor: DeleteHabrSourceProcessor,
    private val getHabrSourceProcessor: GetHabrSourceProcessor,
    private val createVcruSourceProcessor: CreateVcruSourceProcessor,
    private val updateVcruSourceProcessor: UpdateVcruSourceProcessor,
    private val deleteVcruSourceProcessor: DeleteVcruSourceProcessor,
    private val getVcruSourceProcessor: GetVcruSourceProcessor,
    private val createTelegramSourceProcessor: CreateTelegramSourceProcessor,
    private val updateTelegramSourceProcessor: UpdateTelegramSourceProcessor,
    private val deleteTelegramSourceProcessor: DeleteTelegramSourceProcessor,
    private val getTelegramSourceProcessor: GetTelegramSourceProcessor
) : SourceApi {

    companion object {
        private val log = LoggerFactory.getLogger(SourceController::class.java)
    }

    override fun getAllSources(activeOnly: Boolean): ResponseEntity<List<ListSourcesResponse>> {
        log.debug("GET /api/config/v1/sources?activeOnly={}", activeOnly)
        return ResponseEntity.ok(getSourceProcessor.getAll(activeOnly))
    }

    override fun getSourceById(id: UUID): ResponseEntity<GetSourceResponse> {
        log.debug("GET /api/config/v1/sources/{}", id)
        return ResponseEntity.ok(getSourceProcessor.getById(id))
    }

    override fun getSourcesByType(type: SourceType, activeOnly: Boolean): ResponseEntity<List<ListSourcesResponse>> {
        log.debug("GET /api/config/v1/sources/by-type/{}?activeOnly={}", type, activeOnly)
        return ResponseEntity.ok(getSourceProcessor.getByType(type, activeOnly))
    }

    override fun createSource(
        request: CreateSourceRequest,
        userId: String?,
        requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateSourceResponse>> {
        log.info("POST /api/config/v1/sources - Creating source: {} of type {}", request.name, request.sourceType)
        val envelope = createSourceProcessor.process(request, userId, requestId)
        val status = if (envelope.wasExisting) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(envelope)
    }

    override fun updateSource(id: UUID, request: UpdateSourceRequest): ResponseEntity<UpdateSourceResponse> {
        log.info("PUT /api/config/v1/sources/{}", id)
        return ResponseEntity.ok(updateSourceProcessor.process(id, request))
    }

    override fun deleteSource(id: UUID): ResponseEntity<Void> {
        log.info("DELETE /api/config/v1/sources/{}", id)
        deleteSourceProcessor.process(id)
        return ResponseEntity.noContent().build()
    }

    override fun getSourcesNeedingUpdate(): ResponseEntity<List<ListSourcesResponse>> {
        log.debug("GET /api/config/v1/sources/needs-update")
        return ResponseEntity.ok(getSourceProcessor.getNeedingUpdate())
    }

    override fun createHabrSource(
        request: CreateHabrSourceRequest,
        userId: String?,
        requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateHabrSourceResponse>> {
        log.info("POST /api/config/v1/sources/habr - Creating Habr source: {} of type {}", request.name, request.sourceType)
        val envelope = createHabrSourceProcessor.process(request, userId, requestId)
        val status = if (envelope.wasExisting) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(envelope)
    }

    override fun updateHabrSource(id: UUID, request: UpdateHabrSourceRequest): ResponseEntity<UpdateHabrSourceResponse> {
        log.info("PUT /api/config/v1/sources/habr/{}", id)
        return ResponseEntity.ok(updateHabrSourceProcessor.process(id, request))
    }

    override fun deleteHabrSource(id: UUID): ResponseEntity<Void> {
        log.info("DELETE /api/config/v1/sources/habr/{}", id)
        deleteHabrSourceProcessor.process(id)
        return ResponseEntity.noContent().build()
    }

    override fun listHabrSources(): ResponseEntity<List<ListHabrSourcesResponse>> {
        log.debug("GET /api/config/v1/sources/habr")
        return ResponseEntity.ok(getHabrSourceProcessor.listAll())
    }

    override fun getSupportedHabrTypes(): ResponseEntity<List<SourceType>> {
        log.debug("GET /api/config/v1/sources/habr/types")
        return ResponseEntity.ok(getHabrSourceProcessor.getSupportedTypes())
    }

    override fun createVcruSource(
        request: CreateVcruSourceRequest,
        userId: String?,
        requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateVcruSourceResponse>> {
        log.info("POST /api/config/v1/sources/vcru - Creating VC.RU source: {} of type {}", request.name, request.sourceType)
        val envelope = createVcruSourceProcessor.process(request, userId, requestId)
        val status = if (envelope.wasExisting) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(envelope)
    }

    override fun updateVcruSource(id: UUID, request: UpdateVcruSourceRequest): ResponseEntity<UpdateVcruSourceResponse> {
        log.info("PUT /api/config/v1/sources/vcru/{}", id)
        return ResponseEntity.ok(updateVcruSourceProcessor.process(id, request))
    }

    override fun deleteVcruSource(id: UUID): ResponseEntity<Void> {
        log.info("DELETE /api/config/v1/sources/vcru/{}", id)
        deleteVcruSourceProcessor.process(id)
        return ResponseEntity.noContent().build()
    }

    override fun getVcruSourceById(id: UUID): ResponseEntity<GetVcruSourceResponse> {
        log.debug("GET /api/config/v1/sources/vcru/{}", id)
        return ResponseEntity.ok(getVcruSourceProcessor.getById(id))
    }

    override fun listVcruSources(): ResponseEntity<List<ListVcruSourcesResponse>> {
        log.debug("GET /api/config/v1/sources/vcru")
        return ResponseEntity.ok(getVcruSourceProcessor.listAll())
    }

    override fun getSupportedVcruTypes(): ResponseEntity<List<SourceType>> {
        log.debug("GET /api/config/v1/sources/vcru/types")
        return ResponseEntity.ok(getVcruSourceProcessor.getSupportedTypes())
    }

    override fun createTelegramSource(
        request: CreateTelegramSourceRequest,
        userId: String?,
        requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateTelegramSourceResponse>> {
        log.info("POST /api/config/v1/sources/telegram - Creating Telegram source: {} of type {}", request.name, request.sourceType)
        val envelope = createTelegramSourceProcessor.process(request, userId, requestId)
        val status = if (envelope.wasExisting) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(envelope)
    }

    override fun updateTelegramSource(id: UUID, request: UpdateTelegramSourceRequest): ResponseEntity<UpdateTelegramSourceResponse> {
        log.info("PUT /api/config/v1/sources/telegram/{}", id)
        return ResponseEntity.ok(updateTelegramSourceProcessor.process(id, request))
    }

    override fun deleteTelegramSource(id: UUID): ResponseEntity<Void> {
        log.info("DELETE /api/config/v1/sources/telegram/{}", id)
        deleteTelegramSourceProcessor.process(id)
        return ResponseEntity.noContent().build()
    }

    override fun getTelegramSourceById(id: UUID): ResponseEntity<GetTelegramSourceResponse> {
        log.debug("GET /api/config/v1/sources/telegram/{}", id)
        return ResponseEntity.ok(getTelegramSourceProcessor.getById(id))
    }

    override fun listTelegramSources(): ResponseEntity<List<ListTelegramSourcesResponse>> {
        log.debug("GET /api/config/v1/sources/telegram")
        return ResponseEntity.ok(getTelegramSourceProcessor.listAll())
    }

    override fun getSupportedTelegramTypes(): ResponseEntity<List<SourceType>> {
        log.debug("GET /api/config/v1/sources/telegram/types")
        return ResponseEntity.ok(getTelegramSourceProcessor.getSupportedTypes())
    }
}
