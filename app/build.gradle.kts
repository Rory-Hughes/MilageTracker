import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    alias(libs.plugins.hilt)
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

kotlin {
    jvmToolchain(11)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
    source.setFrom(
        "src/main/java",
        "src/test/java",
        "src/androidTest/java",
    )
    basePath = rootDir
}

// Release signing credentials are read from the gitignored `local.properties`
// (never checked in). When the keys are absent — CI, fresh clones, other
// machines — `release` simply builds unsigned, preserving the prior behaviour
// instead of failing the build. RELEASE_STORE_FILE is resolved relative to the
// repository root. To sign locally, add to local.properties:
//   RELEASE_STORE_FILE=keystore/mileagetracker-upload.jks
//   RELEASE_STORE_PASSWORD=...
//   RELEASE_KEY_ALIAS=upload
//   RELEASE_KEY_PASSWORD=...
val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("local.properties")
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("RELEASE_STORE_FILE") != null

android {
    namespace = "com.example.mileagetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.mileagetracker"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.example.mileagetracker.HiltTestRunner"

        // Disabled V2 screens still compile, but the single-user release ships no OAuth configuration.
        buildConfigField("boolean", "FEATURE_TEAM", "false")
        buildConfigField("boolean", "FEATURE_ACCOUNTING_INTEGRATIONS", "false")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("RELEASE_STORE_FILE")!!)
                storePassword = keystoreProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        // Plain JVM unit tests stub out the Android framework. Returning
        // default values (zeros, nulls, no-ops) lets non-Robolectric tests
        // call e.g. android.util.Log without throwing "not mocked"; tests
        // that need real Android behaviour can still opt into Robolectric.
        unitTests.isReturnDefaultValues = true
    }
}

// Make the secrets-gradle-plugin fall back to `local.defaults.properties`
// for any key not present in `local.properties`. This keeps API-key
// configuration uniform: real keys live in `local.properties` (gitignored),
// safe sentinels live in `local.defaults.properties` (checked in).
secrets {
    defaultPropertiesFileName = "local.defaults.properties"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("11")
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("11")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.play.services.location)
    implementation(libs.play.services.tasks)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.material3)
    implementation(libs.material)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.places)
    testImplementation(libs.junit)
    testImplementation(libs.mockk.jvm)
    testImplementation(libs.kotlinx.coroutines.test.jvm)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.firebase.appcheck.debug)
}
