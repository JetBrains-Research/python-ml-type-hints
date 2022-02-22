package datapreparation

enum class FeatureType {
    DATAPOINT_TYPE,
    CODE,
    LANGUAGE,
    PADDING
}

sealed interface Feature {
    val name: String
    val type: FeatureType
    val vectorLength: Int
}

data class StringFeature(
    override val name: String,
    override val type: FeatureType,
    override val vectorLength: Int,
    val value: String
) : Feature

data class ListFeature(
    override val name: String,
    override val type: FeatureType,
    override val vectorLength: Int,
    val value: List<String>
) : Feature
