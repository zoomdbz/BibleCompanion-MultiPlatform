import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi

plugins {
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("org.jetbrains.compose")
}

@OptIn(KotlinNativeCacheApi::class)
kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }

  android {
    namespace = "com.dividesbyzer0.biblecompanion.shared"
    compileSdk = 36
    minSdk = 24

    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }

    // Keep commonTest runnable for the Android target after the Android-KMP
    // plugin's opt-in test migration.
    withHostTest {}

    androidResources {
      enable = true
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
      // These existing Compose libraries predate Kotlin 2.1. Keep their APIs
      // unchanged while using Compose 1.9's documented native-cache workaround.
      disableNativeCache(
        version = DisableCacheInKotlinVersion.`2_3_20`,
        reason = "Navigation, Reorderable and extended icons use pre-Kotlin 2.1 Compose libraries."
      )
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

    commonTest.dependencies {
      implementation(kotlin("test"))
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
