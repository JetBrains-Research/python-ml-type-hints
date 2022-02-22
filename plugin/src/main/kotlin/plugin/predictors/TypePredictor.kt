@file:OptIn(ExperimentalTime::class)

package plugin.predictors

import data_preparation.TypeEmbedder
import data_preparation.createParameterDatapoints
import data_preparation.createReturnDatapoint
import extractor.function.Function
import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.ndarray.arrays.MutableFloatNDArray
import kotlin.time.ExperimentalTime

class TypePredictor {
    companion object {
        val model =
            KIEngine.loadModel(this::class.java.classLoader.getResourceAsStream("/models/model.onnx").readBytes())

        fun predictParameters(function: Function): Map<String, String> {
            model.resetProfiles()
            val types = createParameterDatapoints(function).map {
                it.toVector().asTensor(model.graph.availableInputs.first())
            }.map {
                model.predict(listOf(it))[model.graph.outputs.first().name] as KITensor
            }.map {
                (it.data as MutableFloatNDArray).argmax(1).array[0]
            }.map {
                TypeEmbedder.decode(it)
            }
            return function.argFullNames.zip(types).toMap()
        }

        fun predictReturnType(function: Function): String {
            model.resetProfiles()
            return createReturnDatapoint(function)
                .toVector()
                .asTensor(model.graph.availableInputs.first())
                .let { model.predict(listOf(it))[model.graph.outputs.first().name] as KITensor }
                .let { (it.data as MutableFloatNDArray).argmax(1).array[0] }
                .let { TypeEmbedder.decode(it) }
        }
    }
}
