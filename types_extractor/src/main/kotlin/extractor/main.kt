package extractor

import com.github.ajalt.clikt.core.CliktCommand
import com.intellij.openapi.application.ApplicationStarter
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
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

    override fun run() {
        val types = FileTypesExtractor().extractTypesFromProject(input)
        println(types)
        exitProcess(0)
    }
}
