import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("org.jetbrains.compose")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }

  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach { target ->
    target.binaries.framework {
      baseName = "shared"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.components.resources)
      implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
      implementation("sh.calvin.reorderable:reorderable:2.4.0")
    }

    androidMain.dependencies {
      implementation("androidx.core:core-ktx:1.13.1")
      implementation("androidx.appcompat:appcompat:1.7.0")
      implementation("androidx.datastore:datastore-preferences:1.1.1")
      implementation("com.microsoft.onnxruntime:onnxruntime-android:1.26.0")
      // Play Asset Delivery — required so the app can locate the
      // embedding_assets fast-follow pack (model + metadata) on disk
      // after Play finishes downloading it.
      implementation("com.google.android.play:asset-delivery:2.2.2")
    }

    iosMain.dependencies {
    }
  }
}

compose.resources {
  publicResClass = true
  packageOfResClass = "com.dividesbyzer0.biblecompanion"
  generateResClass = always
}

android {
  namespace = "com.dividesbyzer0.biblecompanion.shared"
  compileSdk = 35

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}


