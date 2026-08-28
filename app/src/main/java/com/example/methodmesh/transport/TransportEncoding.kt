package com.example.methodmesh.transport

import java.net.URLEncoder

internal fun encodeTransportValue(value: Any?): String {
    return URLEncoder
        .encode(value?.toString() ?: "", "UTF-8")
        .replace("+", "%20")
}

internal fun androidExtraPrefix(value: Any?): String {
    return when (value) {
        is Boolean -> "B"
        is Float, is Double -> "f"
        is Int, is Long, is Short -> "i"
        else -> "S"
    }
}
