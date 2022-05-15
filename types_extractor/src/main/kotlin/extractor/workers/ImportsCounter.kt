package extractor.workers

import com.intellij.openapi.diagnostic.thisLogger
import extractor.utils.forEachProjectInDir
import extractor.utils.setupProject

class ImportsCounter {
    val logger = thisLogger()

    fun countResolvedImports(dirPath: String, envName: String): ResolutionResult {
        logger.warn("Counting imports for $dirPath")
        val time = System.currentTimeMillis()
        var totalPreResolved = 0
        var totalUnresolved = 0
        var totalResolvedByUs = 0

        forEachProjectInDir(dirPath) { project, projectDir ->
            val (_, srcConfigurer) = setupProject(project, envName, projectDir)
            srcConfigurer.countImports(project)
            totalPreResolved += srcConfigurer.totalPreResolved
            totalResolvedByUs += srcConfigurer.totalResolved - srcConfigurer.totalPreResolved
            totalUnresolved += srcConfigurer.totalUnresolved
            logger.warn(
                "In project $projectDir:" +
                    " resolved ${srcConfigurer.totalResolved - srcConfigurer.totalPreResolved} imports," +
                    " unresolved ${srcConfigurer.totalUnresolved}," +
                    " resolved before ${srcConfigurer.totalPreResolved}"
            )
        }

        val delta = System.currentTimeMillis() - time
        logger.warn("Time for executing: $delta")
        return ResolutionResult(totalPreResolved, totalUnresolved, totalResolvedByUs)
    }
}

data class ResolutionResult(val preResolved: Int, val unresolved: Int, val resolvedByUs: Int)
