group = rootProject.group
version = rootProject.version

dependencies {
//    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.ajalt:clikt:2.8.0")
    implementation("org.apache.commons:commons-lang3:3.0")
    implementation("edu.stanford.nlp:stanford-corenlp:4.2.0")
    implementation("edu.stanford.nlp:stanford-corenlp:4.2.0:models")
//    implementation("intoxicant.analytics:coreNlpExtensions:1.0.0")
//    implementation("org.apache.lucene:lucene-core:7.0.0")
    implementation("org.slf4j:slf4j-api:+")
    implementation("com.github.holgerbrandl:krangl:0.16.2")


//    testImplementation("junit:junit:4.11")
//    testImplementation(kotlin("test-junit"))

//    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.10.0")
}

val platformType: String by project
val platformVersion: String by project
val platformDownloadSources: String by project

// See https://github.com/JetBrains/gradle-intellij-plugin/


tasks {
    runIde {
        val input: String? by project
        val output: String? by project
        val toInfer: String? by project
        val envName: String? by project
        args = listOfNotNull("inferTypes", "--input", input, "--output", output, "--toInfer", toInfer, "--envName", envName)
        jvmArgs = listOf("-Djava.awt.headless=true", "--add-exports", "java.base/jdk.internal.vm=ALL-UNNAMED")
        maxHeapSize = "20g"
    }
    register("inferTypes") {
        dependsOn(runIde)
    }
}
