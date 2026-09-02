package com.contentplatform.gateway.routing

import com.contentplatform.gateway.config.GatewayProperties
import com.contentplatform.gateway.config.RouteDefinition
import org.springframework.stereotype.Component

/**
 * Resolves request path to a RouteDefinition using longest-prefix match.
 */
@Component
class RouteResolver(private val properties: GatewayProperties) {

    fun resolve(path: String): RouteDefinition? {
        return properties.routes
            .filter { path.startsWith(it.pathPrefix) }
            .maxByOrNull { it.pathPrefix.length }
    }

    fun isPublicRoute(path: String): Boolean {
        val route = resolve(path) ?: return false
        return route.public
    }
}
