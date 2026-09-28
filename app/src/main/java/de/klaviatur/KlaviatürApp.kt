package de.klaviatur

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.klaviatur.util.AppSettings

@HiltAndroidApp
class KlaviatürApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
    }
}
