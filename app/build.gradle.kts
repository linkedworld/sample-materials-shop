plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("kotlin-kapt")
}

val composeVersion = "1.4.0-alpha04"
val coroutinesVersion = "1.3.7"
val roomVersion = "2.2.5"
val archLifecycleVersion = "2.5.1"
val filamentVersion = "1.31.3"

dependencies {
    implementation(kotlin("stdlib"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    implementation("com.google.android.material:material:1.8.0")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.0")
    implementation("androidx.activity:activity-compose:1.7.0-alpha04")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    kapt("androidx.lifecycle:lifecycle-common-java8:$archLifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$archLifecycleVersion")

    //implementation ("androidx.compose.animation:animation:1.4.0-alpha05")
    implementation("androidx.compose.animation:animation-core:1.4.0-alpha05")
    runtimeOnly("androidx.compose.animation:animation:1.4.0-alpha05")
    runtimeOnly("androidx.compose.animation:animation-graphics:1.4.0-alpha05")

    implementation ("androidx.compose.foundation:foundation:1.4.0-alpha05")
    implementation ("androidx.compose.material:material:1.4.0-alpha05")
    runtimeOnly("androidx.compose.material3:material3:1.1.0-alpha05")
    implementation("androidx.compose.material:material-icons-extended:1.4.0-alpha05")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0-alpha06")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.0-alpha06")
    implementation ("androidx.compose.runtime:runtime:1.4.0-alpha05")
    implementation ("androidx.compose.runtime:runtime-livedata:1.4.0-alpha04")
    implementation ("androidx.compose.ui:ui:1.4.0-alpha05")
    implementation ("androidx.compose.ui:ui-tooling-preview:1.4.0-alpha05")
    debugImplementation ("androidx.compose.ui:ui-tooling:1.4.0-alpha05")

    // wear
    implementation ("androidx.wear.compose:compose-foundation:1.2.0-alpha03")
    implementation ("androidx.wear.compose:compose-material:1.2.0-alpha03")
    implementation ("androidx.wear.compose:compose-navigation:1.2.0-alpha03")

    implementation ("com.google.android.filament:filament-android:$filamentVersion")
    implementation ("com.google.android.filament:filament-utils-android:$filamentVersion")
    implementation ("com.google.android.filament:gltfio-android:$filamentVersion")
    implementation ("com.google.android.filament:filamat-android-lite:$filamentVersion")
}

android {
    compileSdk = 33
    buildToolsVersion = "33.0.1"

    defaultConfig {
        applicationId = "com.curiouscreature.compose"
        minSdk = 29
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.incremental", "true")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets["main"].java.srcDir("src/main/kotlin")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.0"
    }

    packagingOptions {
        exclude("META-INF/atomicfu.kotlin_module")
    }

    fun com.android.build.api.dsl.AndroidResources.() {
        noCompress("filamat", "ktx", "glb")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        allWarningsAsErrors = false
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        freeCompilerArgs = listOf(
            *kotlinOptions.freeCompilerArgs.toTypedArray(),
            "-Xskip-prerelease-check",
            "-Xskip-metadata-version-check",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}
