plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Computed once at Gradle configuration time and reused for BOTH the native
// (C++ compiler define, app/src/main/cpp/storage_sync.cpp) and Java
// (BuildConfig field, util/CacheWarmup.kt) signing-certificate checks below.
// Deriving it from the *actual* debug keystore this machine/CI runner will
// sign the release APK with (rather than a value precomputed once and
// hardcoded) means it's always correct regardless of which keystore ends up
// being used -- no risk of embedding a stale constant that doesn't match
// the real signature and bricks a legitimate release build.
val expectedSigHashHex: String = run {
    val keystoreDir = File(System.getProperty("user.home"), ".android")
    val keystoreFile = File(keystoreDir, "debug.keystore")
    if (!keystoreFile.exists()) {
        keystoreDir.mkdirs()
        ProcessBuilder(
            "keytool", "-genkeypair", "-v",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", "android", "-keypass", "android",
            "-alias", "androiddebugkey",
            "-dname", "CN=Android Debug,O=Android,C=US",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
        ).redirectErrorStream(true).start().waitFor()
    }
    try {
        val certProcess = ProcessBuilder(
            "keytool", "-exportcert",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", "android", "-alias", "androiddebugkey",
        ).start()
        val certBytes = certProcess.inputStream.readBytes()
        certProcess.waitFor()
        if (certBytes.isEmpty()) {
            "0000000000000000"
        } else {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(certBytes).take(8).joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        "0000000000000000"
    }
}

android {
    namespace = "com.wanderwk.d3saveeditor"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.wanderwk.d3saveeditor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the APK can be installed directly
            // without needing a release keystore/secret in CI.
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "EXPECTED_SIG_HASH_HEX", "\"$expectedSigHashHex\"")
            externalNativeBuild {
                cmake {
                    // Anti-tamper (signing-certificate) check is release-only -- see
                    // storage_sync.cpp and util/CacheWarmup.kt. Debug builds compile
                    // storage_sync.cpp as a no-op (ANTI_TAMPER_ENABLED undefined), so
                    // local/CI debug builds are never affected by this.
                    cppFlags += "-DANTI_TAMPER_ENABLED=1"
                    cppFlags += "-DEXPECTED_SIG_HASH=0x${expectedSigHashHex}ULL"
                }
            }
        }
        debug {
            buildConfigField("String", "EXPECTED_SIG_HASH_HEX", "\"0\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
