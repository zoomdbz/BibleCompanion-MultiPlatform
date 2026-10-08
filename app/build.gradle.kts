plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose")
}

android.assetPacks += listOf(":embedding-assets")

dependencies {
  implementation(project(":shared"))
  implementation(compose.runtime)
  implementation(compose.foundation)
  implementation(compose.material3)
  // Includes Android 15 system-bar protection and cutout handling.
  implementation("androidx.activity:activity-compose:1.12.4")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.glance:glance-appwidget:1.1.1")
  implementation("androidx.glance:glance-material3:1.1.1")
  implementation("androidx.datastore:datastore-preferences:1.1.1")
}

android {
  namespace = "com.dividesbyzer0.biblecompanion"
  compileSdk = 36

  sourceSets {
    getByName("main") {
      kotlin.srcDirs("src/androidMain/kotlin")
      res.srcDirs("src/androidMain/res")
      manifest.srcFile("src/androidMain/AndroidManifest.xml")
      assets.srcDirs("../shared/assets")
    }
    getByName("debug") {
      assets.srcDirs("../embedding-assets/src/main/assets")
    }
  }

  defaultConfig {
    applicationId = "com.dividesbyzer0.biblecompanion"
    minSdk = 24
    targetSdk = 36
    versionCode = 51
    versionName = "5.0.0"
    vectorDrawables.useSupportLibrary = true
    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
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

  // Let Android memory-map bundled fonts instead of copying compressed CJK
  // font files into the app heap when languages or font weights change.
  androidResources {
    noCompress += "ttf"
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
