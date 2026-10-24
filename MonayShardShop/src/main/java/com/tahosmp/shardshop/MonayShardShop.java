package com.tahosmp.shardshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MonayShardShop extends JavaPlugin implements Listener {
    private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.LIGHT_PURPLE + "ShardShop" + ChatColor.GRAY + "] ";
    private final Map<Integer, ItemStack> products = new HashMap<>();
    private final Map<Integer, Integer> prices = new HashMap<>();
    private ItemStack currency;
    private String title;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadShop();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MonayShardShop enabled. No economy plugin required.");
    }

    private void loadShop() {
        products.clear();
        prices.clear();
        currency = getConfig().getItemStack("currency-item");
        title = color(getConfig().getString("shop-title", "&8ShardShop"));
        if (getConfig().isConfigurationSection("items")) {
            for (String key : getConfig().getConfigurationSection("items").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = getConfig().getItemStack("items." + key + ".item");
                    int price = getConfig().getInt("items." + key + ".price", 0);
                    if (slot >= 0 && slot < 54 && item != null && !item.getType().isAir() && price > 0) {
                        products.put(slot, item);
                        prices.put(slot, price);
                    }
                } catch (NumberFormatException ignored) {
                    getLogger().warning("Ignoring invalid shop slot: " + key);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("shardshop")) return false;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX + "Only players can open the shop.");
                return true;
            }
            openShop(player);
            return true;
        }
        if (!sender.hasPermission("monayshardshop.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (!(sender instanceof Player player) && !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(PREFIX + "This command must be run in-game.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "currency" -> {
                Player p = (Player) sender;
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    p.sendMessage(PREFIX + ChatColor.RED + "Hold your existing shard item in your main hand first.");
                    return true;
                }
                currency = hand.clone();
                currency.setAmount(1);
                getConfig().set("currency-item", currency);
                saveConfig();
                p.sendMessage(PREFIX + ChatColor.GREEN + "Existing shard item saved as the shop payment item.");
            }
            case "add" -> {
                Player p = (Player) sender;
                if (args.length < 2) {
                    p.sendMessage(PREFIX + ChatColor.YELLOW + "Hold the product and use /shardshop add <shard-cost>.");
                    return true;
                }
                if (currency == null || currency.getType().isAir()) {
                    p.sendMessage(PREFIX + ChatColor.RED + "Set your payment item first: hold a shard and use /shardshop currency.");
                    return true;
                }
                int price;
                try {
                    price = Integer.parseInt(args[1]);
                    if (price < 1) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    p.sendMessage(PREFIX + ChatColor.RED + "Shard cost must be a whole number greater than zero.");
                    return true;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    p.sendMessage(PREFIX + ChatColor.RED + "Hold the item you want to sell in the shop.");
                    return true;
                }
                int slot = firstFreeSlot();
                if (slot < 0) {
                    p.sendMessage(PREFIX + ChatColor.RED + "Shop is full (54 items). Remove an item first.");
                    return true;
                }
                ItemStack product = hand.clone();
                product.setAmount(1);
                products.put(slot, product);
                prices.put(slot, price);
                saveShop();
                p.sendMessage(PREFIX + ChatColor.GREEN + "Added " + product.getType().name() + " to shop slot " + slot + " for " + price + " shard(s).");
            }
            case "remove" -> {
                Player p = (Player) sender;
                if (args.length < 2) {
                    p.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /shardshop remove <slot>");
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[1]);
                    if (products.remove(slot) == null) {
                        p.sendMessage(PREFIX + ChatColor.RED + "There is no product in slot " + slot + ".");
                        return true;
                    }
                    prices.remove(slot);
                    saveShop();
                    p.sendMessage(PREFIX + ChatColor.GREEN + "Removed shop slot " + slot + ".");
                } catch (NumberFormatException ex) {
                    p.sendMessage(PREFIX + ChatColor.RED + "Slot must be a number from 0 to 53.");
                }
            }
            case "reload" -> {
                reloadConfig();
                loadShop();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Configuration reloaded.");
            }
            default -> sender.sendMessage(PREFIX + ChatColor.YELLOW + "Commands: /shardshop, /shardshop currency, /shardshop add <cost>, /shardshop remove <slot>, /shardshop reload");
        }
        return true;
    }

    private int firstFreeSlot() {
        for (int i = 0; i < 54; i++) if (!products.containsKey(i)) return i;
        return -1;
    }

    private void saveShop() {
        getConfig().set("items", null);
        for (Map.Entry<Integer, ItemStack> entry : products.entrySet()) {
            int slot = entry.getKey();
            getConfig().set("items." + slot + ".item", entry.getValue());
            getConfig().set("items." + slot + ".price", prices.getOrDefault(slot, 1));
        }
        saveConfig();
    }

    private void openShop(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 54, title);
        for (Map.Entry<Integer, ItemStack> entry : products.entrySet()) {
            ItemStack display = entry.getValue().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                        ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(color("&dPrice: &f" + prices.get(entry.getKey()) + " shard(s)"));
                lore.add(color("&7Click to purchase"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(entry.getKey(), display);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(title)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        ItemStack product = products.get(slot);
        Integer price = prices.get(slot);
        if (product == null || price == null) return;
        if (currency == null || currency.getType().isAir()) {
            player.sendMessage(PREFIX + ChatColor.RED + "The shop payment item hasn't been configured.");
            return;
        }
        int available = countCurrency(player, currency);
        if (available < price) {
            player.sendMessage(PREFIX + ChatColor.RED + "You need " + price + " shard(s); you have " + available + ".");
            return;
        }
        ItemStack purchase = product.clone();
        purchase.setAmount(1);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(purchase);
        if (!overflow.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Make room in your inventory before buying.");
            return;
        }
        removeCurrency(player, currency, price);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Purchased " + product.getType().name() + " for " + price + " shard(s)!");
    }

    private int countCurrency(Player player, ItemStack exemplar) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (matchesCurrency(item, exemplar)) count += item.getAmount();
        }
        return count;
    }

    private boolean matchesCurrency(ItemStack item, ItemStack exemplar) {
        return item != null && !item.getType().isAir() && item.isSimilar(exemplar);
    }

    private void removeCurrency(Player player, ItemStack exemplar, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!matchesCurrency(item, exemplar)) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
