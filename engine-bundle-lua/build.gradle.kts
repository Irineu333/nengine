plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":engine-bundle"))
    implementation(libs.kotlin.reflect)
    implementation(libs.luaj.jse)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
