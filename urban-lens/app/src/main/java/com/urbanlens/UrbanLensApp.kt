package com.urbanlens

import android.app.Application
import android.content.Context
import com.urbanlens.alerts.AlertNotifier
import com.urbanlens.alerts.AlertScheduler
import com.urbanlens.core.data.CityDataRepository
import com.urbanlens.core.routing.RoutePlanner
import com.urbanlens.data.AppDatabase
import com.urbanlens.data.LocationProvider
import com.urbanlens.data.OkHttpHttp
import com.urbanlens.data.ReportRepository
import com.urbanlens.data.SavedPlaceRepository
import com.urbanlens.data.SettingsStore
import org.maplibre.android.MapLibre

class UrbanLensApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MapLibre.getInstance(this)
        AlertNotifier.createChannel(this)
        AlertScheduler.apply(this, container.settings.current().alertsEnabled)
    }
}

/** Manual dependency wiring; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val http = OkHttpHttp()
    val database: AppDatabase = AppDatabase.create(context)
    val settings = SettingsStore(context)
    val cityData = CityDataRepository(http)
    val routePlanner = RoutePlanner(http)
    val reports = ReportRepository(context, database.reports())
    val savedPlaces = SavedPlaceRepository(database.savedPlaces())
    val location = LocationProvider(context)
}

val Context.appContainer: AppContainer get() = (applicationContext as UrbanLensApp).container
