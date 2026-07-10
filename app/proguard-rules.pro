# Правила ProGuard/R8.
# kotlinx.serialization — сохраняем сериализаторы.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
