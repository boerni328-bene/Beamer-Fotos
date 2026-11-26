buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Seit AGP 9 übersetzt das Android-Bauwerkzeug Kotlin selbst und bringt dafür eine eigene, ältere
        // Kotlin-Version mit. Diese Zeile hebt sie auf die Version aus libs.versions.toml (passend zum Compose-Plugin).
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
