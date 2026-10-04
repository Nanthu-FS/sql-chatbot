package com.urbanlens.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.urbanlens.core.geo.LatLng
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Found(val location: LatLng) : LocationResult
    data object NoPermission : LocationResult
    data object Disabled : LocationResult
    data object Unavailable : LocationResult
}

/**
 * One-shot location using the platform LocationManager (no Play Services needed).
 * Asks the fused/network providers alongside GPS so a fix arrives quickly indoors, and only
 * uses GPS when precise location was granted ("approximate only" users can't use it).
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMillis: Long = 20_000): LocationResult {
        val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!precise && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return LocationResult.NoPermission
        val manager = context.getSystemService(LocationManager::class.java) ?: return LocationResult.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.Disabled

        val providers = usableProviders(manager, precise)
        val lastKnown = (providers + LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> safely { manager.getLastKnownLocation(provider) } }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < RECENT_FIX_MILLIS) {
            return LocationResult.Found(lastKnown.toLatLng())
        }

        val fresh = if (providers.isEmpty()) null else withTimeoutOrNull(timeoutMillis) { firstFix(manager, providers) }
        val best = fresh ?: lastKnown
        return if (best != null) LocationResult.Found(best.toLatLng()) else LocationResult.Unavailable
    }

    private fun usableProviders(manager: LocationManager, precise: Boolean): List<String> {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (precise) add(LocationManager.GPS_PROVIDER)
        }
        val existing = safely { manager.allProviders }.orEmpty()
        return candidates.filter { it in existing && safely { manager.isProviderEnabled(it) } == true }
    }

    /** Asks every provider at once; the first fix wins and the other requests are cancelled. */
    private suspend fun firstFix(manager: LocationManager, providers: List<String>): Location? =
        channelFlow {
            for (provider in providers) {
                launch {
                    val fix = try {
                        freshFix(manager, provider)
                    } catch (e: SecurityException) {
                        null
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                    if (fix != null) send(fix)
                }
            }
        }.firstOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }

                    // Older platforms call these without default implementations.
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun Location.toLatLng() = LatLng(latitude, longitude)

    private companion object {
        /** A last-known fix this recent is good enough to show immediately. */
        const val RECENT_FIX_MILLIS = 10 * 60_000L
    }
}
