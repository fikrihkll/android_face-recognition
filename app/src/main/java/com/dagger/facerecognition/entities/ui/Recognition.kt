package com.dagger.facerecognition.entities.ui

data class Recognition(
    val id: String,
    val title: String,
    var similarity: Double = 0.0,
    val vector: Array<FloatArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Recognition

        if (id != other.id) return false
        if (title != other.title) return false
        if (!vector.contentDeepEquals(other.vector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + vector.contentDeepHashCode()
        return result
    }
}
