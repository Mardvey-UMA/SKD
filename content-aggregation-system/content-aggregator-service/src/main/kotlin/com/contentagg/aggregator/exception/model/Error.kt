package com.contentagg.aggregator.exception.model

data class Error(
    val code: String,
    val message: String,
    val cause: String? = null
)
