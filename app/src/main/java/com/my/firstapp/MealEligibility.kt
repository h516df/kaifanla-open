package com.my.firstapp

/** Pure policy shared by both decision modes. Callers supply the clock-derived cutoff. */
fun mealBlockReason(
    shop: Shop,
    dish: Dish,
    skip: SkipToday,
    budget: Double?,
    history: List<Record>,
    recentCutoff: Long?,
    maxWalkMinutes: Int? = null,
    allowUnknownPrice: Boolean = true,
    allowUnknownWalk: Boolean = true,
): String? {
    if (shop.id in skip.shopIds || dish.id in skip.dishIds) return "今天不想吃"
    val paid = dish.paid
    // Preserve existing behavior: a missing price is not silently treated as over budget.
    if (budget != null && paid != null && paid > budget) return "超预算"
    if (recentCutoff != null && history.any {
            it.time >= recentCutoff && it.shopName == shop.name && it.dishName == dish.name
        }) return "最近吃过"
    if (budget != null && paid == null && !allowUnknownPrice) return "价格待确认"
    if (maxWalkMinutes != null) {
        val minutes = shop.walkMinutes
        if (minutes == null && !allowUnknownWalk) return "路程未填写"
        if (minutes != null && minutes > maxWalkMinutes) return "超出步行时间"
    }
    return null
}
