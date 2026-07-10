// Стаб-модуль libv2ray: только сигнатуры для компиляции.
// В рантайме заменяется реальным нативным AAR (см. README).
plugins {
    id("com.android.library")
}

android {
    namespace = "com.infinityconnect.libv2ray_stub"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
