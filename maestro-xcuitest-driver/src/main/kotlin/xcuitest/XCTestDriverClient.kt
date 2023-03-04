package xcuitest

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import maestro.api.GetRunningAppRequest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import xcuitest.api.InputTextRequest
import xcuitest.api.SwipeRequest
import xcuitest.api.TouchRequest
import java.io.IOException
import java.util.concurrent.TimeUnit

class XCTestDriverClient(
    private val host: String = "localhost",
    private val port: Int = 22087,
    private val installNetworkInterceptor: Boolean,
    private val restoreConnection: () -> Boolean = { false }
) {

    private var isShuttingDown = false

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            isShuttingDown = true
        })
    }

    private val okHttpClient by lazy {
        if (installNetworkInterceptor) {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(Interceptor {
                    val request = it.request()
                    try {
                        it.proceed(request)
                    } catch (connectException: IOException) {
                        if (restoreConnection()) {
                            it.proceed(request)
                        } else {
                            throw XCTestDriverUnreachable("Failed to reach out XCUITest Server")
                        }
                    }
                }).addNetworkInterceptor(Interceptor {
                    val request = it.request()
                    try {
                        it.proceed(request)
                    } catch (connectException: IOException) {
                        if (restoreConnection() || isShuttingDown) {
                            Response.Builder()
                                .request(it.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .build()
                        } else {
                            throw XCTestDriverUnreachable("Failed to reach out XCUITest Server")
                        }
                    }
                }).build()
        } else {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(Interceptor {
                    try {
                        val request = it.request()
                        it.proceed(request)
                    } catch (exception: IOException) {
                        throw XCTestDriverUnreachable("Failed to reach out XCUITest Server")
                    }
                })
                .build()
        }
    }

    class XCTestDriverUnreachable(message: String) : IOException(message)

    private val mapper = jacksonObjectMapper()

    fun subTree(appId: String): Response {
        val url = xctestAPIBuilder("subTree")
            .addQueryParameter("appId", appId)
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        return okHttpClient.newCall(request).execute()
    }

    fun screenshot(compressed: Boolean): Response {
        val url = xctestAPIBuilder("screenshot")
            .addQueryParameter("compressed", compressed.toString())
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        return okHttpClient.newCall(request).execute()
    }

    fun isScreenStatic(): Response {
        val url = xctestAPIBuilder("isScreenStatic")
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        return okHttpClient.newCall(request).execute()
    }

    fun runningAppId(appIds: Set<String>): Response {
        return executeJsonRequest("runningApp", GetRunningAppRequest(appIds))
    }

    fun swipe(
        appId: String,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        duration: Double
    ): Response {
        return executeJsonRequest("swipe", SwipeRequest(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            duration = duration
        ))
    }

    fun inputText(
        text: String,
    ): Response {
        return executeJsonRequest("inputText", InputTextRequest(text))
    }

    fun tap(
        x: Float,
        y: Float
    ): Response {
        return executeJsonRequest("touch", TouchRequest(
            x = x,
            y = y,
        ))
    }

    private fun xctestAPIBuilder(pathSegment: String): HttpUrl.Builder {
        return HttpUrl.Builder()
            .scheme("http")
            .host(host)
            .addPathSegment(pathSegment)
            .port(port)
    }

    private fun executeJsonRequest(url: String, body: Any): Response {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val bodyData = mapper.writeValueAsString(body).toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .addHeader("Content-Type", "application/json")
            .url(xctestAPIBuilder(url).build())
            .post(bodyData)

        return okHttpClient.newCall(requestBuilder.build()).execute()
    }
}
