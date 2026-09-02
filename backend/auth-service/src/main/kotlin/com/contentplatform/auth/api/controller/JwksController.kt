package com.contentplatform.auth.api.controller

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class JwksController(
    private val rsaKey: RSAKey
) {

    @GetMapping("/.well-known/jwks.json")
    fun jwks(): Map<String, Any> {
        val jwkSet = JWKSet(rsaKey.toPublicJWK())
        @Suppress("UNCHECKED_CAST")
        return jwkSet.toJSONObject() as Map<String, Any>
    }
}
