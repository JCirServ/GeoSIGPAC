
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.geosigpac.cirserv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geosigpac.cirserv"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // SEGURIDAD: Inyección de API Key desde Variable de Entorno
        // Para configurar:
        // 1. En local (Linux/Mac): export GEMINI_API_KEY="tu_clave_real"
        // 2. En local (Windows): set GEMINI_API_KEY="tu_clave_real"
        // 3. O crear un archivo local.properties y añadir: GEMINI_API_KEY=tu_clave
        val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlinOptions {
        jvmTarget = "21"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true // Necesario para acceder a BuildConfig.GEMINI_API_KEY
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Mapas (MapLibre)
    implementation(libs.maplibre.android.sdk)
    
    // Cámara (CameraX)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
    // Google AI (Gemini) Nativo
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    implementation("com.google.code.gson:gson:2.10.1")

    // Coil (Image Loading)
    implementation(libs.coil.compose)

    // Spatial Indexing (R-Tree)
    implementation("com.github.davidmoten:rtree:0.12")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
