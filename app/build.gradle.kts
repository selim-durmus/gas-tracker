plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tuttoposto.gastracker"
    compileSdk = 36

    defaultConfig {
        // A new package id: the v1 app (com.example.gastracker) was signed with a key that
        // isn't available any more, so v2 installs side by side and imports v1's CSV export.
        applicationId = "com.tuttoposto.gastracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Gas Tracker"
    }

    // Release signing lives outside the repo, in ~/.gradle/gradle.properties:
    //   gasTrackerKeystore, gasTrackerKeystorePassword, gasTrackerKeyAlias
    // Without them, assembleRelease still works but produces an unsigned APK.
    // Keep that keystore backed up — updates only install over an APK signed by the same key.
    val releaseKeystore = providers.gradleProperty("gasTrackerKeystore").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.gradleProperty("gasTrackerKeystorePassword").get()
                keyAlias = providers.gradleProperty("gasTrackerKeyAlias").get()
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        debug {
            // Separate package so debug installs and instrumented test runs (which uninstall
            // the app afterwards) can never touch the real data in the release install.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Gas Tracker (debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        // Version currency is advisory; upgrades are done deliberately.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Room's exported schemas, so MigrationTestHelper can build old database versions.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Aligns the app with room-testing's serialization version; the test APK must match the app.
    implementation(libs.kotlinx.serialization.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Compose UI tests drive Espresso for idling; older Espresso crashes on Android 17.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
