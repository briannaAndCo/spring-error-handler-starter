plugins {
    `java-library`
    `maven-publish`
    signing
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.landonharter"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
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
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
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
                url = "https://github.com/landonharter/spring-error-handler-starter"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        name = "Landon Harter"
                        url = "https://github.com/landonharter"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/landonharter/spring-error-handler-starter.git"
                    developerConnection = "scm:git:ssh://github.com:landonharter/spring-error-handler-starter.git"
                    url = "https://github.com/landonharter/spring-error-handler-starter"
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
