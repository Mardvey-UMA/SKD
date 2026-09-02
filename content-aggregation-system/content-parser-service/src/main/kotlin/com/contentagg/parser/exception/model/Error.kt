package com.contentagg.parser.exception.model

data class Error(
    val code: String,
    val message: String,
    val cause: String? = null,
) {
    // Secondary constructor for Java interop — Kotlin default params are not visible from Java
    constructor(code: String, message: String) : this(code, message, null)
}
