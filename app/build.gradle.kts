import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "org.opentvsearch"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.opentvsearch"
        // minSdk 23 keeps the app installable broadly; the TV-Provider search
        // source (READ_TV_LISTINGS / preview_programs) is feature-gated at API 26+.
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    // JVM unit tests run against the stub android.jar. Returning default values (instead of
    // throwing "not mocked") lets pure logic that touches android types (e.g. constructing an
    // Intent, Uri.parse) run without Robolectric. Used by SearchAggregatorTest and the pure
    // mapRow tests in TvProviderSearchSourceTest.
    testOptions { unitTests { isReturnDefaultValues = true } }
}

dependencies {
    // --- Compose for TV ---
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // --- Leanback + TV Provider (cross-app searchable rows) ---
    implementation("androidx.leanback:leanback:1.1.0-rc02")
    implementation("androidx.tvprovider:tvprovider:1.1.0")

    // --- DI ---
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // --- Settings persistence ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Networking (open-app adapters: Jellyfin/Plex/Kodi/Stremio) ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // --- Test ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
}
