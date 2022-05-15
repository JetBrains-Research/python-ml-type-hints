@file:OptIn(ExperimentalTime::class)

package plugin.predictors

import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.python.psi.PyFunction
import datapreparation.TypeEmbedder
import datapreparation.createParameterDatapoints
import datapreparation.createReturnDatapoint
import extractor.function.FunctionExtractor
import extractor.utils.checkEqual
import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.ndarray.arrays.MutableFloatNDArray
import kotlin.time.ExperimentalTime
import extractor.function.Function
import plugin.utils.timed
import kotlin.math.log

class KInferencePredictor : TypePredictor {
    private val model =
        KIEngine.loadModel(this::class.java.classLoader.getResourceAsStream("models/model1.onnx").readBytes())
    private val logger = thisLogger()

    override fun predictParameters(function: PyFunction, topN: Int): Map<String, List<Pair<String, Double>>> {
        model.resetProfiles()
        val functionData = extractData(function)


        return timed(logger, "Prediction of parameters for function ${functionData.fullName}") {
            val types = createParameterDatapoints(functionData).map {
                it.toVector().asTensor(model.graph.availableInputs.first())
            }.map {
                model.predict(listOf(it))[model.graph.outputs.first().name] as KITensor
            }.map {
                (it.data as MutableFloatNDArray).topK(
                    axis = 1, k = topN, largest = true, sorted = true
                )
            }.map {
                it.second.array.toArray().zip(it.first.array.toArray().toTypedArray()) { type, prob -> type to prob }
            }.map { predictions ->
                predictions.map { prediction ->
                    TypeEmbedder.decode(prediction.first.toInt()) to prediction.second.toDouble()
                }
            }
            functionData.argFullNames.zip(types).toMap()
        }
    }

    override fun predictReturnType(function: PyFunction, topN: Int): List<Pair<String, Double>> {
        model.resetProfiles()
        val functionData = extractData(function)
        return timed(logger, "Prediction  for function ${functionData.fullName}") {
            createReturnDatapoint(functionData).toVector().asTensor(model.graph.availableInputs.first())
                .let { model.predict(listOf(it))[model.graph.outputs.first().name] as KITensor }.let {
                    (it.data as MutableFloatNDArray).topK(
                        axis = 1, k = topN, largest = true, sorted = true
                    )
                }.let { it.second.array.toArray().zip(it.first.array.toArray().toTypedArray()) { type, prob -> type to prob}.take(topN)
                }.map { TypeEmbedder.decode(it.first.toInt()) to it.second.toDouble() }
        }
    }


    private fun extractData(function: PyFunction): Function {
        return timed(logger, "Extracting function ${function.name}") {
            val extractor = FunctionExtractor(function.project, function.containingFile)
            function.accept(extractor)
            val functionData = extractor.functions.first { checkEqual(function, it) }
            functionData
        }
    }
}
