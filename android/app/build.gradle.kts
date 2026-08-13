plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.handsignal.arduino"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.handsignal.arduino"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // The .task model must not be compressed, MediaPipe memory-maps it.
    androidResources {
        noCompress += listOf("task", "tflite")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
}

// Downloads the MediaPipe hand landmark model into assets at build time so the
// finished APK runs 100% offline.
val modelFile = file("src/main/assets/hand_landmarker.task")
val downloadHandModel by tasks.registering {
    outputs.file(modelFile)
    doLast {
        if (!modelFile.exists() || modelFile.length() < 1_000_000) {
            modelFile.parentFile.mkdirs()
            val modelUrl = uri("https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task").toURL()
            modelUrl.openStream().use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }

        }
    }
}
tasks.named("preBuild") { dependsOn(downloadHandModel) }
