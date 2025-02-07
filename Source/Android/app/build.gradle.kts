plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

android {
    compileSdk = 35

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        // Uncomment disable lines for test builds...
        // disable.add("MissingTranslation")
        // disable.add("ExtraTranslation")
    }

    defaultConfig {
        applicationId = "org.mm.j"
        minSdk = 24
        targetSdk = 29
        versionCode = getBuildVersionCode()
        versionName = getVersion()

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DANDROID_PLATFORM=android-24",
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DCMAKE_CXX_FLAGS_RELEASE=-Ofast"
                )
                abiFilters.add("arm64-v8a")
            }
        }
    }

    signingConfigs {
        create("release") {
            val keystoreAlias = System.getenv("KEYSTORE_ALIAS") ?: "androiddebugkey"
            val keystorePassword = System.getenv("KEYSTORE_PASS") ?: "android"
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${project.rootDir}/debug.keystore"

            keyAlias = keystoreAlias
            keyPassword = keystorePassword
            storeFile = file(keystorePath)
            storePassword = keystorePassword
        }

        create("custom_debug") {
            storeFile = file("${project.rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("custom_debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isJniDebuggable = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../../CMakeLists.txt")
            version = "3.22.1+"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    namespace = "org.dolphinemu.dolphinemu"
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt.coil3:coil:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
}

fun getVersion(): String {
    var versionNumber = "0.0"
    try {
        versionNumber = Runtime.getRuntime().exec("git describe --always --long", null, project.rootDir).inputStream.bufferedReader().readText()
            .trim()
            .replace(Regex("(-0)?-[^-]+$"), "")
    } catch (e: Exception) {
        logger.error("Cannot find git, defaulting to dummy version number")
    }
    return "$versionNumber-mmj"
}

fun getBuildVersionCode(): Int {
    return try {
        val versionNumber = Runtime.getRuntime().exec("git rev-list --first-parent --count HEAD", null, project.rootDir).inputStream.bufferedReader().readText().trim()
        versionNumber.toInt()
    } catch (e: Exception) {
        logger.error("Cannot find git, defaulting to dummy version number")
        15808
    }
}
