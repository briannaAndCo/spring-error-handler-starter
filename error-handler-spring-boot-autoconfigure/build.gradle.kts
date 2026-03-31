plugins {
    id("error-handler.kotlin-library")
    id("error-handler.publishing")
}

extra["pomName"] = "Error Handler Spring Boot Autoconfigure"
extra["pomDescription"] = "Auto-configuration for the Error Handler Spring Boot Starter"

dependencies {
    compileOnly(libs.spring.boot.starter)
    compileOnly(libs.spring.boot.starter.web)

    testImplementation(libs.spring.boot.starter.web)
}
