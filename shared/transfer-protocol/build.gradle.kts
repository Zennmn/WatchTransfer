import java.security.MessageDigest

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

fun asciiSafePathKey(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(12)
}

val asciiTestClassesDir = providers.provider {
    File(
        System.getProperty("java.io.tmpdir"),
        "watchtransfer-gradle-test-classes/${asciiSafePathKey(rootDir.absolutePath)}/${project.name}/test"
    )
}

val syncTestClassesToAsciiPath by tasks.registering(Sync::class) {
    dependsOn("compileTestKotlin")
    from(layout.buildDirectory.dir("tmp/kotlin-classes/test"))
    into(asciiTestClassesDir)
}

tasks.test {
    useJUnit()
    dependsOn(syncTestClassesToAsciiPath)
    testClassesDirs = files(asciiTestClassesDir)
    classpath = files(asciiTestClassesDir) + classpath
}
