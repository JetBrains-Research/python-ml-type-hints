package extractor.function

data class Function(
    val name: String,
    val docstring: String,
    val fnDescription: String,
    val argNames: List<String>,
    val argTypes: List<String>,
    val argDescriptions: List<String>,
    val argOccurrences: List<String>,
    val returnType: String,
    val returnExpr: List<String>,
    val returnDescr: String,
    val lineNumber: Int,
    val usages: Collection<String>,
    val fullName: String,
    val argFullNames: List<String>,
    val returnTypePredicted: String,
    val argsTypesPredicted: List<String>
)
