package com.contentagg.aggregator.exception

import java.util.UUID

class ContentNotFoundException : NotFoundException {

    constructor(id: UUID) : super(ErrorCode.CONTENT_NOT_FOUND, "Content not found: $id")

    constructor(message: String) : super(ErrorCode.CONTENT_NOT_FOUND, message)
}
