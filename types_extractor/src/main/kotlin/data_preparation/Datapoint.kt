package data_preparation

import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.FloatNDArray
import io.kinference.ndarray.arrays.NDArray
import io.kinference.ndarray.arrays.tiled.FloatTiledArray

sealed interface Datapoint {
    val features: List<Feature>
    private val vectorLength: Int
        get() = features.sumOf { it.vectorLength } + features.size - 1
    val w2vLength: Int
        get() = 14
    val type: Int
    val numberOfTypes: Int
        get() = 1000

    fun datapointTypeVector(): FloatArray

    private fun vectorizedString(
        sentence: String,
        featureLength: Int,
        w2vModel: Map<String, FloatArray>
    ) = Array(featureLength) { FloatArray(w2vLength) { 0F } }.apply {
        sentence.split(' ').take(featureLength).forEachIndexed { index, word ->
            this[index] = w2vModel.getOrDefault(word, FloatArray(w2vLength) { 0F })
        }
    }

    fun toVector(): NDArray {
        val datapoint = Array(vectorLength) { FloatArray(w2vLength) { 0F } }

        val separator = FloatArray(w2vLength) { 1F }

        var position = 0
        for (feature in features) {
            when (feature.type) {
                FeatureType.DATAPOINT_TYPE -> {
                    datapoint[position++] = datapointTypeVector()
                }

                FeatureType.CODE, FeatureType.LANGUAGE -> {
                    val vectorizedFeature = vectorizedString(
                        when (feature) {
                            is StringFeature -> feature.value
                            is ListFeature -> ""
                        },
                        feature.vectorLength,
                        if (feature.type == FeatureType.CODE) W2V.codeModel else W2V.languageModel
                    )
                    for (word in vectorizedFeature)
                        datapoint[position++] = word
                }
                FeatureType.PADDING -> {
                    for (i in 0 until feature.vectorLength) {
                        datapoint[position++] = FloatArray(w2vLength) { 0F }
                    }
                }
            }
            if (position < datapoint.size) {
                datapoint[position++] = separator
            }
        }
        return FloatNDArray(FloatTiledArray(datapoint), Strides(intArrayOf(1, 55, 14)))
    }


    fun toBePredicted(): IntArray {
        val predictType = IntArray(numberOfTypes) { 0 }
        predictType[type] = 1
        return predictType
    }
}

class ReturnDatapoint(
    name: String,
    functionComment: String,
    returnComment: String,
    returnExpressions: List<String>,
    parameterNames: List<String>,
    override val type: Int
) : Datapoint {
    override val features = listOf(
        StringFeature("datapoint_type", FeatureType.DATAPOINT_TYPE, 1, ""),
        StringFeature("name", FeatureType.CODE, 6, name),
        StringFeature("function_comment", FeatureType.LANGUAGE, 15, functionComment),
        StringFeature("return_comment", FeatureType.LANGUAGE, 6, returnComment),
        ListFeature("return_expressions", FeatureType.CODE, 12, returnExpressions),
        ListFeature("parameter_names", FeatureType.CODE, 10, parameterNames)
    )

    override fun datapointTypeVector() =
        FloatArray(w2vLength) { 0F }.apply { this[1] = 1F }
}

class ParameterDatapoint(
    name: String,
    comment: String,
    override val type: Int
) : Datapoint {
    override val features = listOf(
        StringFeature("datapoint_type", FeatureType.DATAPOINT_TYPE, 1, ""),
        StringFeature("name", FeatureType.CODE, 6, name),
        StringFeature("comment", FeatureType.LANGUAGE, 15, comment),
        StringFeature("padding0", FeatureType.LANGUAGE, 6, ""),
        StringFeature("padding1", FeatureType.PADDING, 12, ""),
        StringFeature("padding2", FeatureType.PADDING, 10, "")
    )

    override fun datapointTypeVector() =
        FloatArray(w2vLength) { 0F }.apply { this[0] = 1F }
}

