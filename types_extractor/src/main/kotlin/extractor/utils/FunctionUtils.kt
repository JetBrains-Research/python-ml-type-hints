package extractor.utils

import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.psi.PyFunction
import extractor.function.Function

fun checkEqual(function: PyFunction?, functionData: Function) =
    function != null && function.name == functionData.fullName &&
        StringUtil.offsetToLineNumber(function.containingFile.text,
            function.textOffset) == functionData.lineNumber &&
        function.parameterList.parameters
            .filter { !it.isSelf }
            .map { it.asNamed }
            .map { it?.name } == functionData.argFullNames