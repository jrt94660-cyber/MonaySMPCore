# MonayShardShop

Standalone Paper plugin shop paid with your existing physical shard item. No Vault, Essentials economy, or virtual currency is used.

## Build

Open the `MonayShardShop` directory and run `gradle build`, or use the GitHub Actions workflow to build the JAR artifact.

## Install

1. Download `MonayShardShop-1.0.0.jar` from the workflow artifact.
2. Put it in the server's `plugins` folder and restart.
3. Hold one of the exact shard items already used by your server and run `/shardshop currency`.
4. Hold an item you want to sell and run `/shardshop add <shard-cost>`.
5. Players use `/shardshop` and click products to buy them.

## Commands

- `/shardshop` — open shop
- `/shardshop currency` — save the item currently held as payment item (admin)
- `/shardshop add <cost>` — add the item held in main hand as a product (admin)
- `/shardshop remove <slot>` — remove a product by its displayed inventory slot (admin)
- `/shardshop reload` — reload config (admin)

Admin permission: `monayshardshop.admin` (default OP).

The currency matcher uses Bukkit ItemStack similarity, including item metadata such as display name, lore, enchantments, and custom model data. Therefore set the currency using the exact item variant you want players to spend. If your shard plugin adds special persistent item data not preserved by Bukkit similarity, test that item on a staging server first.

Current implementation sells one item per purchase; each shop entry costs the configured number of shards. Purchases fail safely if the player's inventory is full or the exact payment item count is insufficient.
