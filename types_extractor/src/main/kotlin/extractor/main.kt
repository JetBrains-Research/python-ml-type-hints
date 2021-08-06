package extractor

import com.github.ajalt.clikt.core.CliktCommand
import com.intellij.openapi.application.ApplicationStarter
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
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

    private val input: String by option("--input", help = "Path to input").required()
    private val output: String by option("--output", help = "Path to output").required()
    private val infer: String? by option("--toInfer")
    private val envName: String by option("--envName").required()

    override fun run() {
        val extractor = FileTypesExtractor(output)
        val inferrer = ProjectTypeInferrer(output)
        val toInfer = infer == "yes"
        extractor.extractTypesFromProjectsInDir(input, envName)
        val types = inferrer.inferTypes(input, toInfer, envName)

        inferrer.printTypes(types, output)
        exitProcess(0)
    }
}
