plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
}

repositories {
    mavenCentral()
}

val libs = the<VersionCatalogsExtension>().named("libs")

val springBootVersion = providers.gradleProperty("springBootVersion")
    .orElse(libs.findVersion("spring-boot").get().requiredVersion)

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

kapt {
    correctErrorTypes = true
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    val bom = platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion.get()}")
    api(bom)
    kapt(bom)

    kapt(libs.findLibrary("spring-boot-autoconfigure-processor").get())
    kapt(libs.findLibrary("spring-boot-configuration-processor").get())

    testImplementation(libs.findLibrary("spring-boot-starter-test").get()) {
        exclude(module = "mockito-core")
        exclude(module = "mockito-junit-jupiter")
    }
    testImplementation(libs.findLibrary("mockk").get())
    testImplementation(libs.findLibrary("springmockk").get())
}

tasks.test {
    useJUnitPlatform()
}
