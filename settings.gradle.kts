pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "JuHeShiYi"

include(":app")

// KMP shared kernel: domain / data / state live here, shared by Android and iOS.
include(":shared")

// iOS host app. Sources are written here but only buildable on macOS + Xcode.
include(":iosApp")
