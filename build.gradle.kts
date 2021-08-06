//import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("idea")
    id("org.jetbrains.kotlin.jvm") version "1.4.10" apply true
    id("org.jetbrains.intellij") version "1.1.3" apply true
    id("org.jetbrains.changelog") version "0.5.0"
//    id("io.gitlab.arturbosch.detekt") version "1.17.0" apply true
//    id("org.jlleitschuh.gradle.ktlint") version "9.4.0"
}

// Import variables from gradle.properties file
// `pluginName_` variable ends with `_` because of the collision with Kotlin magic getter in the `intellij` closure.
// Read more about the issue: https://github.com/JetBrains/intellij-platform-plugin-template/issues/29
group = "org.jetbrains.research.type-hints"
version = 1.0

fun properties(key: String) = project.findProperty(key).toString()

allprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("idea")
        plugin("org.jetbrains.intellij")
//        plugin("io.gitlab.arturbosch.detekt")
    }
    repositories {
        mavenCentral()
        jcenter()
        maven(url = "https://packages.jetbrains.team/maven/p/ki/maven")
    }
    dependencies {
//        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.17.0")
        implementation(platform("org.jetbrains.kotlin:kotlin-reflect:1.5.10"))
//        implementation("com.lordcodes.turtle:turtle:0.5.0")
//        implementation("com.github.holgerbrandl:krangl:0.16.1")
        implementation(kotlin("stdlib-jdk8"))
        implementation("io.kinference:inference:0.1.3")
    }

    // Configure gradle-intellij-plugin plugin.
    // Read more: https://github.com/JetBrains/gradle-intellij-plugin
    intellij {
        version.set(properties("platformVersion"))
        type.set(properties("platformType"))
        downloadSources.set(properties("platformDownloadSources").toBoolean())
        updateSinceUntilBuild.set(true)
        plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
    }

// Configure detekt plugin.
// Read more: https://detekt.github.io/detekt/kotlindsl.html
    /*detekt {
        config = files("./detekt-config.yml")
        buildUponDefaultConfig = true

        reports {
            html.enabled = false
            xml.enabled = false
            txt.enabled = false
        }
    }
*/
    tasks {
        // Set the compatibility versions to 1.8
        withType<JavaCompile> {
            sourceCompatibility = "11"
            targetCompatibility = "11"
        }
        listOf("compileKotlin", "compileTestKotlin").forEach {
            getByName<KotlinCompile>(it) {
                kotlinOptions.jvmTarget = "11"
            }
        }

//        withType<Detekt> {
//            jvmTarget = "11"
//        }

        withType<org.jetbrains.intellij.tasks.BuildSearchableOptionsTask>()
            .forEach { it.enabled = false }
    }
/*    val compileKotlin: KotlinCompile by tasks
    compileKotlin.kotlinOptions {
        jvmTarget = "1.8"
    }
    val compileTestKotlin: KotlinCompile by tasks
    compileTestKotlin.kotlinOptions {
        jvmTarget = "1.8"
    }*/
}