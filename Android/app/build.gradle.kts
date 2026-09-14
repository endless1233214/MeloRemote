import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

abstract class VerifyReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val signingConfigured: Property<Boolean>

    @get:Input
    abstract val storePath: Property<String>

    @TaskAction
    fun verifySigning() {
        check(signingConfigured.get()) {
            "Release signing is not configured. Set MELOREMOTE_UPLOAD_STORE_FILE, " +
                "MELOREMOTE_UPLOAD_STORE_PASSWORD, MELOREMOTE_UPLOAD_KEY_ALIAS, and " +
                "MELOREMOTE_UPLOAD_KEY_PASSWORD as Gradle properties or environment variables."
        }
        check(File(storePath.get()).isFile) {
            "The configured MeloRemote upload keystore does not exist."
        }
    }
}

val releaseStoreFile = providers.gradleProperty("MELOREMOTE_UPLOAD_STORE_FILE")
    .orElse(providers.environmentVariable("MELOREMOTE_UPLOAD_STORE_FILE"))
    .orNull
val releaseStorePassword = providers.gradleProperty("MELOREMOTE_UPLOAD_STORE_PASSWORD")
    .orElse(providers.environmentVariable("MELOREMOTE_UPLOAD_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("MELOREMOTE_UPLOAD_KEY_ALIAS")
    .orElse(providers.environmentVariable("MELOREMOTE_UPLOAD_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("MELOREMOTE_UPLOAD_KEY_PASSWORD")
    .orElse(providers.environmentVariable("MELOREMOTE_UPLOAD_KEY_PASSWORD"))
    .orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.versarepair.meloremote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.versarepair.meloremote"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

tasks.register<VerifyReleaseSigningTask>("verifyReleaseSigning") {
    group = "verification"
    description = "Checks that all MeloRemote upload-signing secrets are available."
    signingConfigured.set(hasReleaseSigning)
    storePath.set(releaseStoreFile.orEmpty())
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.14.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
