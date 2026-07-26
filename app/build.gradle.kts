// Build-скрипт модуля app
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.infinityconnect.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.infinityconnect.vpn"
        minSdk = 26
        targetSdk = 35
        // Поднимать при КАЖДОЙ выкладке на сервер обновлений: сравнение версий
        // идёт по коду, а не по versionName (тот у соседних релизов совпадает).
        versionCode = 3
        versionName = "1.0.2"
        vectorDrawables { useSupportLibrary = true }

        // Только 64-бит ARM: нативные ядра (Xray+Hysteria2) весят ~59 МБ на ABI,
        // а x86/armv7 не нужны реальным телефонам. Режет APK с ~256 до ~80 МБ.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "infinity2026"
            keyAlias = "infinityconnect"
            keyPassword = "infinity2026"
        }
    }

    buildTypes {
        debug {
            // В debug добавляем x86_64: на эмуляторе трансляция arm64 (berberis)
            // не тянет Go-рантаймы ядер — SIGILL. Релиз остаётся чистым arm64.
            ndk { abiFilters += "x86_64" }
        }
        release {
            // R8: сжатие/обфускация кода и вычистка ресурсов. Нативные ядра
            // (.so) не трогает — их вес остаётся, но dex/ресурсы ужимаются.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Сеть
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Хранилища
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.kotlinx.coroutines.android)

    // Нативный движок Xray (AndroidLibXrayLite / libv2ray), собран gomobile.
    // Реальный AAR подключён. Стаб (:libv2ray-stub) используется только когда
    // AAR отсутствует — тогда закомментируйте строку ниже и раскомментируйте
    // compileOnly(project(":libv2ray-stub")). См. README.
    implementation(files("libs/libv2ray.aar"))
    // compileOnly(project(":libv2ray-stub"))

    // Нативный клиент Hysteria2: обёртка github.com/infinityconnect/hysteria2mobile
    // над apernet/hysteria (пакет hysteria2), собрана gomobile. Реальный AAR
    // подключён. Стаб (:libhysteria2-stub) используется только когда AAR
    // отсутствует — тогда закомментируйте строку ниже и раскомментируйте
    // compileOnly(project(":libhysteria2-stub")). См. README.
    implementation(files("libs/libhysteria2.aar"))
    // compileOnly(project(":libhysteria2-stub"))
}
