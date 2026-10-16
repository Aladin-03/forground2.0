import com.android.build.api.variant.impl.VariantOutputImpl
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// Release signing values live in keystore.properties at the repo root (git-ignored; see keystore.properties.example).
// Read at configuration time but only enforced by release tasks, so debug builds and tests work without it.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use(::load)
    }
val keystoreKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val keystoreProblem: String? =
    when {
        !keystorePropertiesFile.isFile ->
            "Release signing: ${keystorePropertiesFile.path} is missing. " +
                "Copy keystore.properties.example to keystore.properties and fill in ${keystoreKeys.joinToString()}."
        keystoreKeys.any { keystoreProperties.getProperty(it).isNullOrBlank() } ->
            "Release signing: keystore.properties has no value for " +
                keystoreKeys.filter { keystoreProperties.getProperty(it).isNullOrBlank() }.joinToString() + "."
        !rootProject.file(keystoreProperties.getProperty("storeFile")).isFile ->
            "Release signing: storeFile ${rootProject.file(keystoreProperties.getProperty("storeFile")).path} not found."
        else -> null
    }

android {
    namespace = "com.aladin.foreground2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aladin.foreground2"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProblem == null) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Short timings so the full gate cycle can be exercised by hand on a device.
            buildConfigField("long", "BREATH_DURATION_MS", "10_000L")
            buildConfigField("long", "ALLOWED_WINDOW_MS", "60_000L")
            buildConfigField("long", "POLL_INTERVAL_MS", "500L")
            buildConfigField("long", "PERMISSION_CHECK_INTERVAL_MS", "5_000L")
            buildConfigField("long", "WIDGET_REFRESH_INTERVAL_MS", "300_000L")
            buildConfigField("long", "WIDGET_MIN_REFRESH_INTERVAL_MS", "60_000L")
            buildConfigField("boolean", "LOGGING_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the key from keystore.properties. Updates must keep using the same key, or Android refuses
            // them (uninstall first, which wipes locks).
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("long", "BREATH_DURATION_MS", "60_000L")
            buildConfigField("long", "ALLOWED_WINDOW_MS", "600_000L")
            buildConfigField("long", "POLL_INTERVAL_MS", "500L")
            buildConfigField("long", "PERMISSION_CHECK_INTERVAL_MS", "5_000L")
            buildConfigField("long", "WIDGET_REFRESH_INTERVAL_MS", "300_000L")
            buildConfigField("long", "WIDGET_MIN_REFRESH_INTERVAL_MS", "60_000L")
            buildConfigField("boolean", "LOGGING_ENABLED", "false")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.useJUnitPlatform() }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            (output as? VariantOutputImpl)?.outputFileName?.set(output.versionName.map { "foreground2-$it.apk" })
        }
    }
}

// Fail release builds up front with a readable message instead of AGP's signing error.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val problem = keystoreProblem
    doFirst { if (problem != null) throw GradleException(problem) }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
}

// detekt 1.23.x runs on Kotlin 2.0.21; keep the project's newer Kotlin off its classpath.
configurations.matching { it.name == "detekt" || it.name == "detektPlugins" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.detektKotlin.get())
        }
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    detektPlugins(project(":detekt-rules"))
}
