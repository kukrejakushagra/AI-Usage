package io.github.kukrejakushagra.claudeusage.auth

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Minimal one-shot HTTP server on 127.0.0.1 that receives the OAuth redirect.
 *
 * Requests with the wrong path or state are answered with an error page and ignored, so another
 * local app cannot abort the sign-in by sending a bogus request.
 */
class LoopbackServer(
    private val expectedState: String,
    private val successHtml: String,
) : Closeable {

    sealed interface Result {
        data class Code(val code: String) : Result
        data class Denied(val error: String) : Result
    }

    private val serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = serverSocket.localPort

    /** Blocks until a valid callback arrives, the timeout expires (null), or [close] is called (null). */
    fun await(timeoutMillis: Long): Result? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null
            serverSocket.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val client = try {
                serverSocket.accept()
            } catch (_: SocketTimeoutException) {
                return null
            } catch (_: SocketException) {
                return null
            }
            val result = client.use { runCatching { handle(it) }.getOrNull() }
            if (result != null) return result
        }
    }

    private fun handle(socket: Socket): Result? {
        socket.soTimeout = 5_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return null
        // Drain headers; bodies are never expected.
        var headerBytes = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            headerBytes += line.length
            if (headerBytes > 16_384) break
        }

        val parts = requestLine.split(' ')
        if (parts.size < 2 || parts[0] != "GET") {
            respond(socket, 405, "Method Not Allowed", page("Unsupported request."))
            return null
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        if (path != OAuthConfig.CALLBACK_PATH) {
            respond(socket, 404, "Not Found", page("Not found."))
            return null
        }
        val params = parseQuery(target.substringAfter('?', ""))
        val state = params["state"]
        if (state == null || !constantTimeEquals(state, expectedState)) {
            respond(socket, 400, "Bad Request", page("This sign-in link is not valid. Start again from the app."))
            return null
        }
        params["error"]?.let { error ->
            respond(socket, 200, "OK", page("Sign-in was cancelled or denied. You can close this tab."))
            return Result.Denied(params["error_description"] ?: error)
        }
        val code = params["code"]?.takeIf { it.isNotBlank() }
        if (code == null) {
            respond(socket, 400, "Bad Request", page("Missing authorization code."))
            return null
        }
        respond(socket, 200, "OK", successHtml)
        return Result.Code(code)
    }

    private fun respond(socket: Socket, status: Int, reason: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(bytes)
            flush()
        }
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }

    companion object {
        fun page(message: String, extra: String = ""): String = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Claude Usage</title>
            <style>body{font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem;text-align:center}
            a.btn{display:inline-block;margin-top:1.5rem;padding:.8rem 1.4rem;border-radius:.6rem;background:#c96442;color:#fff;text-decoration:none}</style>
            </head><body><p>$message</p>$extra</body></html>
        """.trimIndent()

        fun successPage(appPackage: String): String = page(
            "Signed in. Return to the app.",
            """<a class="btn" href="intent://signed-in#Intent;scheme=claudeusage;package=$appPackage;end">Return to the app</a>""",
        )
    }
}
