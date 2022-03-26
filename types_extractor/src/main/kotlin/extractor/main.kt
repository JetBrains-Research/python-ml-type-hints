package extractor

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.intellij.openapi.application.ApplicationStarter
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
    private val infer: String? by argument()
    private val envName: String by argument()

    override fun run() {
        val extractor = FileTypesExtractor(output)
        val inferrer = ProjectTypeInferrer(output)
        extractor.extractTypesFromProjectsInDir(input, envName)
//        val types = inferrer.inferTypes(input, envName)

//        inferrer.printTypes(types, output)
        exitProcess(0)
    }
}
