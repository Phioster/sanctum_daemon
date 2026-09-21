package org.phioster.sanctumd.ui

import org.phioster.sanctumd.model.DashCard
import org.phioster.sanctumd.model.DashTab

/**
 * The dashboard's list edits as pure functions.
 *
 * They used to sit inside [DashboardViewModel], tangled with DataStore writes, which made
 * the rearrange/edit rules (the part with the off-by-one risks) impossible to test.
 * The view model now only persists whatever these return.
 */

/** Swaps two entries; null when either index is out of bounds (nothing should move). */
internal fun <T> List<T>.swapOrNull(index: Int, target: Int): List<T>? {
    if (index < 0 || target < 0 || index >= size || target >= size) return null
    return toMutableList().also { it[index] = it[target]; it[target] = this[index] }
}

/** Moved tab list plus the tab's new index; null when it cannot move that way. */
internal fun List<DashTab>.movedTab(tabId: String, direction: Int): Pair<List<DashTab>, Int>? {
    val idx = indexOfFirst { it.id == tabId }
    val target = idx + direction
    val moved = swapOrNull(idx, target) ?: return null
    return moved to target
}

/** Moves a card inside its tab; returns the list unchanged when it cannot move. */
internal fun List<DashTab>.movedCard(tabId: String, cardId: String, direction: Int): List<DashTab> =
    map { tab ->
        if (tab.id != tabId) return@map tab
        val idx = tab.cards.indexOfFirst { it.id == cardId }
        val cards = tab.cards.swapOrNull(idx, idx + direction) ?: return@map tab
        tab.copy(cards = cards)
    }

/** Applies the card editor's values; the entry count is clamped to what the cards render. */
internal fun List<DashTab>.withCardUpdated(
    tabId: String,
    cardId: String,
    title: String,
    count: Int,
    accent: Long,
    icon: String,
    posterSize: String,
    background: Boolean,
    theme: String,
    density: String,
): List<DashTab> = map { tab ->
    if (tab.id != tabId) tab
    else tab.copy(cards = tab.cards.map { card ->
        if (card.id != cardId) card
        else card.copy(
            title = title.trim(),
            count = count.coerceIn(MIN_CARD_ENTRIES, MAX_CARD_ENTRIES),
            accent = accent,
            icon = icon,
            posterSize = posterSize,
            background = background,
            theme = theme,
            density = density,
        )
    })
}

internal fun List<DashTab>.withCardRemoved(tabId: String, cardId: String): List<DashTab> =
    map { tab ->
        if (tab.id != tabId) tab else tab.copy(cards = tab.cards.filterNot { it.id == cardId })
    }

internal fun List<DashTab>.withCardAdded(tabId: String, card: DashCard): List<DashTab> =
    map { tab -> if (tab.id != tabId) tab else tab.copy(cards = tab.cards + card) }

internal const val MIN_CARD_ENTRIES = 3
internal const val MAX_CARD_ENTRIES = 20
