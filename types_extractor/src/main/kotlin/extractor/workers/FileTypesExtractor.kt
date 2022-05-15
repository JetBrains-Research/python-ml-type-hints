package extractor.workers

import com.intellij.openapi.diagnostic.thisLogger
import extractor.function.FunctionExtractor
import extractor.function.Preprocessor
import extractor.utils.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.QuoteMode
import org.jetbrains.dataframe.DataFrame
import org.jetbrains.dataframe.io.writeCSV
import java.nio.file.Paths

class FileTypesExtractor(val output: String) {
    val logger = thisLogger()
    
    fun extractTypesFromProjectsInDir(dirPath: String, envName: String) {
        logger.warn("Extracting projects from $dirPath")

        var totalFunctions: Long = 0

        forEachProjectInDir(dirPath) { project, projectDir ->
            setupProject(project, envName, projectDir)

            val outFunctionsPath = Paths.get(output, "processed_projects", "${project.name}.csv")
            val avlTypesPath = Paths.get(output, "ext_visible_types", "${project.name}-avltypes.txt")

            createFiles(
                outFunctionsPath,
                avlTypesPath
            )

            val outAvalTypes = avlTypesPath.toFile()

            var functions: DataFrame<Any?>? = null
            traverseProject(project) { psi, filePath ->
                logger.warn("processing ${psi.name} from ${project.name}")
                val functionExtractor = FunctionExtractor(project, psi)
                val preprocessor = Preprocessor()

                psi.accept(functionExtractor)
                totalFunctions += functionExtractor.functions.size

                val extracted = functionExtractor.functions
                val avlTypes = functionExtractor.avalTypes

                functions = addFunctionsToDf(
                    projectDir,
                    filePath,
                    extracted,
                    avlTypes.map { preprocessor.processSentence(it) },
                    functions
                )

                avlTypes.forEach { type ->
                    outAvalTypes.appendText(type + '\n')
                }
            }
            functions?.writeCSV(outFunctionsPath.toString(), CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL))
        }
    }


}
