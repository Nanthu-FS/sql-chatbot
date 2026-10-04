package com.genshincalc.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a player's in-game Character Showcase from Enka.Network (https://enka.network). */
object ShowcaseClient {
    private const val USER_AGENT = "TeyvatDmgCalc/1.0 (Android)"

    /** A failure with a message for the user. */
    class Failure(message: String) : IOException(message)

    suspend fun fetch(uid: String): String = withContext(Dispatchers.IO) {
        val conn = URL("https://enka.network/api/uid/$uid/").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                throw Failure("Couldn't reach enka.network. Check your internet connection.")
            }
            if (code != HttpURLConnection.HTTP_OK) throw Failure(message(code))
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun message(code: Int): String = when (code) {
        400 -> "That doesn't look like a UID."
        404 -> "There is no player with this UID."
        424 -> "The game is under maintenance. Try again later."
        429 -> "Too many requests. Wait a minute and try again."
        else -> "The showcase service isn't working right now (error $code). Try again later."
    }
}
