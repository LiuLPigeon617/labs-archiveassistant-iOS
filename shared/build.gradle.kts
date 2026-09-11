import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.compose.multiplatform)
  // Required since Kotlin 2.0.0 whenever the Compose Multiplatform plugin is applied.
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.android.library)
}

kotlin {
  // JVM target exists so the shared kernel can be compiled and unit-tested on
  // Windows/Linux CI. iOS frameworks can only be produced on macOS + Xcode.
  jvm("desktop")
  androidTarget { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

  // iOS targets are declared only on macOS: Kotlin/Native can *configure* them
  // elsewhere but cannot link frameworks without the Apple SDK.
  if (isMacOs()) {
    val xcf = XCFramework("SharedKit")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
      target.binaries.framework {
        baseName = "SharedKit"
        isStatic = true
        // Registering each framework with the XCFramework is what generates the
        // `assembleSharedKitXCFramework` task used by the Xcode build phase and CI.
        xcf.add(this)
      }
    }
  }

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.ui)
      implementation(compose.material3)
      implementation(compose.components.resources)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.json)
      implementation(libs.kotlinx.io.core)
      implementation(libs.multiplatform.settings)
      implementation(libs.kermit)
      api(libs.ktor.client.core)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(kotlin("test-common"))
      implementation(kotlin("test-annotations-common"))
      implementation(libs.kotlinx.coroutines.test)
    }

    // A named jvm target produces "desktopMain"/"desktopTest", but the Kotlin DSL needs an explicit
    // accessor to reference them directly.
    val desktopMain by getting
    val desktopTest by getting

    desktopTest.dependencies {
      implementation(kotlin("test-junit"))
      implementation(libs.junit)
    }

    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      // Android keeps Jsoup: it is already bundled and battle-tested on this platform.
      implementation(libs.jsoup)
    }

    // The desktop target compiles to bytecode, so it can use Jsoup directly too. Only the
    // Kotlin/Native iOS target needs the multiplatform port (Ksoup).
    desktopMain.dependencies {
      implementation(libs.ktor.client.okhttp)
      implementation(libs.jsoup)
    }

    if (isMacOs()) {
      iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
        implementation(libs.ksoup.html)
      }
    }
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
