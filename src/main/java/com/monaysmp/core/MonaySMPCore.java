package com.monaysmp.core;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class MonaySMPCore extends JavaPlugin {
    private Database database;
    private Economy economy;
    private final Set<UUID> afkPlayers = new HashSet<>();
    private final Set<UUID> rtpCooldown = new HashSet<>();
    private final Random random = new Random();

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            database = new Database(this);
        } catch (SQLException ex) {
            getLogger().severe("Could not open MonaySMP database: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupEconomy();
        getLogger().info("MonaySMP Core enabled. Economy provider: " + (economy == null ? "not found" : economy.getName()));
    }

    @Override public void onDisable() {
        if (database != null) try { database.close(); } catch (SQLException ex) {
            getLogger().warning("Could not close database cleanly: " + ex.getMessage());
        }
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider != null) economy = provider.getProvider();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String prefix() { return color(getConfig().getString("prefix", "&b&lMONAYSMP &8» &r")); }

    private void tell(CommandSender sender, String message) { sender.sendMessage(prefix() + color(message)); }

    private boolean playerOnly(CommandSender sender) {
        if (sender instanceof Player) return false;
        tell(sender, "&cThis command can only be used in-game.");
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("monaysmp.admin")) return true;
        tell(sender, getConfig().getString("settings.messages.no-permission", "&cYou do not have permission."));
        return false;
    }

    private String[] argsTail(String[] args, int start) {
        if (args.length <= start) return new String[0];
        return java.util.Arrays.copyOfRange(args, start, args.length);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        try {
            switch (cmd) {
                case "monaysmp" -> {
                    if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
                        tell(sender, "&7Core &f" + getPluginMeta().getVersion() + " &8| &7Paper &f" + Bukkit.getMinecraftVersion());
                        tell(sender, "&7Use &b/monaysmp reload &7to reload configuration.");
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("reload")) {
                        if (!admin(sender)) return true;
                        reloadConfig(); setupEconomy();
                        tell(sender, "&aConfiguration reloaded.");
                        return true;
                    }
                    tell(sender, "&7Usage: &b/monaysmp [info|reload]");
                    return true;
                }
                case "setspawn" -> {
                    if (!admin(sender) || playerOnly(sender)) return true;
                    Player p = (Player) sender;
                    Location loc = p.getLocation();
                    getConfig().set("spawn.world", loc.getWorld().getName());
                    getConfig().set("spawn.x", loc.getX()); getConfig().set("spawn.y", loc.getY());
                    getConfig().set("spawn.z", loc.getZ()); getConfig().set("spawn.yaw", loc.getYaw());
                    getConfig().set("spawn.pitch", loc.getPitch()); saveConfig();
                    tell(sender, "&aSpawn location saved.");
                    return true;
                }
                case "spawn" -> {
                    if (playerOnly(sender)) return true;
                    String worldName = getConfig().getString("spawn.world");
                    World world = worldName == null ? null : Bukkit.getWorld(worldName);
                    if (world == null) { tell(sender, "&cSpawn is not configured. Ask an admin to use /setspawn."); return true; }
                    Location loc = new Location(world, getConfig().getDouble("spawn.x"), getConfig().getDouble("spawn.y"),
                            getConfig().getDouble("spawn.z"), (float)getConfig().getDouble("spawn.yaw"),
                            (float)getConfig().getDouble("spawn.pitch"));
                    ((Player)sender).teleportAsync(loc);
                    tell(sender, "&aTeleporting to spawn...");
                    return true;
                }
                case "sethome" -> {
                    if (playerOnly(sender)) return true;
                    if (args.length != 1 || !validName(args[0])) { tell(sender, "&cUsage: /sethome <name> (3-16 letters, numbers, underscores)."); return true; }
                    Player p = (Player)sender;
                    var homes = database.listHomes(p.getUniqueId());
                    int limit = getConfig().getInt("settings.default-home-limit", 3);
                    if (!p.hasPermission("monaysmp.homes.unlimited") && !homes.containsKey(args[0].toLowerCase()) && homes.size() >= limit) {
                        tell(sender, "&cYou have reached your home limit (&f" + limit + "&c)."); return true;
                    }
                    database.setHome(p.getUniqueId(), args[0], p.getLocation());
                    tell(sender, "&aHome &f" + args[0] + " &asaved.");
                    return true;
                }
                case "home" -> {
                    if (playerOnly(sender)) return true;
                    if (args.length != 1) { tell(sender, "&cUsage: /home <name>. Use /homes to list homes."); return true; }
                    Player p = (Player)sender;
                    Location home = database.getHome(p.getUniqueId(), args[0], this);
                    if (home == null) { tell(sender, "&cThat home does not exist or its world is unavailable."); return true; }
                    p.teleportAsync(home); tell(sender, "&aTeleporting home..."); return true;
                }
                case "homes" -> {
                    if (playerOnly(sender)) return true;
                    var homes = database.listHomes(((Player)sender).getUniqueId());
                    tell(sender, homes.isEmpty() ? "&7You have no homes yet." : "&7Homes (&f" + homes.size() + "&7): &b" + String.join("&7, &b", homes.keySet()));
                    return true;
                }
                case "delhome" -> {
                    if (playerOnly(sender)) return true;
                    if (args.length != 1) { tell(sender, "&cUsage: /delhome <name>"); return true; }
                    tell(sender, database.deleteHome(((Player)sender).getUniqueId(), args[0]) ? "&aHome deleted." : "&cThat home does not exist.");
                    return true;
                }
                case "balance" -> {
                    if (playerOnly(sender)) return true;
                    if (economy == null) { tell(sender, getConfig().getString("settings.messages.no-economy")); return true; }
                    Player p = (Player)sender;
                    tell(sender, "&7Balance: &a" + economy.format(economy.getBalance(p)));
                    return true;
                }
                case "pay" -> {
                    if (playerOnly(sender)) return true;
                    if (economy == null) { tell(sender, getConfig().getString("settings.messages.no-economy")); return true; }
                    if (args.length != 2) { tell(sender, "&cUsage: /pay <player> <amount>"); return true; }
                    Player from = (Player)sender, to = Bukkit.getPlayerExact(args[0]);
                    if (to == null) { tell(sender, getConfig().getString("settings.messages.invalid-player")); return true; }
                    if (to.equals(from)) { tell(sender, "&cYou cannot pay yourself."); return true; }
                    double amount;
                    try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { amount = -1; }
                    if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000_000_000D) { tell(sender, getConfig().getString("settings.messages.invalid-amount")); return true; }
                    if (!economy.has(from, amount)) { tell(sender, getConfig().getString("settings.messages.insufficient-funds")); return true; }
                    var withdraw = economy.withdrawPlayer(from, amount);
                    if (!withdraw.transactionSuccess()) { tell(sender, "&cPayment failed: " + withdraw.errorMessage); return true; }
                    var deposit = economy.depositPlayer(to, amount);
                    if (!deposit.transactionSuccess()) {
                        economy.depositPlayer(from, amount);
                        tell(sender, "&cPayment failed; your money was returned."); return true;
                    }
                    tell(from, "&aPaid &f" + to.getName() + " &a" + economy.format(amount) + ".");
                    tell(to, "&aReceived &f" + economy.format(amount) + " &afrom &f" + from.getName() + "&a.");
                    return true;
                }
                case "afk" -> {
                    if (playerOnly(sender)) return true;
                    Player p = (Player)sender;
                    if (afkPlayers.remove(p.getUniqueId())) tell(sender, "&aYou are no longer AFK.");
                    else { afkPlayers.add(p.getUniqueId()); tell(sender, "&7You are now marked AFK."); }
                    return true;
                }
                case "rtp" -> {
                    if (playerOnly(sender)) return true;
                    Player p = (Player)sender;
                    if (rtpCooldown.contains(p.getUniqueId())) { tell(sender, "&cPlease wait before using RTP again."); return true; }
                    String worldName = getConfig().getString("settings.rtp.world", "world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) { tell(sender, "&cRTP world &f" + worldName + " &cis not loaded."); return true; }
                    int min = Math.max(0, getConfig().getInt("settings.rtp.min-radius", 500));
                    int max = Math.max(min + 1, getConfig().getInt("settings.rtp.max-radius", 5000));
                    int attempts = Math.max(1, getConfig().getInt("settings.rtp.attempts", 24));
                    for (int i = 0; i < attempts; i++) {
                        int radius = min + random.nextInt(max - min + 1);
                        int x = random.nextBoolean() ? radius : -radius;
                        int z = random.nextBoolean() ? random.nextInt(max * 2 + 1) - max : (random.nextInt(max * 2 + 1) - max);
                        if (Math.abs(z) < min) z = z < 0 ? -min : min;
                        int y = world.getHighestBlockYAt(x, z);
                        Block ground = world.getBlockAt(x, y, z);
                        Material type = ground.getType();
                        if (!type.isSolid() || type == Material.LAVA || type == Material.MAGMA_BLOCK
                                || !world.getBlockAt(x, y + 1, z).isPassable() || !world.getBlockAt(x, y + 2, z).isPassable()) continue;
                        Location target = new Location(world, x + 0.5, y + 1, z + 0.5);
                        p.teleportAsync(target);
                        rtpCooldown.add(p.getUniqueId());
                        long cooldown = Math.max(0, getConfig().getLong("settings.rtp.cooldown-seconds", 60));
                        Bukkit.getScheduler().runTaskLater(this, () -> rtpCooldown.remove(p.getUniqueId()), cooldown * 20L);
                        tell(sender, "&aFinding a safe random location...");
                        return true;
                    }
                    tell(sender, "&cCouldn't find a safe location. Try again.");
                    return true;
                }
                case "msg" -> {
                    if (playerOnly(sender)) return true;
                    if (args.length < 2) { tell(sender, "&cUsage: /msg <player> <message>"); return true; }
                    Player from = (Player)sender, to = Bukkit.getPlayerExact(args[0]);
                    if (to == null) { tell(sender, getConfig().getString("settings.messages.invalid-player")); return true; }
                    String message = String.join(" ", argsTail(args, 1));
                    to.sendMessage(color("&8[&bFrom " + from.getName() + "&8] &f" + message));
                    from.sendMessage(color("&8[&bTo " + to.getName() + "&8] &f" + message));
                    return true;
                }
                case "ignore" -> {
                    if (playerOnly(sender)) return true;
                    tell(sender, "&ePersistent ignore lists are planned; this command is not enabled yet.");
                    return true;
                }
                default -> { return false; }
            }
        } catch (SQLException ex) {
            getLogger().warning("Database operation failed: " + ex.getMessage());
            tell(sender, "&cA database error occurred. Check the server console.");
            return true;
        }
    }

    private boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }
}
