package com.lumecard.shared.data.ai.stream

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*

sealed class StreamReadResult {
    data class Chunk(val text: String, val bytesRead: Long, val totalBytes: Long?) : StreamReadResult()
    data object Done : StreamReadResult()
    data class Error(val message: String) : StreamReadResult()
}

class AiStreamReader(private val client: HttpClient) {

    suspend fun readStream(
        url: String,
        block: HttpRequestBuilder.() -> Unit,
        onChunk: suspend (StreamReadResult) -> Unit,
    ) {
        val response = client.post(url) {
            block()
        }

        val contentLength = response.contentLength()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(8192)
        var received = 0L

        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer)
            if (read > 0) {
                received += read
                val text = buffer.decodeToString(0, read)
                onChunk(StreamReadResult.Chunk(text, received, contentLength))
            }
        }

        onChunk(StreamReadResult.Done)
    }
}
