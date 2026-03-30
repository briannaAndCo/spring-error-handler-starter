pluginManagement {
    val springBootVersion: String by settings
    plugins {
        id("org.springframework.boot") version springBootVersion apply false
    }
}

rootProject.name = "error-handler-spring-boot-starter"
