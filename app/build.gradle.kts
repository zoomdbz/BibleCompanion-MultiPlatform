plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose")
}

android.assetPacks += listOf(":embedding-assets")

kotlin {
  androidTarget {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(project(":shared"))
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation("androidx.activity:activity-compose:1.9.2")
      implementation("androidx.appcompat:appcompat:1.7.0")
      implementation("androidx.core:core-ktx:1.13.1")
      // Force a Material version past the deprecated Window.setStatusBarColor /
      // setNavigationBarColor calls flagged by Play Console against v1.1.6.
      // Pulled in transitively by appcompat; this pin overrides the older
      // 1.12.x that triggers the warning.
      implementation("com.google.android.material:material:1.13.0-alpha13")
      implementation("androidx.glance:glance-appwidget:1.1.1")
      implementation("androidx.glance:glance-material3:1.1.1")
      implementation("androidx.datastore:datastore-preferences:1.1.1")
    }
  }
}

android {
  namespace = "com.dividesbyzer0.biblecompanion"
  compileSdk = 35

  sourceSets {
    getByName("main") {
      assets.srcDirs("../shared/assets")
    }
    getByName("debug") {
      assets.srcDirs("../embedding-assets/src/main/assets")
    }
  }

  defaultConfig {
    applicationId = "com.dividesbyzer0.biblecompanion"
    minSdk = 24
    targetSdk = 35
    versionCode = 41
    versionName = "4.1.0"
    vectorDrawables.useSupportLibrary = true
    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
    debug {
      isMinifyEnabled = false
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources.excludes += setOf(
      "META-INF/DEPENDENCIES",
      "META-INF/LICENSE*",
      "META-INF/NOTICE*"
    )
  }
}
