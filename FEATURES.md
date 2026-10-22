# MonaySMP Core feature roadmap

This project is a clean-room implementation using the uploaded plugin only as a high-level behavior reference. It does not copy the original plugin's compiled classes or proprietary source.

## Foundation
- [x] New MonaySMP Core identity and Java 25/Paper build setup
- [x] Configurable prefix, admin reload/info command
- [x] Spawn/setspawn, homes, AFK, random teleport foundation
- [x] Vault-backed balance and pay commands
- [x] SQLite persistence foundation
- [ ] Automated build and server integration verification

## Systems to implement in stages
- [ ] Native Paper Dialog UI for Java clients, with compatible Bedrock fallbacks
- [ ] TPA requests, RTP queue, settings menu, private messages and ignore persistence
- [ ] Shards economy and transfers
- [ ] Auction House listings, expiry, purchase/refund flow and persistence
- [ ] Orders board, fulfillment and payouts
- [ ] Shop categories, sell/sell-hand/sell-all and worth lookup
- [ ] Teams, friends, duels, queues, FFA arenas and stats
- [ ] Crates/keys, player settings, playtime, ping and AFK rewards
- [ ] Scoreboard, tab integration, placeholders and permissions integrations
- [ ] Admin/moderation commands and migration/backup tools

Items are marked complete only when implemented; this list is not a claim of parity with UltimateDonutSmp2.
