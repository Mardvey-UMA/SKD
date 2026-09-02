"""Middleware that propagates X-Request-Id and X-User-Id headers into context vars."""
from __future__ import annotations

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from src.infrastructure.logging import request_id_var, user_id_var


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Inject request_id and user_id from HTTP headers into logging context vars."""

    async def dispatch(self, request: Request, call_next) -> Response:
        rid = request.headers.get("X-Request-Id")
        uid = request.headers.get("X-User-Id")
        rtok = request_id_var.set(rid)
        utok = user_id_var.set(uid)
        try:
            return await call_next(request)
        finally:
            request_id_var.reset(rtok)
            user_id_var.reset(utok)
