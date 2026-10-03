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
        // Map / transport infrastructure and temporary events are not customer businesses.
        // Mirrors the intent of Foursquare OS Places' documented non-commercial filter.
        "airport_gate",
        "bus_line",
        "intersection",
        "island",
        "line",
        "moving_target",
        "plane",
        "platform",
        "polling_place",
        "road",
        "taxi",
        "train",
        "tree",
        "village",
        "town",
        "city",
        "county",
        "country",
        "state",
        "states_and_municipalities",
        "neighborhood",
        "event",
        "conference",
        "convention",
        "festival",
        "beer_festival",
        "music_festival",
        "entertainment_event",
        "sporting_event",
        "other_event",
        "parade",
        "street_fair",
        "street_food_gathering",
        "trade_fair",
        "christmas_market",
        "stoop_sale",
        "hotel_pool",
        "marketplace",
    )

    fun keepForBusinessInventory(business: VerifiedBusiness): Boolean =
        keepCategory(business.category)

    fun keepCategory(category: String?): Boolean {
        val normalized = BusinessDeduplication.normalizeForComparison(
            category.orEmpty().replace(' ', '_'),
        )
        return normalized.isBlank() || normalized !in clearlyNonBusinessCategories
    }
}
