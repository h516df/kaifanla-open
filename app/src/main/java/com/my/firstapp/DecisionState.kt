package com.my.firstapp

/** Screen-only state. Persisted meals, rules and imports remain owned by AppViewModel. */
data class DecisionState(
    val direct: Boolean = true,
    val shopId: Long? = null,
    val batch: Int = 0,
    val spinning: Boolean = false,
    val result: DecisionResult? = null,
) {
    fun changeMode(direct: Boolean) = if (spinning) this else copy(direct = direct, shopId = null, result = null)
    fun chooseShop(id: Long?) = if (spinning) this else copy(shopId = id, result = null)
    fun nextBatch() = if (spinning) this else copy(batch = batch + 1, result = null)
    fun mealSaved() = if (result !is DecisionResult.Meal) this else copy(shopId = null, batch = batch + 1, result = null)
    fun beginSpin() = if (spinning) this else copy(spinning = true, result = null)
    fun finishSpin(result: DecisionResult?) = if (!spinning) this else copy(spinning = false, result = result)
    fun cancelSpin() = copy(spinning = false)
    fun showResult(result: DecisionResult?) = if (spinning) this else copy(result = result)
    fun dismissResult() = copy(result = null)
    // Transient animation/results are deliberately not resurrected after process recreation.
    fun checkpoint() = listOf(if (direct) 1L else 0L, shopId ?: -1L, batch.toLong())
    companion object {
        fun restore(values: List<Long>) = DecisionState(values[0] == 1L, values[1].takeIf { it != -1L }, values[2].toInt())
    }
}

/** Freeze this at spin start: an animation must finish against the same candidates. */
data class DecisionCandidates(
    val shops: List<Shop>,
    val selectedShop: Shop?,
    val dishes: List<Dish>,
    val sample: List<Pair<Shop, Dish>>,
    val direct: Boolean,
) {
    val count: Int get() = when {
        direct -> sample.size
        selectedShop != null -> dishes.size
        else -> shops.size
    }

    fun result(index: Int): DecisionResult? {
        if (index !in 0 until count) return null
        return when {
            direct -> sample[index].let { DecisionResult.Meal(it.first, it.second) }
            selectedShop != null -> DecisionResult.Meal(selectedShop, dishes[index])
            else -> DecisionResult.Place(shops[index])
        }
    }
}

sealed interface DecisionResult {
    data class Place(val shop: Shop) : DecisionResult
    data class Meal(val shop: Shop, val dish: Dish) : DecisionResult
}
