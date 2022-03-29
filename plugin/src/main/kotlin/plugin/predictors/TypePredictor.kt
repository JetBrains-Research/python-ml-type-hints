@file:OptIn(ExperimentalTime::class)

package plugin.predictors

import datapreparation.TypeEmbedder
import datapreparation.createParameterDatapoints
import datapreparation.createReturnDatapoint
import extractor.function.Function
import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.ndarray.arrays.MutableFloatNDArray
import kotlin.time.ExperimentalTime

class TypePredictor private constructor() {
    companion object {
        val model =
            KIEngine.loadModel(this::class.java.classLoader.getResourceAsStream("/models/model.onnx").readBytes())

        fun predictParameters(function: Function, topN: Int = 1): Map<String, List<String>> {
            model.resetProfiles()
            val types = createParameterDatapoints(function).map {
                it.toVector().asTensor(model.graph.availableInputs.first())
            }.map {
                model.predict(listOf(it))[model.graph.outputs.first().name] as KITensor
            }.map {
                (it.data as MutableFloatNDArray).topK(
                    axis = 1,
                    k = topN,
                    largest = true,
                    sorted = true).second.array.toArray().take(topN)
            }.map { predictions ->
                predictions.map { prediction -> TypeEmbedder.decode(prediction.toInt()) }
            }
            return function.argFullNames.zip(types).toMap()
        }

        fun predictReturnType(function: Function, topN: Int = 1): List<String> {
            model.resetProfiles()
            return createReturnDatapoint(function)
                .toVector()
                .asTensor(model.graph.availableInputs.first())
                .let { model.predict(listOf(it))[model.graph.outputs.first().name] as KITensor }
                .let {
                    (it.data as MutableFloatNDArray).topK(
                        axis = 1,
                        k = topN,
                        largest = true,
                        sorted = true).second.array.toArray().take(topN)
                }
                .map { TypeEmbedder.decode(it.toInt()) }
        }
    }
}
