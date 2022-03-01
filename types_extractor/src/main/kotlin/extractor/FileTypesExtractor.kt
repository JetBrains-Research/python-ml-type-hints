package extractor

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.statistics.modules
import extractor.function.Function
import extractor.function.FunctionExtractor
import extractor.function.Preprocessor
import krangl.DataFrame
import krangl.asDataFrame
import krangl.dataFrameOf
import java.io.File
import java.nio.file.Paths

// TODO needs refactoring
class FileTypesExtractor(val output: String) {
    fun extractTypesFromProject(projectPath: String, toInfer: Boolean): Map<String, TypedElements> {
        val types = mutableMapOf<String, TypedElements>()

        println("Extracting projects from $projectPath")

        var totalTime: Long = 0
        var totalFunctions: Long = 0
        File(projectPath).list().orEmpty().forEach {
            val project = ProjectUtil.openOrImport(Paths.get(projectPath, it), null, true)
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

                val sourceRootConfigurator = MyPythonSourceRootConfigurator()
                sourceRootConfigurator.configureProject(
                    project,
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(projectPath, it))!!
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
        }
        print("Total time for $totalFunctions functions is $totalTime")

        return types
    }

    fun countResolvedImports(projectPath: String): Triple<Int, Int, Int> {
        println("Counting imports for $projectPath")
        val time = System.currentTimeMillis()
        var totalPreResolved = 0
        var totalUnresolved = 0
        var totalResolvedByUs = 0
        File(projectPath).list().orEmpty().forEach {
            val project = ProjectUtil.openOrImport(Paths.get(projectPath, it), null, true)
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

                val srcConfigurator = MyPythonSourceRootConfigurator()
                srcConfigurator.configureProject(
                    project,
                    VirtualFileManager.getInstance().findFileByNioPath(Paths.get(projectPath, it))!!
                )
                srcConfigurator.countImports(project)
                totalPreResolved += srcConfigurator.totalPreResolved
                totalResolvedByUs += srcConfigurator.totalResolved - srcConfigurator.totalPreResolved
                totalUnresolved += srcConfigurator.totalUnresolved
                println(
                    "In project $projectPath:" +
                            " resolved ${srcConfigurator.totalResolved - srcConfigurator.totalPreResolved} imports," +
                            " unresolved ${srcConfigurator.totalUnresolved}," +
                            " resolved before ${srcConfigurator.totalPreResolved}"
                )
            }
        }

        val delta = System.currentTimeMillis() - time
        println("Time for executing: $delta")
        return Triple(totalPreResolved, totalUnresolved, totalResolvedByUs)
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
            } as List<String>)
            val return_type = "\"${function.returnType.replace('\"', '\'')}\""
            val return_expr = prettyPrintList(function.returnExpr)
            val return_descr = function.returnDescr.orEmpty()
            val lineno = function.lineNumber
            val args_occur = prettyPrintList(function.argOccurrences.map {
                if (it == null) return@map ""
                return@map it
            } as List<String>)
            val variables = prettyPrintList(listOf())
            val variables_types = prettyPrintList(listOf())
            val aval_types = prettyPrintList(avlTypes)
            val arg_names_len = function.argNames.size
            val arg_types_len = function.argTypes.size
            out.appendText("$author,$repo,$file,$has_type,$name,$docstring,$func_descr,$arg_names,$arg_types,$arg_descrs,$return_type,$return_expr,$return_descr,$lineno,$args_occur,$variables,$variables_types,$aval_types,$arg_names_len,$arg_types_len\n")
        }
    }

    fun buildDf(project: String, file: String, functions: List<Function>, avlTypes: List<String>) {
//         TODO
//            val df: DataFrame = functions.asDataFrame().addColumn("project") { project }.addColumn("file")
    }

    private fun prettyPrintList(list: List<String>): String {
        return list.map { it.replace('\"', '\'') }.joinToString(
            separator = ", ",
            prefix = "\"[",
            postfix = "]\"",
            transform = { "\'" + it + "\'" }
        )
    }

    fun printTypes(types: Map<String, TypedElements>, output: String) {
        val file = File(output)
        file.createNewFile()

        val writer = file.printWriter()
        writer.println("file;lineno;name;type;element")
        for (entry in types.entries) {
            for (elementInfo in entry.value.types) {
                writer.println(""""${elementInfo.file}";"${elementInfo.line}";"${elementInfo.name}";"${elementInfo.type}";"${elementInfo.elementType}"""")
            }
        }
        writer.flush()
    }
}

class TypedElements {
    var types: ArrayList<ElementInfo> = arrayListOf()

    fun addType(name: String, type: String, filePath: String, line: Int, elementType: ElementType) {
        types.add(ElementInfo(name, type, filePath, line, elementType))
    }

    override fun toString(): String {
        return types.toString()
    }
}

class ElementInfo(val name: String, val type: String, val file: String, val line: Int, val elementType: ElementType)

enum class ElementType(val type: Int) {
    FUNCTION(0),
    PARAMETER(1),
    VARIABLE(2),
    NONE(3),
}

class TypesExtractorElementVisitor(
    private val typedElements: TypedElements,
    private val filePath: String,
    private val toInfer: Boolean
) : PsiRecursiveElementVisitor() {

    override fun visitElement(element: PsiElement) {
        super.visitElement(element)

        if (element is PyAnnotationOwner &&
            //(element is PyTargetExpression ||
            (element is PyFunction || element is PyNamedParameter) &&
            element.parent !is PyImportElement
        ) {
            val context = TypeEvalContext.userInitiated(element.project, element.containingFile)
            val line = StringUtil.offsetToLineNumber(element.containingFile.text, element.textOffset)
            val type = when (element) {
                is PyFunction -> try {
                    element.getReturnStatementType(context)?.name
                } catch (e: Exception) {
                    "Any"
                }
                else -> try {
                    PythonDocumentationProvider.getTypeName(context.getType(element as PyTypedElement), context)
                } catch (e: Exception) {
                    "Any"
                }
            }
            val annotation = element.annotationValue
            val elementType: ElementType = when (element) {
                is PyParameter -> ElementType.PARAMETER
                is PyFunction -> ElementType.FUNCTION
                is PyTargetExpression -> ElementType.VARIABLE
                else -> ElementType.NONE
            }
            if (toInfer) {
                typedElements.addType(
                    (element as PyTypedElement).name.orEmpty(),
                    type ?: "Any", filePath,
                    line, elementType
                )
            } else {
                typedElements.addType(
                    (element as PyTypedElement).name.orEmpty(),
                    annotation ?: "Any", filePath,
                    line, elementType
                )
            }

            // TODO extract
            /*val annotation = (element as PyAnnotationOwner).annotationValue
            if (annotation != null) {
                when (element) {
                    is PyTargetExpression -> {

                        val assignmentStatement = element.parent as PyAssignmentStatement

                        val newAssignmentStatement = PyElementGenerator.getInstance(element.project)
                                .createFromText(LanguageLevel.PYTHON36,
                                        PyAssignmentStatement::class.java,
                                        element.text + " = " + assignmentStatement.assignedValue!!.text)
                        assignmentStatement.replace(newAssignmentStatement)
                    }
                    is PyFunction -> {

                    }
                    is PyNamedParameter -> {

                    }
                    else -> {}
                }
            }*/
        }
    }
}

