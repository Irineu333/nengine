plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":engine"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
