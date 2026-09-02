package com.contentagg.config.api

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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

/**
 * API interface for source configuration management.
 */
@Tag(name = "Sources", description = "Source configuration management API")
@RequestMapping("/api/config/v1/sources")
interface SourceApi {

    @Operation(summary = "List all sources")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    fun getAllSources(
        @Parameter(description = "Return only active sources")
        @RequestParam(required = false, defaultValue = "false") activeOnly: Boolean
    ): ResponseEntity<List<ListSourcesResponse>>

    @Operation(summary = "Get source by ID")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{id}")
    fun getSourceById(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<GetSourceResponse>

    @Operation(summary = "Get sources by type")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/by-type/{type}")
    fun getSourcesByType(
        @Parameter(description = "Source type", required = true)
        @PathVariable type: SourceType,
        @Parameter(description = "Return only active sources")
        @RequestParam(required = false, defaultValue = "false") activeOnly: Boolean
    ): ResponseEntity<List<ListSourcesResponse>>

    @Operation(summary = "Create new source (returns {source, was_existing})")
    @PostMapping
    fun createSource(
        @Valid @RequestBody request: CreateSourceRequest,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateSourceResponse>>

    @Operation(summary = "Update source")
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/{id}")
    fun updateSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateSourceRequest
    ): ResponseEntity<UpdateSourceResponse>

    @Operation(summary = "Delete source")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    fun deleteSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<Void>

    @Operation(summary = "Get sources needing update")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/needs-update")
    fun getSourcesNeedingUpdate(): ResponseEntity<List<ListSourcesResponse>>

    @Operation(summary = "Create Habr source (returns {source, was_existing})")
    @PostMapping("/habr")
    fun createHabrSource(
        @Valid @RequestBody request: CreateHabrSourceRequest,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateHabrSourceResponse>>

    @Operation(summary = "Update Habr source")
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/habr/{id}")
    fun updateHabrSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateHabrSourceRequest
    ): ResponseEntity<UpdateHabrSourceResponse>

    @Operation(summary = "Delete Habr source")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/habr/{id}")
    fun deleteHabrSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<Void>

    @Operation(summary = "List Habr sources")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/habr")
    fun listHabrSources(): ResponseEntity<List<ListHabrSourcesResponse>>

    @Operation(summary = "Get supported Habr source types")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/habr/types")
    fun getSupportedHabrTypes(): ResponseEntity<List<SourceType>>

    @Operation(summary = "Create VC.RU source (returns {source, was_existing})")
    @PostMapping("/vcru")
    fun createVcruSource(
        @Valid @RequestBody request: CreateVcruSourceRequest,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateVcruSourceResponse>>

    @Operation(summary = "Update VC.RU source")
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/vcru/{id}")
    fun updateVcruSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateVcruSourceRequest
    ): ResponseEntity<UpdateVcruSourceResponse>

    @Operation(summary = "Delete VC.RU source")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/vcru/{id}")
    fun deleteVcruSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<Void>

    @Operation(summary = "Get VC.RU source by ID")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/vcru/{id}")
    fun getVcruSourceById(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<GetVcruSourceResponse>

    @Operation(summary = "List VC.RU sources")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/vcru")
    fun listVcruSources(): ResponseEntity<List<ListVcruSourcesResponse>>

    @Operation(summary = "Get supported VC.RU source types")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/vcru/types")
    fun getSupportedVcruTypes(): ResponseEntity<List<SourceType>>

    @Operation(summary = "Create Telegram source (returns {source, was_existing})")
    @PostMapping("/telegram")
    fun createTelegramSource(
        @Valid @RequestBody request: CreateTelegramSourceRequest,
        @RequestHeader(value = "X-User-Id", required = false) userId: String?,
        @RequestHeader(value = "X-Request-Id", required = false) requestId: String?,
    ): ResponseEntity<CreateSourceEnvelope<CreateTelegramSourceResponse>>

    @Operation(summary = "Update Telegram source")
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/telegram/{id}")
    fun updateTelegramSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTelegramSourceRequest
    ): ResponseEntity<UpdateTelegramSourceResponse>

    @Operation(summary = "Delete Telegram source")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/telegram/{id}")
    fun deleteTelegramSource(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<Void>

    @Operation(summary = "Get Telegram source by ID")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/telegram/{id}")
    fun getTelegramSourceById(
        @Parameter(description = "Source UUID", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<GetTelegramSourceResponse>

    @Operation(summary = "List Telegram sources")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/telegram")
    fun listTelegramSources(): ResponseEntity<List<ListTelegramSourcesResponse>>

    @Operation(summary = "Get supported Telegram source types")
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/telegram/types")
    fun getSupportedTelegramTypes(): ResponseEntity<List<SourceType>>
}
