plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mobileclient.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        // Minecraft Bedrock on Android is 64-bit ARM (add "x86_64" for emulators).
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }

    buildFeatures {
        prefab = true            // consume ShadowHook's native API
        prefabPublishing = true  // ship libmobileclient.so + header to NDK users of this AAR
    }
    prefab {
        create("mobileclient") { headers = "src/main/cpp/include" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Inline-hook engine (used for the eglSwapBuffers FPS hook and for your own game hooks).
    implementation("com.bytedance.android:shadowhook:1.0.9")
}
