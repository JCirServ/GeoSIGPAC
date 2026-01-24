
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
    }

    buildTypes {
        release {
            // 1. Debe ser true para que el shrinker de recursos funcione
            isMinifyEnabled = true 
        
            // 2. En Kotlin DSL se usa el prefijo 'is'
            isShrinkResources = true 
        
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
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
        buildConfig = true
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("ch.acra:acra-mail:5.11.3")
    implementation("ch.acra:acra-dialog:5.11.3")

    implementation("id.zelory:compressor:3.0.1")
    implementation ("com.google.guava:guava:31.1-android")
}
