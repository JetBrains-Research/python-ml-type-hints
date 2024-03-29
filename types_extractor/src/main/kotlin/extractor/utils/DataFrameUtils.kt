package extractor.utils

import extractor.function.Function
import org.jetbrains.dataframe.DataFrame
import org.jetbrains.dataframe.add
import org.jetbrains.dataframe.plus
import org.jetbrains.dataframe.toDataFrameByProperties

fun addMeaningfulColumnsToFunctions(
    project: String,
    file: String,
    functions: List<Function>,
    avlTypes: List<String>
): DataFrame<Any?> {
    val df = functions.toDataFrameByProperties()
    return df.add {
        "author" { project }
        "file" { file }
        "repo" { project }
        "variables"<List<String>> { listOf() }
        "variableTypes"<List<String>> { listOf() }
        "avalTypes" { avlTypes }
        "argNamesLen" { (get("argNames") as List<*>).size }
        "argTypesLen" { (get("argTypes") as List<*>).size }
    }
}

fun addFunctionsToDf(
    project: String,
    file: String,
    functions: List<Function>,
    avlTypes: List<String>,
    oldDf: DataFrame<Any?>?
): DataFrame<Any?> {
    val newDf = addMeaningfulColumnsToFunctions(project, file, functions, avlTypes)

    return if (oldDf == null) newDf else oldDf + newDf
}
