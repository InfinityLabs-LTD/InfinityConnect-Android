// Стаб-модуль libhysteria2: только сигнатуры для компиляции.
// В рантайме заменяется реальным нативным AAR (см. README).
plugins {
    id("com.android.library")
}

android {
    namespace = "com.infinityconnect.libhysteria2_stub"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
