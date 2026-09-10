plugins {
    kotlin("jvm") version "1.9.24"
    application
}
repositories { mavenCentral() }
dependencies { implementation("org.json:json:20240303") }
kotlin { jvmToolchain(17) }
application { mainClass.set("com.example.injectionsites.InteropKt") }
