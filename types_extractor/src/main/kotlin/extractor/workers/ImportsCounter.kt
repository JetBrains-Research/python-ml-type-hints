package extractor.workers

import extractor.utils.forEachProjectInDir
import extractor.utils.setupProject

class ImportsCounter {
    fun countResolvedImports(dirPath: String, envName: String): ResolutionResult {
        println("Counting imports for $dirPath")
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
            println(
                "In project $projectDir:" +
                        " resolved ${srcConfigurer.totalResolved - srcConfigurer.totalPreResolved} imports," +
                        " unresolved ${srcConfigurer.totalUnresolved}," +
                        " resolved before ${srcConfigurer.totalPreResolved}"
            )
        }

        /*File(dirPath).list().orEmpty().forEach {
            val project = ProjectUtil.openOrImport(Paths.get(dirPath, it), null, true)
            println("Extracting types from $it")
            if (project != null) {
                val projectManager = ProjectRootManager.getInstance(project)
                // Use the very first suggested conda environment
                val mySdkPath =
                    CondaEnvSdkFlavor.getInstance().suggestHomePaths(project.modules[0], UserDataHolderBase())
                        .filter { sdk -> sdk.contains("diploma") }
                        .take(1)[0]
                val sdkConfigurer = SdkConfigurer(project, projectManager)
                sdkConfigurer.setProjectSdk(mySdkPath)
                print(mySdkPath)

                val srcConfigurator = MyPythonSourceRootConfigurer()
                srcConfigurator.configureProject(
                    project,
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(dirPath, it))!!
                )
                srcConfigurator.countImports(project)
                totalPreResolved += srcConfigurator.totalPreResolved
                totalResolvedByUs += srcConfigurator.totalResolved - srcConfigurator.totalPreResolved
                totalUnresolved += srcConfigurator.totalUnresolved
                println(
                    "In project $dirPath:" +
                            " resolved ${srcConfigurator.totalResolved - srcConfigurator.totalPreResolved} imports," +
                            " unresolved ${srcConfigurator.totalUnresolved}," +
                            " resolved before ${srcConfigurator.totalPreResolved}"
                )
            }
        }*/

        val delta = System.currentTimeMillis() - time
        println("Time for executing: $delta")
        return ResolutionResult(totalPreResolved, totalUnresolved, totalResolvedByUs)
    }
}

data class ResolutionResult(val preResolved: Int, val unresolved: Int, val resolvedByUs: Int)