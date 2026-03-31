plugins {
    id("error-handler.kotlin-library")
    id("error-handler.publishing")
}

extra["pomName"] = "Error Handler Spring Boot Starter"
extra["pomDescription"] = "A comprehensive, opinionated error handling platform for Spring Boot applications"

dependencies {
    api(project(":error-handler-spring-boot-autoconfigure"))
}
