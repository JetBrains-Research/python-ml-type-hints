package plugin.typing

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

class PyGuessTypeProvider : PyTypeProviderBase() {

    override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
        val type: String = "str" // TypePredictor.predictDLTPy(param.containingFile, listOf(param.name!!)).next()
        return Ref.create(PyTypeParser.getTypeByName(param, type, context))
    }

    override fun getReturnType(callable: PyCallable, context: TypeEvalContext): Ref<PyType>? {
        return Ref.create(PyTypeParser.getTypeByName(callable, "str", context))
    }
}
