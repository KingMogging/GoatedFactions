package gg.goatedcraft.gfactions.commands;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.ChunkWrapper;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // Added missing import
import java.util.List;
import java.util.stream.Collectors;


@SuppressWarnings("deprecation")
public class AdminFactionCommand implements CommandExecutor, TabCompleter {

    private final GFactionsPlugin plugin;

    public AdminFactionCommand(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("goatedfactions.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use admin faction commands.");
            return true;
        }

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            sendAdminHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "deletefaction":
            case "disband":
                if (args.length < 2) { sender.sendMessage(ChatColor.RED + "Usage: /fa deletefaction <factionName>"); return true; }
                handleDeleteFaction(sender, args[1]);
                break;
            case "addpower":
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /fa addpower <factionName> <amount>"); return true; }
                handleAddPower(sender, args[1], args[2]);
                break;
            case "setpower":
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /fa setpower <factionName> <amount>"); return true; }
                handleSetPower(sender, args[1], args[2]);
                break;
            case "unclaim":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /fa unclaim <all <factionName>|current [factionName]>");
                    return true;
                }
                String type = args[1].toLowerCase();
                String factionNameForUnclaim = (args.length > 2) ? args[2] : null;
                if (type.equals("all") && factionNameForUnclaim == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /fa unclaim all <factionName>");
                    return true;
                }
                handleUnclaim(sender, type, factionNameForUnclaim);
                break;
            case "reloadconfig":
                plugin.reloadConfig();
                plugin.loadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + plugin.getName() + " configuration reloaded.");
                break;
            case "dynmapreload":
            case "dynmaprefresh":
                if (plugin.DYNMAP_ENABLED && plugin.getDynmapManager() != null) {
                    if (!plugin.getDynmapManager().isEnabled()) {
                        sender.sendMessage(ChatColor.YELLOW + "Dynmap integration was not active. Attempting to reactivate...");
                        plugin.getDynmapManager().activate();
                    }
                    if (plugin.getDynmapManager().isEnabled()) {
                        plugin.getDynmapManager().updateAllFactionClaimsVisuals();
                        sender.sendMessage(ChatColor.GREEN + "Dynmap faction claims visuals reprocessed.");
                    } else { sender.sendMessage(ChatColor.RED + "Failed to activate Dynmap integration. Check console.");}
                } else { sender.sendMessage(ChatColor.RED + "Dynmap integration is disabled or not available.");}
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown admin command. Use /fa help.");
                break;
        }
        return true;
    }

    private void handleDeleteFaction(CommandSender sender, String factionName) {
        Faction faction = plugin.getFaction(factionName);
        if (faction == null) { sender.sendMessage(ChatColor.RED + "Faction '" + factionName + "' not found."); return; }
        plugin.disbandFactionInternal(faction, true);
        sender.sendMessage(ChatColor.GREEN + "Faction '" + faction.getName() + "' has been forcefully deleted.");
    }

    private void handleAddPower(CommandSender sender, String factionName, String amountStr) {
        Faction faction = plugin.getFaction(factionName);
        if (faction == null) { sender.sendMessage(ChatColor.RED + "Faction '" + factionName + "' not found."); return; }
        int amount;
        try { amount = Integer.parseInt(amountStr); }
        catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Invalid amount: '" + amountStr + "'. Must be a whole number."); return; }
        if (amount <= 0) { sender.sendMessage(ChatColor.RED + "Amount must be positive."); return; }

        int oldPower = faction.getCurrentPower();
        faction.addPower(amount);
        sender.sendMessage(ChatColor.GREEN + "Added " + (faction.getCurrentPower() - oldPower) + " power to '" + faction.getName() + "'. New: " + faction.getCurrentPower() + "/" + faction.getMaxPower());
    }

    private void handleSetPower(CommandSender sender, String factionName, String amountStr) {
        Faction faction = plugin.getFaction(factionName);
        if (faction == null) { sender.sendMessage(ChatColor.RED + "Faction '" + factionName + "' not found."); return; }
        int amount;
        try { amount = Integer.parseInt(amountStr); }
        catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Invalid amount: '" + amountStr + "'. Must be a whole number."); return; }
        if (amount < 0) { sender.sendMessage(ChatColor.RED + "Power cannot be negative."); return; }

        int oldPower = faction.getCurrentPower();
        faction.setCurrentPower(amount);
        sender.sendMessage(ChatColor.GREEN + "Set power of '" + faction.getName() + "' to " + faction.getCurrentPower() + "/" + faction.getMaxPower() + " (was " + oldPower + ")");
    }

    private void handleUnclaim(CommandSender sender, String type, @Nullable String factionNameForUnclaim) {
        if (type.equalsIgnoreCase("all")) {
            if (factionNameForUnclaim == null) { sender.sendMessage(ChatColor.RED + "Usage: /fa unclaim all <factionName>"); return; }
            Faction faction = plugin.getFaction(factionNameForUnclaim);
            if (faction == null) { sender.sendMessage(ChatColor.RED + "Faction '" + factionNameForUnclaim + "' not found."); return; }

            int initialClaimCount = faction.getClaimedChunks().size();
            if (initialClaimCount == 0) { sender.sendMessage(ChatColor.YELLOW + faction.getName() + " has no chunks to unclaim."); return; }

            new ArrayList<>(faction.getClaimedChunks()).forEach(plugin::unclaimChunkAdmin);
            sender.sendMessage(ChatColor.GREEN + "All " + initialClaimCount + " chunks for '" + faction.getName() + "' have been admin-unclaimed.");

        } else if (type.equalsIgnoreCase("current")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "The 'current' unclaim type can only be executed by a player.");
                return;
            }
            Chunk chunk = player.getLocation().getChunk();
            ChunkWrapper cw = new ChunkWrapper(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
            String ownerKey = plugin.getClaimedChunksMap().get(cw);

            if (ownerKey == null) { sender.sendMessage(ChatColor.RED + "The current chunk ("+cw.toStringShort()+") is not claimed."); return; }

            Faction chunkOwner = plugin.getFaction(ownerKey);
            if (chunkOwner == null) {
                plugin.unclaimChunkAdmin(cw);
                sender.sendMessage(ChatColor.YELLOW + "Chunk ("+cw.toStringShort()+") was claimed by an unknown/ghost faction. It has been forcibly unclaimed.");
                return;
            }

            if (factionNameForUnclaim != null && !chunkOwner.getNameKey().equalsIgnoreCase(factionNameForUnclaim.toLowerCase())) {
                sender.sendMessage(ChatColor.RED + "Current chunk ("+cw.toStringShort()+") is owned by '" + chunkOwner.getName() + "', not '" + factionNameForUnclaim + "'.");
                return;
            }

            plugin.unclaimChunkAdmin(cw);
            sender.sendMessage(ChatColor.GREEN + "Chunk (" + cw.toStringShort() + ") has been admin-unclaimed from '" + chunkOwner.getName() + "'.");

        } else {
            sender.sendMessage(ChatColor.RED + "Invalid unclaim type. Use 'all <factionName>' or 'current [factionName]'.");
        }
    }

    public static void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "--- GoatedFactions Admin Help ---");
        sender.sendMessage(ChatColor.GOLD + "/fa deletefaction <name>" + ChatColor.GRAY + " - Deletes faction (alias: /fa disband).");
        sender.sendMessage(ChatColor.GOLD + "/fa addpower <name> <amt>" + ChatColor.GRAY + " - Adds power (capped by max).");
        sender.sendMessage(ChatColor.GOLD + "/fa setpower <name> <amt>" + ChatColor.GRAY + " - Sets exact power (capped by max, min 0).");
        sender.sendMessage(ChatColor.GOLD + "/fa unclaim all <name>" + ChatColor.GRAY + " - Unclaims all land for specified faction.");
        sender.sendMessage(ChatColor.GOLD + "/fa unclaim current [name]" + ChatColor.GRAY + " - Unclaims current chunk (optionally verify owner).");
        sender.sendMessage(ChatColor.GOLD + "/fa reloadconfig" + ChatColor.GRAY + " - Reloads GoatedFactions config.yml.");
        sender.sendMessage(ChatColor.GOLD + "/fa dynmapreload" + ChatColor.GRAY + " - Reprocesses all faction claims for Dynmap (alias: /fa dynmaprefresh).");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("goatedfactions.admin")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "deletefaction", "disband", "addpower", "setpower", "unclaim", "reloadconfig", "dynmapreload", "dynmaprefresh");
            for (String sub : subCommands) {
                if (sub.startsWith(currentArg)) completions.add(sub);
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (Arrays.asList("deletefaction", "disband", "addpower", "setpower").contains(subCmd)) {
                plugin.getFactionsByNameKey().values().stream()
                        .filter(f -> f.getName().toLowerCase().startsWith(currentArg))
                        .forEach(f -> completions.add(f.getName()));
            } else if (subCmd.equals("unclaim")) {
                List<String> types = Arrays.asList("all", "current");
                for (String type : types) {
                    if (type.startsWith(currentArg)) completions.add(type);
                }
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            // String secondArg = args[1].toLowerCase(); // Not strictly needed for this completion logic

            if (subCmd.equals("unclaim") && (args[1].equalsIgnoreCase("all") || args[1].equalsIgnoreCase("current"))) {
                plugin.getFactionsByNameKey().values().stream()
                        .filter(f -> f.getName().toLowerCase().startsWith(currentArg))
                        .forEach(f -> completions.add(f.getName()));
            } else if (Arrays.asList("addpower", "setpower").contains(subCmd)) {
                if ("<amount>".startsWith(currentArg)) {
                    completions.add("<amount>");
                }
            }
        }
        return completions.stream().distinct().collect(Collectors.toList());
    }
}