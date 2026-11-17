plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.metro)
}

android {
    namespace = "be.digitalia.mediasession2mqtt"
    compileSdk = 37

    defaultConfig {
        applicationId = "be.digitalia.mediasession2mqtt"
        minSdk = 22
        targetSdk = 37
        versionCode = 2200117
        versionName = "1.1.6-s0undtv.1"

        androidResources.localeFilters += listOf("en")
    }

    // Fork-only: sign with a local key when ~/.gradle/gradle.properties provides one, otherwise build unsigned as upstream
    val forkKeystore = providers.gradleProperty("MS2MQTT_KEYSTORE").orNull
    val forkSigningConfig = forkKeystore?.let {
        signingConfigs.create("fork") {
            storeFile = file(it)
            storePassword = providers.gradleProperty("MS2MQTT_KEYSTORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("MS2MQTT_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("MS2MQTT_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            forkSigningConfig?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                "proguard-defaults.txt",
                "proguard-rules.pro"
            )

            packaging {
                resources {
                    excludes += listOf(
                        "DebugProbesKt.bin",
                        "kotlin-tooling-metadata.json",
                        "kotlin/**",
                        "META-INF/*.version",
                        "META-INF/androidx/**",
                        "META-INF/dev/zacsweers/metro/**",
                        "META-INF/versions/**",
                        "META-INF/NOTICE.md"
                    )
                }
                vcsInfo.include = false
            }
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kmqtt.common)
    implementation(libs.kmqtt.client)

    testImplementation(libs.junit)
}
