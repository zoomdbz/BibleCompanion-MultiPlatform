pluginManagement {
  repositories {
    // Explicit URL forms first so Bitrise's mirror interceptor can't transparently
    // replace them with a stale-cache repo that returns 404 for AGP plugin markers.
    maven { url = uri("https://dl.google.com/android/maven2") }
    maven { url = uri("https://repo.maven.apache.org/maven2") }
    maven { url = uri("https://plugins.gradle.org/m2") }
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    maven { url = uri("https://dl.google.com/android/maven2") }
    maven { url = uri("https://repo.maven.apache.org/maven2") }
    google()
    mavenCentral()
  }
}
rootProject.name = "BibleCompanion"
include(":app")
include(":shared")
