pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "error-handler-spring-boot"

include(
    "error-handler-spring-boot-autoconfigure",
    "error-handler-spring-boot-starter",
)
