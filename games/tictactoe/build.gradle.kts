plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

dependencies {
    implementation(projects.engine)
    implementation(projects.engineSkiko)
    implementation(projects.engineBundle)
    implementation(projects.engineBundleLua)
}

application {
    mainClass.set("com.neoutils.engine.games.tictactoe.MainKt")
}
