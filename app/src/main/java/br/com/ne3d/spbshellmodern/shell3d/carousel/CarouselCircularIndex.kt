package br.com.ne3d.spbshellmodern.shell3d.carousel

/** Circular index math for an infinite carousel.
 *
 * The logical index grows (or shrinks) without bounds while the user or the
 * autoplay keeps turning. The physical index is always a valid panel slot.
 * No navigation logic may clamp the logical index to a first/last card.
 */
object CarouselCircularIndex {
    /** Physical slot for any logical index. Always in `0 until panelCount`. */
    fun physicalIndex(logicalIndex: Long, panelCount: Int): Int {
        if (panelCount <= 0) return 0
        return Math.floorMod(logicalIndex, panelCount.toLong()).toInt()
    }

    fun physicalIndex(logicalIndex: Int, panelCount: Int): Int =
        physicalIndex(logicalIndex.toLong(), panelCount)

    fun next(logicalIndex: Long): Long = logicalIndex + 1L

    fun previous(logicalIndex: Long): Long = logicalIndex - 1L

    fun isValidPhysical(physicalIndex: Int, panelCount: Int): Boolean =
        panelCount > 0 && physicalIndex in 0 until panelCount
}
