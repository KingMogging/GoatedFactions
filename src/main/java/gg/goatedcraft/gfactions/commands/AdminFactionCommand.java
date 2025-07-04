package gg.goatedcraft.gfactions.commands;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.ChunkWrapper;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
            case "setclaimlimit": // New command
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /fa setclaimlimit <factionName> <limit|-1 for default>"); return true; }
                handleSetClaimLimit(sender, args[1], args[2]);
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
            case "vault":
                if (!plugin.VAULT_SYSTEM_ENABLED) {
                    sender.sendMessage(ChatColor.RED + "The faction vault system is currently disabled.");
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be run by a player to open a vault.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /fa vault <factionName>");
                    return true;
                }
                handleAdminVault((Player) sender, args[1]);
                break;
            case "reloadconfig":
                plugin.reloadConfig();
                plugin.loadPluginConfig();

                plugin.startPowerRegeneration();

                BukkitTask currentPowerDecayTask = plugin.getPowerDecayTask();
                if (plugin.POWER_DECAY_ENABLED) {
                    plugin.startPowerDecayTask();
                } else if (currentPowerDecayTask != null && !currentPowerDecayTask.isCancelled()) {
                    currentPowerDecayTask.cancel();
                }
                sender.sendMessage(ChatColor.GREEN + plugin.getName() + " configuration reloaded and applied. Tasks depending on config have been reset.");
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
                    } else {
                        sender.sendMessage(ChatColor.RED + "Failed to activate Dynmap integration. Check console.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Dynmap integration is disabled or not available.");
                }
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

        int oldPower = faction.getCurrentPower();
        faction.addPower(amount);
        plugin.updateFactionActivity(faction.getNameKey());
        plugin.saveFactionsData();
        sender.sendMessage(ChatColor.GREEN + "Modified power for '" + faction.getName() + "'. Old: " + oldPower + ", New: " + faction.getCurrentPower() + "/" + faction.getMaxPowerCalculated(plugin));
        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionAppearance(faction);
        }
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
        plugin.updateFactionActivity(faction.getNameKey());
        plugin.saveFactionsData();
        sender.sendMessage(ChatColor.GREEN + "Set power of '" + faction.getName() + "' to " + faction.getCurrentPower() + "/" + faction.getMaxPowerCalculated(plugin) + " (was " + oldPower + ")");
        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionAppearance(faction);
        }
    }

    private void handleSetClaimLimit(CommandSender sender, String factionName, String limitStr) {
        Faction faction = plugin.getFaction(factionName);
        if (faction == null) {
            sender.sendMessage(ChatColor.RED + "Faction '" + factionName + "' not found.");
            return;
        }
        int limit;
        try {
            limit = Integer.parseInt(limitStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid limit: '" + limitStr + "'. Must be a whole number (use -1 for default calculation, 0 for unlimited).");
            return;
        }

        faction.setClaimLimitOverride(limit);
        plugin.saveFactionsData(); // Save the change

        String limitDisplay;
        if (limit == -1) {
            limitDisplay = "default calculation (" + faction.getMaxClaimsCalculated(plugin) + ")";
        } else if (limit == 0) {
            limitDisplay = "unlimited";
        } else {
            limitDisplay = String.valueOf(limit);
        }
        sender.sendMessage(ChatColor.GREEN + "Claim limit for faction '" + faction.getName() + "' set to " + limitDisplay + ".");
        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionAppearance(faction); // Update Dynmap as claim limit might be in description
        }
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

    private void handleAdminVault(Player adminPlayer, String factionName) {
        Faction targetFaction = plugin.getFaction(factionName);
        if (targetFaction == null) {
            adminPlayer.sendMessage(ChatColor.RED + "Faction '" + factionName + "' not found.");
            return;
        }
        if (targetFaction.getVault() == null) {
            adminPlayer.sendMessage(ChatColor.RED + "Faction '" + targetFaction.getName() + "' does not have a vault (possibly disabled or an error).");
            return;
        }
        adminPlayer.openInventory(targetFaction.getVault());
        adminPlayer.sendMessage(ChatColor.GREEN + "Opening vault for faction: " + targetFaction.getName());
    }


    public static void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "--- GoatedFactions Admin Help ---");
        sender.sendMessage(ChatColor.GOLD + "/fa deletefaction <name>" + ChatColor.GRAY + " - Deletes faction (alias: /fa disband).");
        sender.sendMessage(ChatColor.GOLD + "/fa addpower <name> <amt>" + ChatColor.GRAY + " - Adds power (capped by max).");
        sender.sendMessage(ChatColor.GOLD + "/fa setpower <name> <amt>" + ChatColor.GRAY + " - Sets exact power (capped by max, min 0).");
        sender.sendMessage(ChatColor.GOLD + "/fa setclaimlimit <name> <limit>" + ChatColor.GRAY + " - Sets claim limit (-1 for default, 0 for unlimited).");
        sender.sendMessage(ChatColor.GOLD + "/fa unclaim all <name>" + ChatColor.GRAY + " - Unclaims all land for specified faction.");
        sender.sendMessage(ChatColor.GOLD + "/fa unclaim current [name]" + ChatColor.GRAY + " - Unclaims current chunk (optionally verify owner).");
        sender.sendMessage(ChatColor.GOLD + "/fa vault <name>" + ChatColor.GRAY + " - Opens the specified faction's vault.");
        sender.sendMessage(ChatColor.GOLD + "/fa reloadconfig" + ChatColor.GRAY + " - Reloads GoatedFactions config.yml.");
        sender.sendMessage(ChatColor.GOLD + "/fa dynmapreload" + ChatColor.GRAY + " - Reprocesses all faction claims for Dynmap (alias: /fa dynmaprefresh).");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("goatedfactions.admin")) return Collections.emptyList();
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "deletefaction", "disband", "addpower", "setpower", "setclaimlimit", "unclaim", "vault", "reloadconfig", "dynmapreload", "dynmaprefresh");
            subCommands.stream()
                    .filter(s -> s.startsWith(currentArg))
                    .forEach(completions::add);
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (Arrays.asList("deletefaction", "disband", "addpower", "setpower", "vault", "setclaimlimit").contains(subCmd)) {
                plugin.getFactionsByNameKey().values().stream()
                        .map(Faction::getName)
                        .filter(fName -> fName != null && fName.toLowerCase().startsWith(currentArg))
                        .forEach(completions::add);
            } else if (subCmd.equals("unclaim")) {
                Arrays.asList("all", "current").stream()
                        .filter(s -> s.startsWith(currentArg))
                        .forEach(completions::add);
            }
        } else if (args.length == 3) {
            String subCmd = args[0].toLowerCase();
            String secondArg = args[1].toLowerCase();

            if (subCmd.equals("unclaim") && (secondArg.equals("all") || secondArg.equals("current"))) {
                plugin.getFactionsByNameKey().values().stream()
                        .map(Faction::getName)
                        .filter(fName -> fName != null && fName.toLowerCase().startsWith(currentArg))
                        .forEach(completions::add);
            } else if (subCmd.equals("setclaimlimit")) {
                // No specific suggestions for the limit number, but could add "-1", "0"
                if ("-1".startsWith(currentArg)) completions.add("-1");
                if ("0".startsWith(currentArg)) completions.add("0");
            }
        }
        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions.stream().distinct().collect(Collectors.toList());
    }
}
