# Правила ProGuard/R8.
# kotlinx.serialization — сохраняем сериализаторы.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable DTO: имена/поля нужны рефлексии плагина сериализации.
-keep,includedescriptorclasses class com.infinityconnect.vpn.**$$serializer { *; }
-keepclassmembers class com.infinityconnect.vpn.** {
    *** Companion;
}

# gomobile-обёртки нативных ядер: классы дергаются из JNI (libgojni.so /
# libhy2gojni.so) по имени — R8 не должен их переименовывать/выкидывать.
#
# go.** — рантайм gomobile для Xray (libv2ray).
# hy2go.** — тот же рантайм для Hysteria2, переименованный патчем тулчейна,
#   чтобы два AAR не конфликтовали (см. hysteria2-mobile/apply-toolchain-patch.sh).
#   Без этого правила Hysteria2 падает ТОЛЬКО в релизе: JNI ищет hy2go/Seq,
#   а R8 его уже обфусцировал.
-keep class go.** { *; }
-keep class hy2go.** { *; }
-keep class libv2ray.** { *; }
-keep class hysteria2.** { *; }

# Retrofit/OkHttp (стандартные правила).
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn kotlinx.coroutines.**

# Tink (androidx.security-crypto) ссылается на compile-only аннотации errorprone.
-dontwarn com.google.errorprone.annotations.**
