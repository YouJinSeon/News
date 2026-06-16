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
        versionCode = 24
        versionName = "1.0.2"

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

    signingConfigs {
        create("release") {
            // 비밀값은 local.properties에서 읽음(깃에 올리지 않음)
            val storeFilePath = project.findProperty("RELEASE_STORE_FILE") as String?
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            // local.properties에 RELEASE_STORE_FILE 등이 있으면 릴리스 서명 적용
            if (project.findProperty("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation(libs.play.review)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.install.referrer)
}
