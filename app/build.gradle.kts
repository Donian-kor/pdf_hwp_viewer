import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    signingProps.getProperty(propKey) ?: System.getenv(envKey)

val storePath = signingValue("release.storeFile", "SIGNING_STORE_FILE")
val storePass = signingValue("release.storePassword", "SIGNING_STORE_PASSWORD")
val keyAliasValue = signingValue("release.keyAlias", "SIGNING_KEY_ALIAS")
val keyPass = signingValue("release.keyPassword", "SIGNING_KEY_PASSWORD")
val hasSigning = listOf(storePath, storePass, keyAliasValue, keyPass).all { !it.isNullOrBlank() }

android {
    namespace = "kr.docuview.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "kr.docuview.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(storePath!!)
                storePassword = storePass
                keyAlias = keyAliasValue
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasSigning) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        android {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Keep the in-app privacy policy in sync with the single source of truth at the project root.
    val privacyAssetsDir = layout.buildDirectory.dir("generated/assets/privacy")
    val copyPrivacyPolicy = tasks.register("copyPrivacyPolicy", Copy::class) {
        from(rootProject.file("privacy-policy.html"))
        into(privacyAssetsDir)
    }
    sourceSets {
        getByName("main") {
            assets.srcDir { privacyAssetsDir }
        }
    }
    tasks.matching {
        it.name == "mergeDebugAssets" ||
            it.name == "mergeReleaseAssets" ||
            it.name == "generateReleaseLintVitalReportModel" ||
            it.name == "lintVitalAnalyzeRelease"
    }.configureEach { dependsOn(copyPrivacyPolicy) }
}

gradle.taskGraph.whenReady {
    val wantsRelease = allTasks.any { it.name == "assembleRelease" || it.name == "bundleRelease" }
    if (wantsRelease && !hasSigning) {
        throw GradleException(
            "릴리즈 서명 정보가 없습니다. signing.properties 또는 SIGNING_* 환경 변수를 설정하세요.",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
