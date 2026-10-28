buildscript {
    dependencies {
        // SafeArgs plugin for Navigation
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
    }
}

// Android aur Kotlin ke main versions
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
