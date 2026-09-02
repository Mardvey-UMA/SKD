package com.contentagg.aggregator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ContentAggregatorServiceApplication

fun main(args: Array<String>) {
    runApplication<ContentAggregatorServiceApplication>(*args)
}
