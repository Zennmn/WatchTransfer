import org.gradle.api.tasks.Sync
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.watchtransfer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.watchtransfer"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared:transfer-protocol"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

fun asciiSafePathKey(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(12)
}

fun configureAsciiUnitTestPath(variant: String) {
    val cap = variant.replaceFirstChar { it.uppercase() }
    val asciiUnitTestClassesDir = providers.provider {
        File(
            System.getProperty("java.io.tmpdir"),
            "watchtransfer-gradle-test-classes/${asciiSafePathKey(rootDir.absolutePath)}/${asciiSafePathKey(project.path)}/${variant}UnitTest"
        )
    }
    val syncClasses = tasks.register<Sync>("sync${cap}UnitTestClassesToAsciiPath") {
        dependsOn(
            "compile${cap}Kotlin",
            "compile${cap}JavaWithJavac",
            "compile${cap}UnitTestKotlin",
            "compile${cap}UnitTestJavaWithJavac"
        )
        from(layout.buildDirectory.dir("tmp/kotlin-classes/$variant"))
        from(layout.buildDirectory.dir("intermediates/javac/$variant/compile${cap}JavaWithJavac/classes"))
        from(layout.buildDirectory.dir("tmp/kotlin-classes/${variant}UnitTest"))
        from(layout.buildDirectory.dir("intermediates/javac/${variant}UnitTest/compile${cap}UnitTestJavaWithJavac/classes"))
        from(project(":shared:transfer-protocol").layout.buildDirectory.dir("libs"))
        into(asciiUnitTestClassesDir)
    }
    tasks.named("test${cap}UnitTest").configure {
        dependsOn(syncClasses)
        (this as org.gradle.api.tasks.testing.Test).apply {
            testClassesDirs = files(asciiUnitTestClassesDir)
            classpath = files(asciiUnitTestClassesDir) + files(asciiUnitTestClassesDir.map { File(it, "transfer-protocol.jar") }) + classpath
        }
    }
}

afterEvaluate {
    configureAsciiUnitTestPath("debug")
    configureAsciiUnitTestPath("release")
}
