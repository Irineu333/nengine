plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":engine-bundle"))
    implementation(libs.graalvm.polyglot)
    implementation(libs.graalvm.python)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}
