import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.android.library)
}

kotlin {
  // JVM target exists so the shared kernel can be compiled and unit-tested on
  // Windows/Linux CI. iOS frameworks can only be produced on macOS + Xcode.
  jvm()

  androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

  // iOS targets are declared only on macOS: Kotlin/Native can *configure* them
  // elsewhere but cannot link frameworks without the Apple SDK.
  if (isMacOs()) {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
      target.binaries.framework {
        baseName = "SharedKit"
        isStatic = true
      }
    }
  }

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.json)
      implementation(libs.kotlinx.io.core)
      implementation(libs.multiplatform.settings)
      implementation(libs.kermit)
      api(libs.ktor.client.core)
    }

    commonTest.dependencies { implementation(libs.junit) }

    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      implementation(libs.ksoup.html)
    }

    if (isMacOs()) {
      iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    }

    jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
  }
}

android {
  namespace = "com.lyihub.archiveassistant.shared"
  compileSdk = 36
  defaultConfig { minSdk = 31 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

fun isMacOs(): Boolean = System.getProperty("os.name").lowercase().startsWith("mac")
