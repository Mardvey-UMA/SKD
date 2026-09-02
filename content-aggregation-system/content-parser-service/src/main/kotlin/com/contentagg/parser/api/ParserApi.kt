package com.contentagg.parser.api

import com.contentagg.parser.api.model.parser.getStatus.GetStatusResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/api/v1/parser")
interface ParserApi {

    @PostMapping("/parse-all")
    fun parseAllSources(): ResponseEntity<Map<String, String>>

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<GetStatusResponse>

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>>
}
