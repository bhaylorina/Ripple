buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.8.3")
    }
}

plugins {
    id("com.android.application") version "8.7.2" apply false
    // 🔥 Yahan 1.9.24 ki jagah 2.3.0 kar diya gaya hai
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false 
}
