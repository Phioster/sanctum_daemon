package org.phioster.sanctumd.model

/**
 * How a title can be watched on a service: included in a subscription, rented, or bought.
 *
 * The order is the ranking. A provider that offers a title on all three is shown as [STREAM],
 * because that is the offer that costs nothing extra.
 */
enum class WatchProviderKind(val label: String) {
    STREAM("stream"),
    RENT("rent"),
    BUY("buy"),
}

/**
 * One streaming service a title is available on, in one country.
 *
 * TMDB (via Seerr's proxy) reports availability per region, so a provider only ever means
 * anything together with the region it was read for, see [WatchAvailability.region].
 */
data class WatchProvider(
    val id: Int,
    val name: String,
    /** Absolute TMDB logo URL; "" when TMDB has no logo for the provider. */
    val logoUrl: String,
    val kind: WatchProviderKind,
)

/**
 * Where a title streams in one region. Empty [providers] means "nowhere", which is what hides
 * the whole section rather than showing an empty row.
 *
 * [link] is TMDB's own JustWatch page for this title and region; it is what a provider logo
 * opens, since TMDB gives no per-provider deep link.
 */
data class WatchAvailability(
    val region: String,
    val providers: List<WatchProvider> = emptyList(),
    val link: String = "",
) {
    val isEmpty: Boolean get() = providers.isEmpty()

    companion object {
        val NONE = WatchAvailability(region = "")
    }
}
