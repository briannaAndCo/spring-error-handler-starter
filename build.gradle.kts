plugins {
    `java-library`
    `maven-publish`
    signing
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    // TODO: Migrate to KSP once spring-boot-autoconfigure-processor and spring-boot-configuration-processor support it
    kotlin("kapt") version "2.0.21"
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.briannaandco"
version = property("version") as String

java {
    withSourcesJar()
    withJavadocJar()
}

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

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    kapt("org.springframework.boot:spring-boot-autoconfigure-processor")
    kapt("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
        exclude(module = "mockito-junit-jupiter")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("io.mockk:mockk-jvm:1.13.13")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "Error Handler Spring Boot Starter"
                description = "A comprehensive, opinionated error handling platform for Spring Boot applications"
                url = "https://github.com/briannaAndCo/spring-error-handler-starter"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        name = "briannaAndCo"
                        url = "https://github.com/briannaAndCo"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/briannaAndCo/spring-error-handler-starter.git"
                    developerConnection = "scm:git:ssh://github.com:briannaAndCo/spring-error-handler-starter.git"
                    url = "https://github.com/briannaAndCo/spring-error-handler-starter"
                }
            }
        }
    }
    repositories {
        maven {
            name = "OSSRH"
            url = uri(
                if (version.toString().endsWith("-SNAPSHOT"))
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                else
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            )
            credentials {
                username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    isRequired = !version.toString().endsWith("-SNAPSHOT")
    sign(publishing.publications["mavenJava"])
}
