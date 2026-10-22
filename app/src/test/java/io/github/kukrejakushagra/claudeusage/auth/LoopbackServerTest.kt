package io.github.kukrejakushagra.claudeusage.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LoopbackServerTest {
    private fun get(port: Int, pathAndQuery: String): Pair<Int, String> {
        val conn = URL("http://127.0.0.1:$port$pathAndQuery").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        return try {
            val code = conn.responseCode
            val body = (if (code < 400) conn.inputStream else conn.errorStream).bufferedReader().readText()
            code to body
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun ignoresBadRequestsThenAcceptsValidCallback() {
        val server = LoopbackServer("good-state", "<p>Signed in</p>")
        val executor = Executors.newSingleThreadExecutor()
        val pending = executor.submit<LoopbackServer.Result?> { server.await(10_000) }
        try {
            assertEquals(404, get(server.port, "/favicon.ico").first)
            assertEquals(400, get(server.port, "/callback?code=evil&state=bad-state").first)
            assertEquals(400, get(server.port, "/callback?state=good-state").first)
            val (status, body) = get(server.port, "/callback?code=the-code&state=good-state")
            assertEquals(200, status)
            assertTrue(body.contains("Signed in"))
            assertEquals(LoopbackServer.Result.Code("the-code"), pending.get(5, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun reportsDenialWithValidState() {
        val server = LoopbackServer("s", "ok")
        val executor = Executors.newSingleThreadExecutor()
        val pending = executor.submit<LoopbackServer.Result?> { server.await(10_000) }
        try {
            get(server.port, "/callback?error=access_denied&state=s")
            assertEquals(LoopbackServer.Result.Denied("access_denied"), pending.get(5, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun timesOutAndCloseUnblocks() {
        LoopbackServer("s", "ok").use { assertNull(it.await(200)) }

        val server = LoopbackServer("s", "ok")
        val executor = Executors.newSingleThreadExecutor()
        val pending = executor.submit<LoopbackServer.Result?> { server.await(60_000) }
        Thread.sleep(100)
        server.close()
        assertNull(pending.get(5, TimeUnit.SECONDS))
        executor.shutdownNow()
    }

    @Test
    fun successPageLinksBackToApp() {
        val html = LoopbackServer.successPage("com.example.app")
        assertTrue(html.contains("intent://signed-in#Intent;scheme=claudeusage;package=com.example.app;end"))
    }
}
