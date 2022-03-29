
// Import variables from gradle.properties file
val pluginGroup: String by project
// `pluginName_` variable ends with `_` because of the collision with Kotlin magic getter in the `intellij` closure.
// Read more about the issue: https://github.com/JetBrains/intellij-platform-plugin-template/issues/29
val pluginName_: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project

val platformType: String by project
val platformVersion: String by project
val platformDownloadSources: String by project

group = rootProject.group
version = rootProject.version

// Configure project's dependencies
//repositories {
//    mavenCentral()
//    jcenter()
//    maven(url = "https://packages.jetbrains.team/maven/p/ki/maven")
//}
dependencies {
    implementation("com.lordcodes.turtle:turtle:0.5.0")
    implementation(project(":types_extractor"))
}
