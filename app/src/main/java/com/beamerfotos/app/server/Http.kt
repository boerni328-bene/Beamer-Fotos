package com.beamerfotos.app.server

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder

data class Request(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
) {
    val contentLength: Long? get() = headers["content-length"]?.toLongOrNull()
}

class Response(val status: Int, val contentType: String, val body: ByteArray)

/** Das Nötigste von HTTP/1.1 für den kleinen Webserver (ohne Android, getestet in HttpTest). */
object Http {
    fun readLine(input: InputStream, max: Int = 8192): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > max) throw IOException("Zeile zu lang")
        }
        return buf.toString("UTF-8")
    }

    fun readRequest(input: InputStream): Request? {
        val line = readLine(input) ?: return null
        val parts = line.split(' ')
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        for (i in 0 until 100) {
            val h = readLine(input) ?: break
            if (h.isEmpty()) break
            val colon = h.indexOf(':')
            if (colon > 0) headers[h.substring(0, colon).trim().lowercase()] = h.substring(colon + 1).trim()
        }
        val target = parts[1]
        return Request(
            method = parts[0].uppercase(),
            path = target.substringBefore('?'),
            query = parseQuery(target.substringAfter('?', "")),
            headers = headers,
        )
    }

    fun parseQuery(query: String): Map<String, String> =
        query.split('&').filter { it.isNotEmpty() }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }

    fun writeResponse(out: OutputStream, r: Response) {
        val head = buildString {
            append("HTTP/1.1 ").append(r.status).append(' ').append(reason(r.status)).append("\r\n")
            append("Content-Type: ").append(r.contentType).append("\r\n")
            append("Content-Length: ").append(r.body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(r.body)
        out.flush()
    }

    fun json(status: Int, vararg fields: Pair<String, Any?>): Response {
        val body = fields.joinToString(",", "{", "}") { (k, v) -> quote(k) + ":" + value(v) }
        return Response(status, "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Boolean, is Int, is Long -> v.toString()
        is List<*> -> v.joinToString(",", "[", "]") { value(it) }
        else -> quote(v.toString())
    }

    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        411 -> "Length Required"
        413 -> "Payload Too Large"
        415 -> "Unsupported Media Type"
        507 -> "Insufficient Storage"
        else -> "Error"
    }
}
