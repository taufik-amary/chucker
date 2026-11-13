package com.chuckerteam.chucker.internal.support

import com.chuckerteam.chucker.internal.data.entity.BODY_FRAMING_HEADERS
import com.chuckerteam.chucker.internal.data.entity.MockAction
import com.chuckerteam.chucker.internal.data.entity.parseMockHeaders
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

/** The response of [rule] to [request], built without reaching the server. */
internal fun mockResponse(
    request: Request,
    rule: MockAction.MapLocal,
): Response {
    val headers = rule.parsedHeaders()
    val now = System.currentTimeMillis()
    return Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(rule.statusCode)
        .message("")
        .headers(headers)
        .body(rule.responseBody(headers))
        .sentRequestAtMillis(now)
        .receivedResponseAtMillis(now)
        .build()
}

/**
 * [response] with the status and body of [rule], and its headers: the ones of the response that
 * the rule doesn't name are kept.
 */
internal fun overrideResponse(
    response: Response,
    rule: MockAction.MapLocal,
): Response {
    // The original body is replaced, so it won't be read by anyone.
    response.body.close()
    val ruleHeaders = rule.parsedHeaders()
    val headers =
        response.headers
            .newBuilder()
            .apply {
                (BODY_FRAMING_HEADERS + ruleHeaders.names()).forEach { removeAll(it) }
                addAll(ruleHeaders)
            }.build()
    return response
        .newBuilder()
        .code(rule.statusCode)
        .message(if (rule.statusCode == response.code) response.message else "")
        .headers(headers)
        .body(rule.responseBody(headers))
        .build()
}

/** The headers were checked when the rule was saved, but an unreadable rule must not break the call. */
private fun MockAction.MapLocal.parsedHeaders(): Headers {
    val headers = parseMockHeaders(headers) ?: Headers.headersOf()
    return headers
        .newBuilder()
        .apply { BODY_FRAMING_HEADERS.forEach { removeAll(it) } }
        .build()
}

private fun MockAction.MapLocal.responseBody(headers: Headers): ResponseBody {
    val contentType = headers["Content-Type"]?.toMediaTypeOrNull()
    return body.toResponseBody(contentType)
}
