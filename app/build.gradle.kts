import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.teddyjs.news"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.teddyjs.news"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.0"

        // Gemini API Key - local.properties 에서 주입
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\""
        )
        buildConfigField("String", "OPENWEATHER_API_KEY",
            "\"${project.findProperty("OPENWEATHER_API_KEY") ?: ""}\"")

        buildConfigField("String", "NAVER_CLIENT_ID",
            "\"${project.findProperty("NAVER_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET",
            "\"${project.findProperty("NAVER_CLIENT_SECRET") ?: ""}\"")

        // AdMob App ID - 실제 ID로 교체 필요
        manifestPlaceholders["admobAppId"] = project.findProperty("ADMOB_APP_ID") ?: "ca-app-pub-1691492105013314~8284094988"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.text)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.workmanager.ktx)

    // AdMob
    implementation(libs.admob)

    // Billing
    implementation(libs.billing)

    // Coil
    implementation(libs.coil.compose)

    // Timber
    implementation(libs.timber)

    implementation(libs.compose.material)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.app.update)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
}
