package app.pokeplan.core.reward

/**
 * The version of the reward rules: what an event pays, the egg, candy, rank and transfer tables, the caps. The server records
 * it on every ledger row (`platform.reward_ledger.rules_version`), so an outcome can always be replayed with the rules that
 * made it. Bump it in the same change as any rule that changes what an event pays; `GoldenVectorTest` refuses to regenerate
 * `tools/server/golden.json` with new outcomes under the old version. Every source, sink and cap is listed in docs/economy.md.
 */
object RewardRules {
    const val VERSION: Int = 10
}
