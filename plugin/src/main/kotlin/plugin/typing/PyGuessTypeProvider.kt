package plugin.typing

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext
import extractor.function.FunctionExtractor
import plugin.predictors.TypePredictor

class PyGuessTypeProvider : PyTypeProviderBase() {

    override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
        if (shouldNotInfer(param)) {
            return super.getParameterType(param, func, context)
        }
        val extractor = FunctionExtractor()
        func.accept(extractor)
        val function = extractor.functions.first()

        val type =
            TypePredictor.predictParameters(function)[param.name!!]?.first()
        return Ref.create(
            PyTypeParser.getTypeByName(
                param,
                if (type == null || type == "other") (context.getType(param)?.name ?: "Any") else type,
                context
            )
        )
    }

    private fun shouldNotInfer(param: PyNamedParameter): Boolean {
        return param.isPositionalContainer || param.isKeywordContainer || param.isKeywordOnly || param.isSelf
    }

    override fun getReturnType(callable: PyCallable, context: TypeEvalContext): Ref<PyType>? {
        if (callable !is PyFunction) {
            return super.getReturnType(callable, context)
        }

        val extractor = FunctionExtractor()
        callable.accept(extractor)
        val function = extractor.functions.first()

        val type = TypePredictor.predictReturnType(function).first()
        return Ref.create(
            PyTypeParser.getTypeByName(
                callable,
                if (type == "other") (context.getReturnType(callable)?.name ?: "Any") else type,
                context
            )
        )
    }
}
