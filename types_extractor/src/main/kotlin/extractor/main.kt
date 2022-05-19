package extractor

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.intellij.openapi.application.ApplicationStarter
import extractor.utils.getDatasetFrom
import extractor.workers.FileTypesExtractor
import extractor.workers.ProjectTypeInferrer
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {

    override fun getCommandName(): String = "inferTypes"

    override fun main(args: Array<out String>) {
        println(args.joinToString(" "))
        TypesExtractor().main(args.slice(1 until args.size))
    }
}

class TypesExtractor : CliktCommand() {

    private val input: String by argument(help = "Path to input")
    private val output: String by argument(help = "Path to output")
    private val envName: String by argument()
    private val fileFilter: String by argument()

    override fun run() {
//        val extractor = FileTypesExtractor(output)
        val neededFiles = if (fileFilter != "") getDatasetFrom(fileFilter, "test") else null
        val inferrer = ProjectTypeInferrer(output, neededFiles?.toSet())
//        extractor.extractTypesFromProjectsInDir(input, envName)
        inferrer.inferTypes(input, envName)
        exitProcess(0)
    }
}
