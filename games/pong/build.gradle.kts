plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(projects.engine)
    implementation(projects.engineSkiko)
    implementation(projects.engineBundle)
    implementation(projects.engineBundlePython)
}

application {
    mainClass.set("com.neoutils.engine.games.pong.MainKt")
}
