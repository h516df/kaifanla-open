package com.my.firstapp

/** Public release routing only. Never put GitHub tokens or private signing material here. */
data class MealUpdateChannel(
    val repository: String,
    val certificateSha256: String,
    val publicDistributionApproved: Boolean,
) {
    val ready: Boolean get() = publicDistributionApproved &&
        repository.matches(Regex("h516df/[A-Za-z0-9][A-Za-z0-9_.-]*")) &&
        repository !in setOf("h516df/jintian-chishenme", "h516df/kaifanla") && !repository.contains("..") &&
        certificateSha256.matches(Regex("[a-f0-9]{64}"))

    /** No URL is exposed until the public destination and production certificate are confirmed. */
    val apiUrl: String? get() = if (ready) "https://api.github.com/repos/$repository" else null

    companion object {
        const val PRODUCTION_PACKAGE = "com.jintianchishenme.app"
        // Deliberately disabled: source repository is private, public distribution is not configured.
        val Pending = MealUpdateChannel("", "", false)
    }
}
