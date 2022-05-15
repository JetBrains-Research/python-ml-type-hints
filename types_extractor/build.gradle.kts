group = rootProject.group
version = rootProject.version

dependencies {
//    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.ajalt:clikt:2.8.0")
    implementation("org.apache.commons:commons-lang3:3.0")
    implementation("edu.stanford.nlp:stanford-corenlp:4.2.0")
    implementation("edu.stanford.nlp:stanford-corenlp:4.2.0:models")
    implementation("org.slf4j:slf4j-api:+")
    api("io.kinference", "inference-core", "0.1.10")


//    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.10.0")
}

val platformType: String by project
val platformVersion: String by project
val platformDownloadSources: String by project

// See https://github.com/JetBrains/gradle-intellij-plugin/

open class IOCliTask : org.jetbrains.intellij.tasks.RunIdeTask() {
    // Name of the runner
    @get:Input
    val runner: String? by project

    // Path to the directory containing projects for the dataset
    val input: String? by project
    //Path to the output directory
    val output: String? by project
    val envName: String? by project
    // Path to a file containing split dataset
    val fileFilter: String? by project

    init {
        jvmArgs = listOf(
            "-Djava.awt.headless=true",
            "--add-exports",
            "java.base/jdk.internal.vm=ALL-UNNAMED",
            "-Djdk.module.illegalAccess.silent=true"
        )
        maxHeapSize = "20g"
        standardInput = System.`in`
        standardOutput = System.`out`
    }
}

tasks {
    register<IOCliTask>("runInferTypes") {
        dependsOn("buildPlugin")
        args = listOfNotNull(
            runner,
            input,
            output,
            envName,
            fileFilter ?: ""
        )
    }
}