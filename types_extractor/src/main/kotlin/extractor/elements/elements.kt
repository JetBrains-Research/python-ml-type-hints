package extractor.elements

class ElementInfo(
    val name: String,
    val type: String,
    val annotation: String,
    val file: String,
    val line: Int,
    val elementType: ElementType
)

enum class ElementType(val type: Int) {
    FUNCTION(0),
    PARAMETER(1),
    VARIABLE(2),
    NONE(3),
}
