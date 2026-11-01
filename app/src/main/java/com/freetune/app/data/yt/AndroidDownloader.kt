package com.freetune.app.data.yt

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Minimal OkHttp-backed downloader used by NewPipeExtractor.
 */
class AndroidDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header("User-Agent", USER_AGENT)
        }
        if (request.headers().keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("YouTube rate limit reached", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string() ?: "",
                response.request.url.toString()
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
