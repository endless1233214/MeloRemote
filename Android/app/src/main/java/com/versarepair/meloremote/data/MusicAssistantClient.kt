package com.versarepair.meloremote.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.versarepair.meloremote.data.model.MAClientException
import com.versarepair.meloremote.data.model.MAEventEnvelope
import com.versarepair.meloremote.data.model.MALoginResponse
import com.versarepair.meloremote.data.model.MAServerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.lang.reflect.Type
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MusicAssistantClient(
    private val gson: Gson = Gson(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
) {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private val partialResults = ConcurrentHashMap<String, MutableList<JsonElement>>()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var handshake: CompletableDeferred<MAServerInfo>? = null
    @Volatile private var closingIntentionally = false
    @Volatile private var eventHandler: ((MAEventEnvelope) -> Unit)? = null

    var baseUrl: HttpUrl? = null
        private set

    fun setEventHandler(handler: ((MAEventEnvelope) -> Unit)?) {
        eventHandler = handler
    }

    suspend fun fetchServerInfo(serverUrl: HttpUrl): MAServerInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(serverUrl.newBuilder().addPathSegment("info").build()).build()
        val response = httpClient.newCall(request).await()
        response.use {
            if (it.code != 200) throw MAClientException.InvalidResponse
            gson.fromJson(it.body.string(), MAServerInfo::class.java)
                ?: throw MAClientException.InvalidResponse
        }
    }

    suspend fun login(serverUrl: HttpUrl, username: String, password: String): MALoginResponse {
        val url = serverUrl.newBuilder().addPathSegments("auth/login").build()
        var firstError: Throwable? = null
        for (request in loginRequests(url, username, password)) {
            try {
                return performLoginRequest(request)
            } catch (error: Throwable) {
                if (firstError == null) firstError = error
            }
        }
        throw firstError ?: MAClientException.AuthenticationFailed("Login failed.")
    }

    fun loginRequests(url: HttpUrl, username: String, password: String): List<Request> {
        val currentPayload = JsonObject().apply {
            addProperty("provider_id", "builtin")
            addProperty("device_name", "MeloRemote")
            add("credentials", JsonObject().apply {
                addProperty("username", username)
                addProperty("password", password)
            })
        }
        val legacyPayload = JsonObject().apply {
            addProperty("provider_id", "builtin")
            addProperty("device_name", "MeloRemote")
            addProperty("username", username)
            addProperty("password", password)
        }
        val formPayload = "username=${username.formEncoded()}&password=${password.formEncoded()}"

        return listOf(
            requestWithBody(url, gson.toJson(currentPayload), "application/json"),
            requestWithBody(url, gson.toJson(legacyPayload), "application/json"),
            requestWithBody(url, formPayload, "application/x-www-form-urlencoded"),
        )
    }

    suspend fun connect(serverUrl: HttpUrl, token: String): MAServerInfo {
        disconnect()
        closingIntentionally = false
        baseUrl = serverUrl

        val socketUrl = serverUrl.newBuilder()
            .scheme(if (serverUrl.isHttps) "wss" else "ws")
            .encodedPath("/ws")
            .query(null)
            .build()
        val currentHandshake = CompletableDeferred<MAServerInfo>()
        handshake = currentHandshake
        val request = Request.Builder().url(socketUrl).build()
        webSocket = httpClient.newWebSocket(request, SocketListener())

        val info = try {
            withTimeout(15_000) { currentHandshake.await() }
        } catch (error: Throwable) {
            disconnect()
            throw error
        } finally {
            handshake = null
        }
        if (info.schemaVersion < info.minSupportedSchemaVersion) {
            disconnect()
            throw MAClientException.IncompatibleServer
        }

        sendCommand(
            "auth",
            mapOf(
                "token" to gson.toJsonTree(token),
                "device_name" to gson.toJsonTree("MeloRemote"),
            ),
        )
        return info
    }

    fun disconnect() {
        closingIntentionally = true
        handshake?.cancel()
        handshake = null
        webSocket?.close(1001, "Client disconnect")
        webSocket = null
        baseUrl = null
        failAllPending(MAClientException.Disconnected)
    }

    suspend fun sendCommand(
        command: String,
        args: Map<String, JsonElement> = emptyMap(),
    ): JsonElement {
        val socket = webSocket ?: throw MAClientException.Disconnected
        val messageId = UUID.randomUUID().toString().replace("-", "")
        val payload = JsonObject().apply {
            addProperty("message_id", messageId)
            addProperty("command", command)
            add("args", JsonObject().also { target -> args.forEach(target::add) })
        }
        val deferred = CompletableDeferred<JsonElement>()
        pending[messageId] = deferred
        if (!socket.send(gson.toJson(payload))) {
            pending.remove(messageId)
            throw MAClientException.Disconnected
        }
        return try {
            withTimeout(20_000) { deferred.await() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            throw MAClientException.Api("Music Assistant did not respond in time.")
        } finally {
            pending.remove(messageId)
            partialResults.remove(messageId)
        }
    }

    suspend fun <T> sendCommand(
        command: String,
        args: Map<String, JsonElement> = emptyMap(),
        type: Type,
    ): T = gson.fromJson(sendCommand(command, args), type)

    private suspend fun performLoginRequest(request: Request): MALoginResponse {
        val response = httpClient.newCall(request).await()
        response.use {
            val body = it.body.string()
            val authError = decodedAuthError(body)
            if (it.code == 401) {
                throw MAClientException.AuthenticationFailed(
                    authError ?: "The username or password is incorrect.",
                )
            }
            if (it.code != 200) {
                throw MAClientException.AuthenticationFailed(
                    authError ?: "Login failed with status ${it.code}.",
                )
            }
            val result = gson.fromJson(body, MALoginResponse::class.java)
                ?: throw MAClientException.InvalidResponse
            if (result.resolvedAccessToken.isNullOrBlank()) {
                throw MAClientException.AuthenticationFailed(
                    result.error ?: "Music Assistant did not return a token.",
                )
            }
            return result
        }
    }

    private fun handleMessage(text: String) {
        val value = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return
        if (!value.isJsonObject) return
        val objectValue = value.asJsonObject

        val activeHandshake = handshake
        if (activeHandshake != null && !activeHandshake.isCompleted && objectValue.has("server_id")) {
            runCatching { gson.fromJson(objectValue, MAServerInfo::class.java) }
                .onSuccess(activeHandshake::complete)
                .onFailure(activeHandshake::completeExceptionally)
            return
        }

        val messageId = objectValue.stringOrNull("message_id")
        if (messageId != null) {
            if (objectValue.has("error_code")) {
                val code = objectValue["error_code"]?.asInt ?: -1
                val details = objectValue.stringOrNull("details")
                    ?: "Music Assistant API error $code."
                pending.remove(messageId)?.completeExceptionally(MAClientException.Api(details))
                partialResults.remove(messageId)
                return
            }
            val result = objectValue["result"] ?: com.google.gson.JsonNull.INSTANCE
            if (objectValue["partial"]?.asBoolean == true) {
                if (result.isJsonArray) {
                    partialResults.computeIfAbsent(messageId) { mutableListOf() }
                        .addAll(result.asJsonArray)
                }
                return
            }
            val accumulated = partialResults.remove(messageId)
            val resolved = if (accumulated != null) {
                JsonArray().apply {
                    accumulated.forEach(::add)
                    if (result.isJsonArray) result.asJsonArray.forEach(::add)
                }
            } else {
                result
            }
            pending.remove(messageId)?.complete(resolved)
            return
        }

        objectValue.stringOrNull("event")?.let { event ->
            eventHandler?.invoke(
                MAEventEnvelope(
                    event = event,
                    objectId = objectValue.stringOrNull("object_id"),
                    data = objectValue["data"],
                ),
            )
        }
    }

    private fun failAllPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        partialResults.clear()
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handshake?.completeExceptionally(t)
            failAllPending(t)
            if (!closingIntentionally) {
                eventHandler?.invoke(MAEventEnvelope("disconnected", null, null))
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            failAllPending(MAClientException.Disconnected)
            if (!closingIntentionally) {
                eventHandler?.invoke(MAEventEnvelope("disconnected", null, null))
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun normalizedServerUrl(address: String): HttpUrl {
            var value = address.trim()
            if (value.isEmpty()) throw MAClientException.InvalidServerAddress
            if (!value.contains("://")) value = "http://$value"
            val url = value.toHttpUrlOrNull() ?: throw MAClientException.InvalidServerAddress
            if (url.scheme != "http" && url.scheme != "https") {
                throw MAClientException.InvalidServerAddress
            }
            return url.newBuilder()
                .encodedPath(url.encodedPath.trimEnd('/').ifEmpty { "/" })
                .build()
        }

        private fun requestWithBody(url: HttpUrl, body: String, contentType: String): Request =
            Request.Builder()
                .url(url)
                .post(body.toRequestBody(contentType.toMediaType()))
                .build()

        private fun decodedAuthError(body: String): String? {
            val value = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
            return value.stringOrNull("error")
                ?: value.stringOrNull("message")
                ?: value.stringOrNull("detail")
        }

        private fun String.formEncoded(): String =
            URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeUnless { it.isJsonNull }?.runCatching { asString }?.getOrNull()

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}
