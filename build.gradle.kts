plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

android {
    namespace = "pk.myhisaab.sdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs Android resources/manifest available on the JVM.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.fragment.ktx)

    // Unit tests. Robolectric supplies a real android.net.Uri on the JVM so the
    // URL-encoding logic is exercised for real (no emulator needed).
    // If the runner ever complains about AGP/SDK support, bump this version.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// --- JitPack publication -------------------------------------------------
// Repo lives at github.com/myhisaab/myhisaabsdk, so JitPack serves:
//   implementation("com.github.myhisaab:myhisaabsdk:<tag>")
// Keep `version` equal to the git tag you push.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.myhisaab"
            artifactId = "myhisaabsdk"
            version = "1.0.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
