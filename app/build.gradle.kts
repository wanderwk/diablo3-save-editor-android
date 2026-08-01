import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Root cause of a real shipped bug (2026-08-01): this used to be derived
// from `signingConfigs.getByName("debug")` -- AGP's own auto-managed
// `~/.android/debug.keystore`. That broke in CI: `keytool -genkeypair`
// without an explicit `-storetype` defaults to PKCS12 on modern JDKs, but
// AGP's internal debug-keystore creator expects/writes JKS. When AGP found
// our PKCS12 file at that path, it silently treated it as invalid and
// regenerated its own JKS keystore with a FRESH random keypair for the
// actual signing step -- by which point the hash we'd already baked into
// the native lib (read from OUR file, before AGP's silent regeneration)
// no longer matched the certificate the release APK actually got signed
// with. Every release install self-killed ~1s after launch, silently
// (exactly the native layer's intended behavior for a real mismatch --
// just triggered by our own build, not a repackaging attempt).
//
// Fix: stop touching AGP's magic "debug" signing config entirely. Use our
// own committed keystore (`ci-release.keystore`, PKCS12, checked into git
// below `d3savereleasekey`/`signingConfigs.create("ciRelease")`) as the
// single source of truth for BOTH the actual release signing AND this
// hash -- so there is no second, independent keystore-creation code path
// that can silently diverge from what real signing uses. It's not a real
// secret (same philosophy as the debug-keystore approach this replaces:
// no Play Store distribution, sideloaded APK only), just now a *stable*
// one instead of an ambiguous auto-generated one.
val expectedSigHashHex: String = run {
    val keystoreFile = file("ci-release.keystore")
    try {
        val certProcess = ProcessBuilder(
            "keytool", "-exportcert",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", "d3save-ci-2026", "-alias", "d3savereleasekey",
        ).start()
        val certBytes = certProcess.inputStream.readBytes()
        certProcess.waitFor()
        if (certBytes.isEmpty()) {
            "0000000000000000"
        } else {
            MessageDigest.getInstance("SHA-256")
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

    signingConfigs {
        create("ciRelease") {
            storeFile = file("ci-release.keystore")
            storeType = "PKCS12"
            storePassword = "d3save-ci-2026"
            keyAlias = "d3savereleasekey"
            keyPassword = "d3save-ci-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with a keystore committed to the repo (not a real secret --
            // no Play Store distribution, sideloaded APK only) so the APK can be
            // installed directly, AND so it's stable/known ahead of time for the
            // anti-tamper hash below. See expectedSigHashHex's comment for why
            // this replaced AGP's own auto-managed debug keystore.
            signingConfig = signingConfigs.getByName("ciRelease")
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
