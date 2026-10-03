package com.lanu.globaldonuksatisradari.data

/**
 * Conservative broad-inventory filter.
 *
 * The sales radar is an inventory of organizations / customer points, not a generic map layer.
 * Only categories that are clearly non-business infrastructure are removed. Schools, hospitals,
 * hotels, factories, offices, shops, farms and other potentially sellable customer points stay.
 */
object BusinessEntityEligibility {
    private val clearlyNonBusinessCategories = setOf(
        "park",
        "playground",
        "garden",
        "square",
        "monument",
        "memorial",
        "cemetery",
        "grave_yard",
        "bench",
        "shelter",
        "toilets",
        "drinking_water",
        "waste_basket",
        "recycling",
        "parking",
        "parking_entrance",
        "bicycle_parking",
        "motorcycle_parking",
        "public_bookcase",
        "fountain",
        "clock",
        "road",
        "intersection",
        "platform",
        "airport_gate",
        "tree",
        "village",
        "town",
        "city",
        "county",
        "country",
        "neighborhood",
    )

    fun keepForBusinessInventory(business: VerifiedBusiness): Boolean {
        val normalized = BusinessDeduplication.normalizeForComparison(
            business.category.orEmpty().replace(' ', '_'),
        )
        return normalized !in clearlyNonBusinessCategories
    }
}
