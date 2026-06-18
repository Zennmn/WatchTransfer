import org.gradle.api.tasks.Sync
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.watchtransfer.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.watchtransfer.phone"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(project(":shared:transfer-protocol"))
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

val asciiDebugUnitTestClassesDir = providers.provider {
    File(
        System.getProperty("java.io.tmpdir"),
        "watchtransfer-gradle-test-classes/${asciiSafePathKey(rootDir.absolutePath)}/${project.name}/debugUnitTest"
    )
}

val asciiDebugUnitTestDepsDir = providers.provider {
    File(
        System.getProperty("java.io.tmpdir"),
        "watchtransfer-gradle-test-classes/${asciiSafePathKey(rootDir.absolutePath)}/${project.name}/debugUnitTestDeps"
    )
}

val syncDebugUnitTestClassesToAsciiPath by tasks.registering(Sync::class) {
    dependsOn(
        "compileDebugKotlin",
        "compileDebugJavaWithJavac",
        "compileDebugUnitTestKotlin",
        "compileDebugUnitTestJavaWithJavac"
    )
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debug"))
    from(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"))
    from(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"))
    from(layout.buildDirectory.dir("intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"))
    into(asciiDebugUnitTestClassesDir)
}

val syncDebugUnitTestDepsToAsciiPath by tasks.registering(Sync::class) {
    dependsOn("compileDebugKotlin", "compileDebugUnitTestKotlin")
    from(configurations["debugUnitTestRuntimeClasspath"])
    into(asciiDebugUnitTestDepsDir)
    eachFile {
        // Flatten: put all JARs directly into the target dir
        path = file.name
    }
}

afterEvaluate {
    tasks.named("testDebugUnitTest").configure {
        dependsOn(syncDebugUnitTestClassesToAsciiPath)
        dependsOn(syncDebugUnitTestDepsToAsciiPath)
        (this as org.gradle.api.tasks.testing.Test).apply {
            testClassesDirs = files(asciiDebugUnitTestClassesDir)
            val depsDir = asciiDebugUnitTestDepsDir.get()
            val depJars = if (depsDir.exists()) depsDir.listFiles()?.filter { it.extension == "jar" } ?: emptyList() else emptyList()
            classpath = files(asciiDebugUnitTestClassesDir) + files(depJars) + classpath.filter { file ->
                file.absolutePath.all { it.code < 128 }
            }
        }
    }
}
