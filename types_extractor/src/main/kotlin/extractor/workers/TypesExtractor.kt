package extractor.workers

import extractor.function.Function
import extractor.function.FunctionExtractor
import extractor.function.Preprocessor
import extractor.utils.createFilesInDir
import extractor.utils.forEachProjectInDir
import extractor.utils.setupProject
import extractor.utils.traverseProject
import java.io.File
import java.nio.file.Paths

// TODO needs refactoring
class FileTypesExtractor(val output: String) {
    fun extractTypesFromProjectsInDir(dirPath: String, envName: String) {
        println("Extracting projects from $dirPath")

        var totalFunctions: Long = 0

        forEachProjectInDir(dirPath) { project, projectDir ->
            setupProject(project, envName, projectDir)

            val outFunctionsPath = Paths.get("processed_projects", "$projectDir.csv")
            val avlTypesPath = Paths.get("ext_visible_types", "$projectDir-avltypes.txt")

            createFilesInDir(output,
                outFunctionsPath.toString(),
                avlTypesPath.toString()
            )

            val outFunctions = outFunctionsPath.toFile()
            val outAvalTypes = avlTypesPath.toFile()

            traverseProject(project) { psi, filePath ->
                val functionExtractor = FunctionExtractor()
                val preprocessor = Preprocessor()

                psi.accept(functionExtractor)
                totalFunctions += functionExtractor.functions.size

                val extracted = functionExtractor.functions
                val avlTypes = functionExtractor.avalTypes

                printFunctions(
                    projectDir,
                    filePath,
                    extracted,
                    avlTypes.mapNotNull { preprocessor.processSentence(it) },
                    outFunctions
                )
                avlTypes.forEach { type ->
                    outAvalTypes.appendText(type + '\n')
                }
            }
        }

        /*File(dirPath).list().orEmpty().forEach {
            val project = ProjectUtil.openOrImport(Paths.get(dirPath, it), null, true)
            println("Extracting types from $it")
            if (project != null) {
                val projectManager = ProjectRootManager.getInstance(project)
                // Use the very first suggested conda environment
                val mySdkPaths =
                    CondaEnvSdkFlavor.getInstance().suggestHomePaths(project.modules[0], UserDataHolderBase())
                        .filter { sdk -> sdk.contains(envName) }
                if (mySdkPaths.isEmpty()) {
                    throw NoSuchElementException("no suitable SDK found")
                }
                val sdkConfigurer = SdkConfigurer(project, projectManager)
                sdkConfigurer.setProjectSdk(mySdkPaths[0])
                print(mySdkPaths[0])

                val sourceRootConfigurator = MyPythonSourceRootConfigurator()
                sourceRootConfigurator.configureProject(
                    project,
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(dirPath, it))!!
                )

                val outFunctions = Paths.get(output, "processed_projects", "$it.csv").toFile()
                outFunctions.createNewFile()
                outFunctions.writeText("author,repo,file,has_type,name,docstring,func_descr,arg_names,arg_types,arg_descrs,return_type,return_expr,return_descr,lineno,args_occur,variables,variables_types,aval_types,arg_names_len,arg_types_len\n")
                val outAvalTypes = Paths.get(output, "ext_visible_types", "$it-avltypes.txt").toFile()
                outAvalTypes.createNewFile()

                projectManager.contentRoots.forEach { root ->
                    VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
                        if (virtualFile.extension != "py" || virtualFile.canonicalPath == null) {
                            return@iterateChildrenRecursively true
                        }
                        val psi = PsiManager.getInstance(project)
                            .findFile(virtualFile) ?: return@iterateChildrenRecursively true
                        val typedElements = TypedElements()
                        val filePath = virtualFile.path

                        // this line is for inferring types
                        //psi.accept(TypesExtractorElementVisitor(typedElements, filePath, toInfer))

                        val time = System.currentTimeMillis()
                        val functionExtractor = FunctionExtractor()
                        val preprocessor = Preprocessor()

                        psi.accept(functionExtractor)
                        totalFunctions += functionExtractor.functions.size
                        totalTime += System.currentTimeMillis() - time


                        val extracted = functionExtractor.functions
                        val avlTypes = functionExtractor.avalTypes

                        types[filePath] = typedElements
                        printFunctions(
                            it,
                            filePath,
                            extracted,
                            avlTypes.mapNotNull { preprocessor.processSentence(it) },
                            outFunctions
                        )
                        avlTypes.forEach { type ->
                            outAvalTypes.appendText(type + '\n')
                        }
                        true
                    }
                }
            }
        }*/
//        print("Total time for $totalFunctions functions is $totalTime")
    }



    // TODO print as dataframe
    fun printFunctions(
        project: String,
        file: String,
        functions: List<Function>,
        avlTypes: List<String>,
        out: File
    ) {
        //println(Paths.get(output, "$project.csv").toString())
        val author = project
        val repo = project.split(Regex("__"))[0]
        functions.forEach { function ->
            val has_type = function.returnType.isNotEmpty().toString().capitalize()
            val name = function.name
            val docstring = function.docstring.orEmpty()
            val func_descr = function.fnDescription.orEmpty()
            val arg_names = prettyPrintList(function.argNames)
            val arg_types = prettyPrintList(function.argTypes)
            val arg_descrs = prettyPrintList(function.argDescriptions.map {
                if (it == null) return@map ""
                return@map it
            })
            val return_type = "\"${function.returnType.replace('\"', '\'')}\""
            val return_expr = prettyPrintList(function.returnExpr)
            val return_descr = function.returnDescr.orEmpty()
            val lineno = function.lineNumber
            val args_occur = prettyPrintList(function.argOccurrences.map {
                if (it == null) return@map ""
                return@map it
            })
            val variables = prettyPrintList(listOf())
            val variables_types = prettyPrintList(listOf())
            val aval_types = prettyPrintList(avlTypes)
            val arg_names_len = function.argNames.size
            val arg_types_len = function.argTypes.size
            out.appendText("$author,$repo,$file,$has_type,$name,$docstring,$func_descr,$arg_names,$arg_types,$arg_descrs,$return_type,$return_expr,$return_descr,$lineno,$args_occur,$variables,$variables_types,$aval_types,$arg_names_len,$arg_types_len\n")
        }
    }

    private fun prettyPrintList(list: List<String>): String {
        return list.map { it.replace('\"', '\'') }.joinToString(
            separator = ", ",
            prefix = "\"[",
            postfix = "]\"",
            transform = { "\'" + it + "\'" }
        )
    }
}





