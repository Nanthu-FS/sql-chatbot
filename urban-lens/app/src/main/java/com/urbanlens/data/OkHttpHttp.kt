package com.urbanlens.data

import com.urbanlens.core.net.Http
import com.urbanlens.core.net.HttpException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpHttp(private val client: OkHttpClient = defaultClient()) : Http {

    override suspend fun get(url: String): String = execute(Request.Builder().url(url).build())

    override suspend fun postForm(url: String, form: Map<String, String>): String {
        val body = FormBody.Builder().apply { form.forEach { (key, value) -> add(key, value) } }.build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(HttpException(it.code, "HTTP ${it.code} from ${request.url.host}"))
                    } else {
                        val text = try {
                            it.body?.string().orEmpty()
                        } catch (e: IOException) {
                            continuation.resumeWithException(e)
                            return
                        }
                        continuation.resume(text)
                    }
                }
            }
        })
    }

    companion object {
        /** Public OSM services ask clients to identify themselves. */
        const val USER_AGENT = "UrbanLens/0.1 (Android; https://github.com/Nanthu-FS/urban-lens)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            })
            .build()
    }
}
