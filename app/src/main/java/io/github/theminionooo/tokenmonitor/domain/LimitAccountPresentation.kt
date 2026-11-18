package io.github.theminionooo.tokenmonitor.domain

internal data class LimitAccountPresentation(
    val revision: Long,
    val providers: Map<String, LimitProviderPresentation>,
) {
    internal fun applyNames(accounts: List<LimitAccount>): List<LimitAccount> = accounts.map { account ->
        val name = providers[account.provider]?.labels?.get(account.accountKey)
        if (name.isNullOrEmpty()) account else account.copy(accountName = name)
    }

    /** Keep the dashboard's provider positions and use the desktop order within each provider. */
    internal fun applyOrder(accounts: List<LimitAccount>): List<LimitAccount> {
        val ordered = accounts.groupBy { it.provider }.mapNotNull { (provider, rows) ->
            val ranks = providers[provider]?.order.orEmpty()
            if (ranks.isEmpty() || rows.size < 2) null
            else provider to rows.sortedBy { ranks[it.accountKey] ?: Int.MAX_VALUE }.iterator()
        }.toMap()
        return accounts.map { ordered[it.provider]?.next() ?: it }
    }
}

internal data class LimitProviderPresentation(
    val labels: Map<String, String>,
    val order: Map<String, Int>,
)
