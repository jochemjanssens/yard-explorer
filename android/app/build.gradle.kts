import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// The Play upload key: from keystore.properties locally (never committed), or from environment variables in the
// GitHub release workflow (filled from repository secrets).
val keystoreProps = Properties().apply {
  rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
  System.getenv("UPLOAD_STORE_FILE")?.let { file ->
    setProperty("storeFile", file)
    setProperty("storePassword", System.getenv("UPLOAD_STORE_PASSWORD"))
    setProperty("keyPassword", System.getenv("UPLOAD_KEY_PASSWORD") ?: System.getenv("UPLOAD_STORE_PASSWORD"))
    setProperty("keyAlias", System.getenv("UPLOAD_KEY_ALIAS") ?: "upload")
  }
}

android {
  namespace = "be.jochemjanssens.yardexplorer"
  compileSdk = 36
  defaultConfig {
    applicationId = "be.jochemjanssens.yardexplorer"
    minSdk = 26
    targetSdk = 36
    versionCode = 2
    versionName = "0.0.2"
  }

  signingConfigs {
    if (!keystoreProps.isEmpty) create("upload") {
      storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
      storePassword = keystoreProps.getProperty("storePassword")
      keyAlias = keystoreProps.getProperty("keyAlias")
      keyPassword = keystoreProps.getProperty("keyPassword")
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // The upload key when keystore.properties exists; otherwise the local debug key, which is fine for
      // installing on your own phone but is rejected by Play Console.
      signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Map: MapLibre with OpenStreetMap raster tiles; OkHttp to send an identifying User-Agent.
  implementation(libs.maplibre.android)
  implementation(libs.okhttp)

  // Unit tests for the KML / yard logic (plain JVM)
  testImplementation(libs.junit)
}
