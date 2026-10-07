package com.example.mileagetracker.di

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifies the Google Maps API key string so Hilt can distinguish it from any
 * other injected [String].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapsApiKey

/**
 * Qualifies the geocode.maps.co reverse-geocoding API key. Read from the
 * manifest meta-data injected by the secrets-gradle-plugin from
 * `local.properties` (key `GEOCODE_MAPS_CO_API_KEY`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeocodeMapsCoApiKey

/**
 * Hilt module for shared networking dependencies.
 *
 * Provides the singleton [OkHttpClient] reused across the app and the Google
 * Maps API key read from the manifest `<meta-data>` entry injected by the
 * secrets-gradle-plugin. Centralising the key here keeps the meta-data lookup
 * out of ViewModels and repositories.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    @MapsApiKey
    fun provideMapsApiKey(@ApplicationContext context: Context): String {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        // Fail fast at graph construction rather than letting an empty key reach
        // Google and surface as an opaque 400/403 at runtime.
        val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        return requireNotNull(key?.takeIf { it.isNotBlank() }) {
            "com.google.android.geo.API_KEY is missing or blank in AndroidManifest.xml meta-data"
        }
    }

    /**
     * Provides the geocode.maps.co API key, or an empty string if unconfigured.
     *
     * Unlike [provideMapsApiKey] this does NOT throw on missing — reverse
     * geocoding is a non-fatal enhancement (we fall back to raw coordinates),
     * so a missing key should not prevent the app from starting. The
     * repository checks `isBlank()` before calling the service.
     */
    @Provides
    @Singleton
    @GeocodeMapsCoApiKey
    fun provideGeocodeMapsCoApiKey(@ApplicationContext context: Context): String {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val raw = appInfo.metaData?.getString(
            "com.example.mileagetracker.GEOCODE_MAPS_CO_API_KEY",
        )
        return raw
            ?.takeIf { it.isNotBlank() && it != "REPLACE_WITH_GEOCODE_MAPS_CO_API_KEY" }
            ?: ""
    }
}
