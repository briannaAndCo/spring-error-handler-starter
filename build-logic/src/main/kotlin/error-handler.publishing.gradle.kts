plugins {
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = project.extra.has("pomName").let {
                    if (it) project.extra["pomName"] as String else project.name
                }
                description = project.extra.has("pomDescription").let {
                    if (it) project.extra["pomDescription"] as String
                    else "A comprehensive, opinionated error handling platform for Spring Boot applications"
                }
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
                username = providers.gradleProperty("ossrhUsername")
                    .orElse(providers.environmentVariable("OSSRH_USERNAME")).orNull
                password = providers.gradleProperty("ossrhPassword")
                    .orElse(providers.environmentVariable("OSSRH_PASSWORD")).orNull
            }
        }
    }
}

signing {
    isRequired = !version.toString().endsWith("-SNAPSHOT")
    sign(publishing.publications["mavenJava"])
}
