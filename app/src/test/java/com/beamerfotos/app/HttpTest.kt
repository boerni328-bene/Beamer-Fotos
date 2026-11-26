package com.beamerfotos.app

import com.beamerfotos.app.server.Http
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HttpTest {
    @Test
    fun anfrageMitAbfrageUndKoepfen() {
        val raw = "PUT /api/upload?t=ab12cd&album=Gr%C3%BC%C3%9Fe%20Anna&name=IMG_1.jpeg HTTP/1.1\r\n" +
            "Host: 192.168.1.5:8080\r\nContent-Length: 5\r\nContent-Type: image/jpeg\r\n\r\nHALLO"
        val input = ByteArrayInputStream(raw.toByteArray())
        val r = Http.readRequest(input)!!
        assertEquals("PUT", r.method)
        assertEquals("/api/upload", r.path)
        assertEquals("ab12cd", r.query["t"])
        assertEquals("Grüße Anna", r.query["album"])
        assertEquals("IMG_1.jpeg", r.query["name"])
        assertEquals(5L, r.contentLength)
        // Der Inhalt bleibt ungelesen im Strom.
        assertEquals("HALLO", input.readBytes().decodeToString())
    }

    @Test
    fun leereVerbindung() {
        assertNull(Http.readRequest(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun kaputteProzentzeichenBrechenNichtAb() {
        assertEquals(mapOf("a" to "100%"), Http.parseQuery("a=100%"))
    }

    @Test
    fun antwortUndJson() {
        val out = ByteArrayOutputStream()
        Http.writeResponse(out, Http.json(200, "ok" to true, "name" to "A \"B\"", "liste" to listOf("x", 1)))
        val text = out.toString("UTF-8")
        assertEquals(true, text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertEquals(true, text.endsWith("{\"ok\":true,\"name\":\"A \\\"B\\\"\",\"liste\":[\"x\",1]}"))
    }
}
