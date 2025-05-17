package gg.goatedcraft.gfactions.commands;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.ChunkWrapper;
import gg.goatedcraft.gfactions.data.Faction;
import gg.goatedcraft.gfactions.data.FactionRank;
import gg.goatedcraft.gfactions.data.Outpost;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation") // For OfflinePlayer methods
public class FactionCommand implements CommandExecutor, TabCompleter {

    private final GFactionsPlugin plugin;
    private static final String COST_PLACEHOLDER = ChatColor.GOLD.toString(); // Placeholder for cost display

    public FactionCommand(GFactionsPlugin plugin) {
        this.plugin = plugin;
        if (this.plugin == null) {
            Bukkit.getLogger().log(Level.SEVERE, "[GFactions - FactionCommand] CRITICAL: GFactionsPlugin instance is NULL!");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { // Java 16+ pattern variable
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            sendPlayerHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

        // Subcommand routing
        switch (subCommand) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f create <name>");
                    return true;
                }
                handleCreateFaction(player, args[1]);
                break;
            case "list":
                handleListFactions(player, args.length > 1 ? args[1] : "1");
                break;
            case "who":
            case "info":
                handleWhoFaction(player, playerFaction, args.length > 1 ? args[1] : null);
                break;
            case "claim":
                handleClaimChunk(player, playerFaction, false, null);
                break;
            case "unclaim":
                handleUnclaimChunk(player, playerFaction);
                break;
            case "sethome":
                handleSetHome(player, playerFaction);
                break;
            case "home":
                handleHome(player, playerFaction, (args.length > 1 ? args[1] : null), false);
                break;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f invite <player>");
                    return true;
                }
                handleInvitePlayer(player, playerFaction, args[1]);
                break;
            case "uninvite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f uninvite <player>");
                    return true;
                }
                handleUninvitePlayer(player, playerFaction, args[1]);
                break;
            case "kick":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f kick <player>");
                    return true;
                }
                handleKick(player, playerFaction, args[1]);
                break;
            case "leave":
                handleLeave(player, playerFaction);
                break;
            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f accept <factionName>");
                    return true;
                }
                handleAcceptMemberInvite(player, args[1]);
                break;
            case "power":
                handlePowerCheck(player, playerFaction, args.length > 1 ? args[1] : null);
                break;
            case "promote":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f promote <player>");
                    return true;
                }
                handlePromote(player, playerFaction, args[1]);
                break;
            case "demote":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f demote <player>");
                    return true;
                }
                handleDemote(player, playerFaction, args[1]);
                break;
            case "leader":
            case "owner":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f " + subCommand + " <player>");
                    return true;
                }
                handleLeaderTransfer(player, playerFaction, args[1]);
                break;
            case "trust":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f trust <player>");
                    return true;
                }
                handleTrust(player, playerFaction, args[1]);
                break;
            case "untrust":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f untrust <player>");
                    return true;
                }
                handleUntrust(player, playerFaction, args[1]);
                break;
            case "vault":
                handleVault(player, playerFaction);
                break;
            case "ally":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f ally <factionName>");
                    return true;
                }
                handleAllyRequest(player, playerFaction, args[1]);
                break;
            case "unally":
            case "neutral":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f " + subCommand + " <factionName>");
                    return true;
                }
                handleUnallyOrNeutral(player, playerFaction, args[1]);
                break;
            case "allyaccept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f allyaccept <factionName>");
                    return true;
                }
                handleAllyAccept(player, playerFaction, args[1]);
                break;
            case "allyrequests":
                handleAllyRequestsList(player, playerFaction);
                break;
            case "enemy":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /f enemy <factionName>");
                    return true;
                }
                handleEnemyDeclare(player, playerFaction, args[1]);
                break;
            case "disband":
                handleDisband(player, playerFaction);
                break;
            case "chat":
            case "c":
                handleFactionChatToggle(player, playerFaction);
                break;
            case "outpost":
                if (args.length < 2) {
                    sendOutpostHelp(player);
                    return true;
                }
                String outpostAction = args[1].toLowerCase();
                String outpostIdentifier = (args.length > 2) ? args[2] : null;
                switch (outpostAction) {
                    case "create":
                        handleOutpostCreate(player, playerFaction);
                        break;
                    case "sethome":
                        handleOutpostSetHome(player, playerFaction, outpostIdentifier);
                        break;
                    case "home":
                        handleOutpostHome(player, playerFaction, outpostIdentifier);
                        break;
                    case "delete":
                        handleOutpostDelete(player, playerFaction, outpostIdentifier);
                        break;
                    default:
                        sendOutpostHelp(player);
                        break;
                }
                break;
            default:
                sendPlayerHelp(player);
                break;
        }
        return true;
    }

    private void sendPlayerHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "--- " + plugin.getName() + " Player Help ---");
        player.sendMessage(ChatColor.YELLOW + "/f create <name>" + ChatColor.GRAY + " - Create a new faction.");
        player.sendMessage(ChatColor.YELLOW + "/f list [page]" + ChatColor.GRAY + " - List all factions.");
        player.sendMessage(ChatColor.YELLOW + "/f who|info [faction]" + ChatColor.GRAY + " - View faction information.");
        player.sendMessage(ChatColor.YELLOW + "/f claim" + ChatColor.GRAY + " - Claim chunk (Cost: " + COST_PLACEHOLDER + plugin.COST_CLAIM_CHUNK + ChatColor.GRAY + ").");
        player.sendMessage(ChatColor.YELLOW + "/f unclaim" + ChatColor.GRAY + " - Unclaim the chunk you are standing in.");
        player.sendMessage(ChatColor.YELLOW + "/f sethome" + ChatColor.GRAY + " - Set your faction's home in claimed land.");
        player.sendMessage(ChatColor.YELLOW + "/f home [faction]" + ChatColor.GRAY + " - Teleport to faction home.");
        player.sendMessage(ChatColor.YELLOW + "/f invite <player>" + ChatColor.GRAY + " - Invite a player to your faction.");
        player.sendMessage(ChatColor.YELLOW + "/f uninvite <player>" + ChatColor.GRAY + " - Revoke an invitation.");
        player.sendMessage(ChatColor.YELLOW + "/f kick <player>" + ChatColor.GRAY + " - Kick a player from your faction.");
        player.sendMessage(ChatColor.YELLOW + "/f leave" + ChatColor.GRAY + " - Leave your current faction.");
        player.sendMessage(ChatColor.YELLOW + "/f accept <factionName>" + ChatColor.GRAY + " - Accept an invite to a faction.");
        player.sendMessage(ChatColor.YELLOW + "/f power [player/faction]" + ChatColor.GRAY + " - Check power levels.");
        player.sendMessage(ChatColor.YELLOW + "/f promote <player>" + ChatColor.GRAY + " - Promote a faction member.");
        player.sendMessage(ChatColor.YELLOW + "/f demote <player>" + ChatColor.GRAY + " - Demote a faction admin.");
        player.sendMessage(ChatColor.YELLOW + "/f leader <player>" + ChatColor.GRAY + " - Transfer faction ownership.");
        player.sendMessage(ChatColor.YELLOW + "/f trust <player>" + ChatColor.GRAY + " - Trust a player in your territory.");
        player.sendMessage(ChatColor.YELLOW + "/f untrust <player>" + ChatColor.GRAY + " - Untrust a player.");
        player.sendMessage(ChatColor.YELLOW + "/f vault" + ChatColor.GRAY + " - Open faction vault.");
        player.sendMessage(ChatColor.YELLOW + "/f ally <faction>" + ChatColor.GRAY + " - Request alliance (Cost: " + COST_PLACEHOLDER + plugin.COST_SEND_ALLY_REQUEST + ChatColor.GRAY + ").");
        player.sendMessage(ChatColor.YELLOW + "/f unally <faction>" + ChatColor.GRAY + " - Break alliance with a faction.");
        player.sendMessage(ChatColor.YELLOW + "/f neutral <faction>" + ChatColor.GRAY + " - Set faction to neutral (Cost: " + COST_PLACEHOLDER + plugin.COST_DECLARE_NEUTRAL + ChatColor.GRAY + ").");
        player.sendMessage(ChatColor.YELLOW + "/f allyaccept <faction>" + ChatColor.GRAY + " - Accept an alliance request.");
        player.sendMessage(ChatColor.YELLOW + "/f allyrequests" + ChatColor.GRAY + " - List pending ally requests.");
        player.sendMessage(ChatColor.YELLOW + "/f enemy <faction>" + ChatColor.GRAY + " - Declare a faction as an enemy (Cost: " + COST_PLACEHOLDER + plugin.COST_DECLARE_ENEMY + ChatColor.GRAY + ").");
        player.sendMessage(ChatColor.YELLOW + "/f disband" + ChatColor.GRAY + " - Disband your faction.");
        player.sendMessage(ChatColor.YELLOW + "/f chat|c" + ChatColor.GRAY + " - Toggle faction-only chat.");
        player.sendMessage(ChatColor.YELLOW + "/f outpost" + ChatColor.GRAY + " - View outpost subcommands.");
        if (player.hasPermission("goatedfactions.admin")) {
            player.sendMessage(ChatColor.RED + "Admin commands: /fa help");
        }
    }

    private void sendOutpostHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "--- Faction Outpost Help ---");
        player.sendMessage(ChatColor.YELLOW + "/f outpost create" + ChatColor.GRAY + " - Create an outpost (Cost: " + COST_PLACEHOLDER + plugin.COST_CREATE_OUTPOST + ChatColor.GRAY + ").");
        player.sendMessage(ChatColor.YELLOW + "/f outpost sethome [id]" + ChatColor.GRAY + " - Set/relocate an outpost's home.");
        player.sendMessage(ChatColor.YELLOW + "/f outpost home [id]" + ChatColor.GRAY + " - Teleport to an outpost's home.");
        player.sendMessage(ChatColor.YELLOW + "/f outpost delete [id]" + ChatColor.GRAY + " - Delete an outpost.");
        player.sendMessage(ChatColor.GRAY + "Use outpost ID (e.g., 1, 2). View IDs with /f who.");
    }

    private void handleCreateFaction(Player player, String name) {
        if (plugin.getFactionByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "You are already in a faction.");
            return;
        }
        if (plugin.getFaction(name) != null) {
            player.sendMessage(ChatColor.RED + "A faction with that name already exists.");
            return;
        }
        if (name.length() < plugin.FACTION_NAME_MIN_LENGTH || name.length() > plugin.FACTION_NAME_MAX_LENGTH) {
            player.sendMessage(ChatColor.RED + "Faction name must be " + plugin.FACTION_NAME_MIN_LENGTH + "-" + plugin.FACTION_NAME_MAX_LENGTH + " characters long.");
            return;
        }
        if (!plugin.FACTION_NAME_PATTERN.matcher(name).matches()) {
            player.sendMessage(ChatColor.RED + "Faction name contains invalid characters. Use letters, numbers, and underscores only.");
            return;
        }

        if (plugin.PREVENT_CLAIM_NEAR_SPAWN && plugin.SERVER_SPAWN_LOCATION != null) {
            Chunk currentChunk = player.getLocation().getChunk();
            Location chunkCenter = new Location(currentChunk.getWorld(), currentChunk.getX() * 16 + 8, player.getLocation().getY(), currentChunk.getZ() * 16 + 8);
            if (Objects.equals(chunkCenter.getWorld(), plugin.SERVER_SPAWN_LOCATION.getWorld()) &&
                    chunkCenter.distanceSquared(plugin.SERVER_SPAWN_LOCATION) < (double) plugin.SPAWN_PROTECTION_RADIUS * plugin.SPAWN_PROTECTION_RADIUS) {
                if (!player.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                    player.sendMessage(ChatColor.RED + "You cannot create a faction this close to server spawn.");
                    return;
                }
            }
        }

        Faction newFaction = plugin.createFactionAndReturn(player, name);
        if (newFaction != null) {
            player.sendMessage(ChatColor.GREEN + "Faction '" + ChatColor.AQUA + newFaction.getName() + ChatColor.GREEN + "' has been created!");
            player.sendMessage(ChatColor.GREEN + "Your home has been set at your current location, and this chunk has been claimed.");
            player.sendMessage(ChatColor.GREEN + "Power: " + newFaction.getCurrentPower() + "/" + newFaction.getMaxPower());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create faction. This might be due to an internal error or an existing faction/player conflict not caught earlier.");
        }
    }

    private void handleListFactions(Player player, String pageArg) {
        int page;
        try {
            page = Integer.parseInt(pageArg);
            if (page < 1) page = 1;
        } catch (NumberFormatException e) {
            page = 1;
        }

        List<Faction> allFactions = new ArrayList<>(plugin.getFactionsByNameKey().values());
        allFactions.sort(Comparator.comparingInt((Faction f) -> f.getMembers().size()).reversed()
                .thenComparing(Faction::getName, String.CASE_INSENSITIVE_ORDER));

        int factionsPerPage = 10;
        int totalPages = Math.max(1, (int) Math.ceil((double) allFactions.size() / factionsPerPage));
        page = Math.min(page, totalPages);


        player.sendMessage(ChatColor.GOLD + "--- Factions List (Page " + String.valueOf(page) + "/" + String.valueOf(totalPages) + ") ---");
        if (allFactions.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No factions exist yet.");
            return;
        }

        int startIndex = (page - 1) * factionsPerPage;
        int endIndex = Math.min(startIndex + factionsPerPage, allFactions.size());

        for (int i = startIndex; i < endIndex; i++) {
            Faction fac = allFactions.get(i);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(fac.getOwnerUUID());
            String ownerName = owner.getName() != null ? owner.getName() : "Unknown (" + fac.getOwnerUUID().toString().substring(0,6) + ")";
            player.sendMessage(ChatColor.YELLOW + String.valueOf(i + 1) + ". " + fac.getName() +
                    ChatColor.GRAY + " (" + String.valueOf(fac.getMembers().size()) + " members, Owner: " + ownerName +
                    ", Power: " + String.valueOf(fac.getCurrentPower()) + "/" + String.valueOf(fac.getMaxPower()) + ")");
        }
    }

    private void handleWhoFaction(Player player, @Nullable Faction playerFaction, @Nullable String targetFactionNameArg) {
        Faction targetFaction;
        if (targetFactionNameArg == null) {
            if (playerFaction == null) {
                player.sendMessage(ChatColor.RED + "You are not in a faction. Use /f who <factionName>");
                return;
            }
            targetFaction = playerFaction;
        } else {
            targetFaction = plugin.getFaction(targetFactionNameArg);
            if (targetFaction == null) {
                player.sendMessage(ChatColor.RED + "Faction '" + targetFactionNameArg + "' not found.");
                return;
            }
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(targetFaction.getOwnerUUID());
        String ownerName = owner.getName() != null ? owner.getName() : "Unknown (" + targetFaction.getOwnerUUID().toString().substring(0,6) + ")";

        player.sendMessage(ChatColor.GOLD + "--- " + ChatColor.AQUA + targetFaction.getName() + ChatColor.GOLD + " ---");
        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + ownerName);
        player.sendMessage(ChatColor.YELLOW + "Power: " + ChatColor.RED + targetFaction.getCurrentPower() + ChatColor.GRAY + "/" + ChatColor.GREEN + targetFaction.getMaxPower());
        player.sendMessage(ChatColor.YELLOW + "Claims: " + ChatColor.WHITE + String.valueOf(targetFaction.getClaimedChunks().size()) +
                (plugin.MAX_CLAIMS_PER_FACTION > 0 ? "/" + String.valueOf(plugin.MAX_CLAIMS_PER_FACTION) : ""));

        if (targetFaction.getHomeLocation() != null) {
            Location home = targetFaction.getHomeLocation();
            player.sendMessage(ChatColor.YELLOW + "Home: " + ChatColor.WHITE + (home.getWorld() != null ? home.getWorld().getName() : "N/A") +
                    " (" + home.getBlockX() + ", " + home.getBlockY() + ", " + home.getBlockZ() + ")");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Home: " + ChatColor.WHITE + "Not set");
        }

        List<String> memberList = new ArrayList<>();
        List<String> adminList = new ArrayList<>();
        targetFaction.getMembers().forEach((uuid, rank) -> {
            OfflinePlayer memberPlayer = Bukkit.getOfflinePlayer(uuid);
            String memberName = memberPlayer.getName() != null ? memberPlayer.getName() : uuid.toString().substring(0, 8);
            ChatColor rankColor = ChatColor.GRAY;
            if (rank == FactionRank.OWNER) rankColor = ChatColor.GOLD;
            else if (rank == FactionRank.ADMIN) rankColor = ChatColor.RED;

            String prefix = memberPlayer.isOnline() ? ChatColor.GREEN + "● " : ChatColor.DARK_GRAY + "● ";

            if (rank == FactionRank.ADMIN || rank == FactionRank.OWNER) {
                adminList.add(prefix + rankColor + memberName);
            } else {
                memberList.add(prefix + rankColor + memberName);
            }
        });
        adminList.sort(String.CASE_INSENSITIVE_ORDER);
        memberList.sort(String.CASE_INSENSITIVE_ORDER);

        if (!adminList.isEmpty()) {
            String adminMessage = ChatColor.YELLOW + "Admins (" + String.valueOf(adminList.size()) + "): " + String.join(ChatColor.GRAY + ", ", adminList);
            player.sendMessage(adminMessage);
        }
        if (!memberList.isEmpty()) {
            String memberMessage = ChatColor.YELLOW + "Members (" + String.valueOf(memberList.size()) + "): " + String.join(ChatColor.GRAY + ", ", memberList);
            player.sendMessage(memberMessage);
        }
        if (adminList.isEmpty() && memberList.isEmpty() && targetFaction.getRank(targetFaction.getOwnerUUID()) == FactionRank.OWNER) {
            String ownerDisplayName = owner.getName() != null ? owner.getName() : "Owner"; // Should use ownerName derived above
            String ownerStatusPrefix = owner.isOnline() ? ChatColor.GREEN + "● " : ChatColor.DARK_GRAY + "● ";
            player.sendMessage(ChatColor.YELLOW + "Members (1): " + ownerStatusPrefix + ChatColor.GOLD + ownerDisplayName);
        }

        if (!targetFaction.getAllyFactionKeys().isEmpty()) {
            String allies = targetFaction.getAllyFactionKeys().stream()
                    .map(key -> { Faction f = plugin.getFaction(key); return f != null && f.getName() != null ? f.getName() : key; })
                    .collect(Collectors.joining(", "));
            player.sendMessage(ChatColor.YELLOW + "Allies: " + ChatColor.AQUA + allies);
        }
        if (!targetFaction.getEnemyFactionKeys().isEmpty()) {
            String enemies = targetFaction.getEnemyFactionKeys().stream()
                    .map(key -> { Faction f = plugin.getFaction(key); return f != null && f.getName() != null ? f.getName() : key; })
                    .collect(Collectors.joining(", "));
            player.sendMessage(ChatColor.YELLOW + "Enemies: " + ChatColor.DARK_RED + enemies);
        }
        if (!targetFaction.getOutposts().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Outposts: " + ChatColor.WHITE + String.valueOf(targetFaction.getOutposts().size()) +
                    (plugin.MAX_OUTPOSTS_PER_FACTION > 0 ? "/" + String.valueOf(plugin.MAX_OUTPOSTS_PER_FACTION) : ""));
            for (Outpost outpost : targetFaction.getOutposts()) {
                Location opLoc = outpost.getOutpostSpawnLocation();
                String opWorld = opLoc != null && opLoc.getWorld() != null ? opLoc.getWorld().getName() : "N/A";
                String opCoords = opLoc != null ? opLoc.getBlockX() + "," + opLoc.getBlockY() + "," + opLoc.getBlockZ() : "N/A";
                String outpostClaimsStr = String.valueOf(outpost.getOutpostSpecificClaims().size());
                player.sendMessage(ChatColor.GRAY + "  - Outpost #" + String.valueOf(outpost.getOutpostID()) + ": " + opWorld + " (" + opCoords + "), Claims: " + outpostClaimsStr);
            }
        }
    }

    private void handleClaimChunk(Player player, @Nullable Faction playerFaction, boolean isOutpostCreation, @Nullable Outpost attachingToOutpost) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You must be in a faction to claim land.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "You must be an admin or owner to claim land.");
            return;
        }

        Chunk chunkToClaim = player.getLocation().getChunk();
        // The GFactionsPlugin#claimChunk method now handles all messages and logic
        if (plugin.claimChunk(playerFaction, chunkToClaim, false, isOutpostCreation, attachingToOutpost)) {
            // Success message is handled by claimChunk if needed, or can be added here
            // For example: player.sendMessage(ChatColor.GREEN + "Chunk claimed!");
            // Power status update:
            player.sendMessage(ChatColor.GREEN + "Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower());
        }
        // Failure messages are handled within plugin.claimChunk()
    }

    private void handleUnclaimChunk(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "You must be an admin or owner to unclaim land.");
            return;
        }

        Chunk chunkToUnclaim = player.getLocation().getChunk();
        ChunkWrapper cw = new ChunkWrapper(chunkToUnclaim.getWorld().getName(), chunkToUnclaim.getX(), chunkToUnclaim.getZ());

        String ownerKey = plugin.getClaimedChunksMap().get(cw);
        if (ownerKey == null || !ownerKey.equalsIgnoreCase(playerFaction.getNameKey())) {
            player.sendMessage(ChatColor.RED + "This chunk is not claimed by your faction.");
            return;
        }

        // Prevent unclaiming last main territory chunk (already handled in plugin.unclaimChunkPlayer)
        // Prevent unclaiming last outpost chunk (already handled in plugin.unclaimChunkPlayer)

        plugin.unclaimChunkPlayer(playerFaction, cw); // This method in plugin will handle logic and messages
        // Message for unclaim success is now in GFactionsPlugin.unclaimChunkPlayer
        player.sendMessage(ChatColor.GRAY + "Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower());
    }

    private void handleSetHome(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "You must be an admin or owner to set the faction home.");
            return;
        }

        Chunk currentChunk = player.getLocation().getChunk();
        ChunkWrapper cw = new ChunkWrapper(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ());

        if (!playerFaction.getClaimedChunks().contains(cw) || playerFaction.isChunkInOutpostTerritory(cw)) {
            player.sendMessage(ChatColor.RED + "You can only set your faction home in your faction's main claimed territory (not outpost land).");
            return;
        }

        playerFaction.setHomeLocation(player.getLocation());
        plugin.saveFactionsData();
        player.sendMessage(ChatColor.GREEN + "Faction home has been set to your current location!");
    }

    private void handleHome(Player player, @Nullable Faction playerFaction, @Nullable String targetFactionName, boolean isOutpostHomeCmd) {
        if (isOutpostHomeCmd) return;

        Faction targetFac;
        if (targetFactionName != null) {
            targetFac = plugin.getFaction(targetFactionName);
            if (targetFac == null) {
                player.sendMessage(ChatColor.RED + "Faction '" + targetFactionName + "' not found.");
                return;
            }
            boolean canTeleportToOther = playerFaction != null && (playerFaction.equals(targetFac) || playerFaction.isAlly(targetFac.getNameKey()));
            if (playerFaction != null && !playerFaction.equals(targetFac) && !canTeleportToOther) {
                player.sendMessage(ChatColor.RED + "You can only teleport to your own faction's home or an ally's home.");
                return;
            }
        } else {
            if (playerFaction == null) {
                player.sendMessage(ChatColor.RED + "You are not in a faction. Use /f home <factionName> or join a faction.");
                return;
            }
            targetFac = playerFaction;
        }

        Location homeLocation = targetFac.getHomeLocation();
        if (homeLocation == null) {
            player.sendMessage(ChatColor.RED + (targetFac.equals(playerFaction) ? "Your faction does not have a home set." : targetFac.getName() + " does not have a home set."));
            return;
        }
        if (homeLocation.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "The home location for " + targetFac.getName() + " is in an unloaded world. Cannot teleport.");
            plugin.getLogger().warning("Attempted to teleport to home for " + targetFac.getName() + " but world " + (targetFac.getHomeChunk() != null ? targetFac.getHomeChunk().getWorldName() : "unknown") + " is not loaded.");
            return;
        }
        plugin.initiateTeleportWarmup(player, homeLocation, ChatColor.GREEN + "Teleporting to " + targetFac.getName() + "'s home...");
    }

    private void handleInvitePlayer(Player inviter, @Nullable Faction inviterFaction, String invitedPlayerName) {
        if (inviterFaction == null) {
            inviter.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = inviterFaction.getRank(inviter.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            inviter.sendMessage(ChatColor.RED + "You must be an admin or owner to invite players.");
            return;
        }

        OfflinePlayer invitedOfflinePlayer = Bukkit.getOfflinePlayer(invitedPlayerName);
        String invitedDisplayName = invitedOfflinePlayer.getName() != null ? invitedOfflinePlayer.getName() : invitedPlayerName;

        if (!invitedOfflinePlayer.hasPlayedBefore() && !invitedOfflinePlayer.isOnline()) {
            inviter.sendMessage(ChatColor.RED + "Player '" + invitedDisplayName + "' not found or has never played here.");
            return;
        }
        if (plugin.getFactionByPlayer(invitedOfflinePlayer.getUniqueId()) != null) {
            inviter.sendMessage(ChatColor.RED + invitedDisplayName + " is already in a faction.");
            return;
        }
        if (inviterFaction.getMembers().containsKey(invitedOfflinePlayer.getUniqueId())) {
            inviter.sendMessage(ChatColor.RED + invitedDisplayName + " is already a member of your faction.");
            return;
        }
        if (plugin.MAX_MEMBERS_PER_FACTION > 0 && inviterFaction.getMembers().size() >= plugin.MAX_MEMBERS_PER_FACTION) {
            inviter.sendMessage(ChatColor.RED + "Your faction has reached the maximum member limit (" + String.valueOf(plugin.MAX_MEMBERS_PER_FACTION) + ").");
            return;
        }

        if (plugin.sendMemberInvite(inviterFaction, invitedOfflinePlayer.getUniqueId())) {
            inviter.sendMessage(ChatColor.GREEN + "Invite sent to " + invitedDisplayName + ". It will expire in " + String.valueOf(plugin.EXPIRATION_MEMBER_INVITE_MINUTES) + " minutes.");
            if (invitedOfflinePlayer.isOnline()) {
                ((Player) invitedOfflinePlayer).sendMessage(ChatColor.GREEN + "You have been invited to join " + inviterFaction.getName() + "! Type " + ChatColor.YELLOW + "/f accept " + inviterFaction.getName() + ChatColor.GREEN + " to join.");
            }
        } else {
            inviter.sendMessage(ChatColor.RED + "Could not send invite. They might already have a pending invite from your faction, or an error occurred.");
        }
    }

    private void handleUninvitePlayer(Player player, @Nullable Faction playerFaction, String targetPlayerName) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "You must be an admin or owner to uninvite players.");
            return;
        }
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        String targetDisplayName = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : targetPlayerName;
        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline()) {
            player.sendMessage(ChatColor.RED + "Player '" + targetDisplayName + "' not found.");
            return;
        }

        plugin.revokeMemberInvite(playerFaction, targetOfflinePlayer);
        player.sendMessage(ChatColor.GREEN + "Any pending invite for " + targetDisplayName + " to your faction has been revoked.");
    }

    private void handleKick(Player kicker, @Nullable Faction kickerFaction, String targetName) {
        if (kickerFaction == null) {
            kicker.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank kickerRank = kickerFaction.getRank(kicker.getUniqueId());
        if (kickerRank != FactionRank.OWNER && kickerRank != FactionRank.ADMIN) {
            kicker.sendMessage(ChatColor.RED + "You must be an admin or owner to kick members.");
            return;
        }

        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetName);
        String targetDisplayName = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : targetName;

        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline() && !kickerFaction.getMembers().containsKey(targetOfflinePlayer.getUniqueId())) {
            kicker.sendMessage(ChatColor.RED + "Player '" + targetDisplayName + "' not found or is not in your faction.");
            return;
        }

        Faction targetPlayerCurrentFaction = plugin.getFactionByPlayer(targetOfflinePlayer.getUniqueId());
        if (targetPlayerCurrentFaction == null || !targetPlayerCurrentFaction.equals(kickerFaction)) {
            kicker.sendMessage(ChatColor.RED + targetDisplayName + " is not a member of your faction.");
            return;
        }

        FactionRank targetRank = kickerFaction.getRank(targetOfflinePlayer.getUniqueId());
        if (targetRank == FactionRank.OWNER) {
            kicker.sendMessage(ChatColor.RED + "You cannot kick the owner of the faction.");
            return;
        }
        if (targetRank == FactionRank.ADMIN && kickerRank != FactionRank.OWNER) {
            kicker.sendMessage(ChatColor.RED + "Only the owner can kick other admins.");
            return;
        }

        if (plugin.removePlayerFromFaction(kickerFaction, targetOfflinePlayer.getUniqueId(), true)) {
            kickerFaction.broadcastMessage(ChatColor.AQUA + targetDisplayName + ChatColor.YELLOW + " has been kicked from the faction by " + ChatColor.AQUA + kicker.getName() + ChatColor.YELLOW + ".", null);
            if (targetOfflinePlayer.isOnline()) {
                ((Player) targetOfflinePlayer).sendMessage(ChatColor.RED + "You have been kicked from " + kickerFaction.getName() + ".");
            }
        } else {
            kicker.sendMessage(ChatColor.RED + "Could not kick " + targetDisplayName + ". This shouldn't happen if checks passed.");
        }
    }

    private void handleLeave(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction to leave.");
            return;
        }
        if (playerFaction.isOwner(player.getUniqueId()) && playerFaction.getMembers().size() > 1) {
            player.sendMessage(ChatColor.RED + "You are the owner! Please transfer ownership using " + ChatColor.YELLOW + "/f leader <newLeader>" + ChatColor.RED + " or disband the faction using " + ChatColor.YELLOW + "/f disband" + ChatColor.RED + " if you are the last member.");
            return;
        }

        String factionNameBeforeLeave = playerFaction.getName(); // Store name before potential disband
        if (plugin.removePlayerFromFaction(playerFaction, player.getUniqueId(), false)) {
            player.sendMessage(ChatColor.GREEN + "You have left " + factionNameBeforeLeave + ".");
            // Check if the faction still exists by trying to fetch it again by its original name key
            Faction factionAfterLeave = plugin.getFaction(factionNameBeforeLeave);
            if (factionAfterLeave != null) { // Faction still exists
                factionAfterLeave.broadcastMessage(ChatColor.AQUA + player.getName() + ChatColor.YELLOW + " has left the faction.", player.getUniqueId());
            } else { // Faction was disbanded (owner was last to leave)
                Bukkit.broadcastMessage(ChatColor.GOLD + factionNameBeforeLeave + " has been disbanded as its last member left.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Could not leave faction. This is an unexpected error.");
        }
    }

    private void handleAcceptMemberInvite(Player player, String factionNameToJoin) {
        if (plugin.getFactionByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "You are already in a faction. Leave your current faction first with /f leave.");
            return;
        }
        Faction targetFaction = plugin.getFaction(factionNameToJoin);
        if (targetFaction == null) {
            player.sendMessage(ChatColor.RED + "Faction '" + factionNameToJoin + "' does not exist or the invite is no longer valid.");
            return;
        }

        if (plugin.acceptMemberInvite(player.getUniqueId(), targetFaction.getNameKey())) {
            player.sendMessage(ChatColor.GREEN + "You have joined " + targetFaction.getName() + "!");
            targetFaction.broadcastMessage(ChatColor.AQUA + player.getName() + ChatColor.GREEN + " has joined the faction!", null);
        } else {
            player.sendMessage(ChatColor.RED + "Failed to join " + targetFaction.getName() + ". The invite may have expired, been revoked, or the faction is full.");
        }
    }

    private void handlePowerCheck(Player player, @Nullable Faction playerFaction, @Nullable String targetName) {
        if (targetName == null) {
            if (playerFaction != null) {
                player.sendMessage(ChatColor.GOLD + "--- Your Faction Power: " + playerFaction.getName() + " ---");
                player.sendMessage(ChatColor.YELLOW + "Current Power: " + ChatColor.RED + String.valueOf(playerFaction.getCurrentPower()));
                player.sendMessage(ChatColor.YELLOW + "Maximum Power: " + ChatColor.GREEN + String.valueOf(playerFaction.getMaxPower()));
                player.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.WHITE + String.valueOf(playerFaction.getMembers().size()));
            } else {
                player.sendMessage(ChatColor.RED + "You are not in a faction. Use /f power <faction/player> to check others.");
            }
            return;
        }

        Faction targetFaction = plugin.getFaction(targetName);
        if (targetFaction != null) {
            player.sendMessage(ChatColor.GOLD + "--- Faction Power: " + targetFaction.getName() + " ---");
            player.sendMessage(ChatColor.YELLOW + "Current Power: " + ChatColor.RED + String.valueOf(targetFaction.getCurrentPower()));
            player.sendMessage(ChatColor.YELLOW + "Maximum Power: " + ChatColor.GREEN + String.valueOf(targetFaction.getMaxPower()));
            player.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.WHITE + String.valueOf(targetFaction.getMembers().size()));
        } else {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            String targetDisplayName = targetPlayer.getName() != null ? targetPlayer.getName() : targetName;
            if (targetPlayer.hasPlayedBefore() || targetPlayer.isOnline()) {
                Faction facOfTarget = plugin.getFactionByPlayer(targetPlayer.getUniqueId());
                if (facOfTarget != null) {
                    player.sendMessage(ChatColor.GOLD + "--- Player " + targetDisplayName + " (Faction: " + facOfTarget.getName() + ") ---");
                    player.sendMessage(ChatColor.YELLOW + "Faction Power: " + ChatColor.RED + String.valueOf(facOfTarget.getCurrentPower()) +
                            ChatColor.GRAY + "/" + ChatColor.GREEN + String.valueOf(facOfTarget.getMaxPower()));
                } else {
                    player.sendMessage(ChatColor.RED + "Player " + targetDisplayName + " is not in a faction.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Faction or player '" + targetName + "' not found.");
            }
        }
    }

    private void handlePromote(Player promoter, @Nullable Faction promoterFaction, String targetName) {
        if (promoterFaction == null) {
            promoter.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        if (!promoterFaction.isOwner(promoter.getUniqueId())) {
            promoter.sendMessage(ChatColor.RED + "Only the faction owner can promote members.");
            return;
        }
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetName);
        String targetDisplayName = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : targetName;

        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline() && !promoterFaction.getMembers().containsKey(targetOfflinePlayer.getUniqueId())) {
            promoter.sendMessage(ChatColor.RED + "Player '" + targetDisplayName + "' not found or is not in your faction.");
            return;
        }
        if (!promoterFaction.getMembers().containsKey(targetOfflinePlayer.getUniqueId())) {
            promoter.sendMessage(ChatColor.RED + targetDisplayName + " is not a member of your faction.");
            return;
        }
        if (promoter.getUniqueId().equals(targetOfflinePlayer.getUniqueId())) {
            promoter.sendMessage(ChatColor.RED + "You cannot promote yourself.");
            return;
        }

        if (promoterFaction.promotePlayer(targetOfflinePlayer.getUniqueId())) {
            promoterFaction.broadcastMessage(ChatColor.AQUA + targetDisplayName + ChatColor.GREEN + " has been promoted to Faction Admin by " + promoter.getName() + "!", null);
            plugin.saveFactionsData();
        } else {
            promoter.sendMessage(ChatColor.RED + targetDisplayName + " is already an admin or could not be promoted (e.g., is owner).");
        }
    }

    private void handleDemote(Player demoter, @Nullable Faction demoterFaction, String targetName) {
        if (demoterFaction == null) {
            demoter.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        if (!demoterFaction.isOwner(demoter.getUniqueId())) {
            demoter.sendMessage(ChatColor.RED + "Only the faction owner can demote admins.");
            return;
        }
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetName);
        String targetDisplayName = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : targetName;

        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline() && !demoterFaction.getMembers().containsKey(targetOfflinePlayer.getUniqueId())) {
            demoter.sendMessage(ChatColor.RED + "Player '" + targetDisplayName + "' not found or is not in your faction.");
            return;
        }
        if (!demoterFaction.getMembers().containsKey(targetOfflinePlayer.getUniqueId())) {
            demoter.sendMessage(ChatColor.RED + targetDisplayName + " is not a member of your faction.");
            return;
        }
        if (demoter.getUniqueId().equals(targetOfflinePlayer.getUniqueId())) {
            demoter.sendMessage(ChatColor.RED + "You cannot demote yourself.");
            return;
        }

        if (demoterFaction.demotePlayer(targetOfflinePlayer.getUniqueId())) {
            demoterFaction.broadcastMessage(ChatColor.AQUA + targetDisplayName + ChatColor.YELLOW + " has been demoted to Faction Member by " + demoter.getName() + ".", null);
            plugin.saveFactionsData();
        } else {
            demoter.sendMessage(ChatColor.RED + targetDisplayName + " is not an admin or could not be demoted (e.g., is owner).");
        }
    }

    private void handleLeaderTransfer(Player currentOwner, @Nullable Faction faction, String newLeaderName) {
        if (faction == null) {
            currentOwner.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        if (!faction.isOwner(currentOwner.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + "You are not the owner of this faction.");
            return;
        }
        OfflinePlayer newOwnerOfflinePlayer = Bukkit.getOfflinePlayer(newLeaderName);
        String newLeaderDisplayName = newOwnerOfflinePlayer.getName() != null ? newOwnerOfflinePlayer.getName() : newLeaderName;

        if (!newOwnerOfflinePlayer.hasPlayedBefore() && !newOwnerOfflinePlayer.isOnline() && !faction.getMembers().containsKey(newOwnerOfflinePlayer.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + "Player '" + newLeaderDisplayName + "' not found or is not in your faction.");
            return;
        }
        if (!faction.getMembers().containsKey(newOwnerOfflinePlayer.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + newLeaderDisplayName + " is not a member of your faction. Promote them or have them join first.");
            return;
        }
        if (currentOwner.getUniqueId().equals(newOwnerOfflinePlayer.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + "You are already the owner.");
            return;
        }

        if (plugin.transferOwnership(faction, currentOwner, newOwnerOfflinePlayer)) {
            faction.broadcastMessage(ChatColor.GOLD + currentOwner.getName() + " has transferred ownership of the faction to " + newLeaderDisplayName + "!", null);
        } else {
            currentOwner.sendMessage(ChatColor.RED + "Failed to transfer ownership. This is an unexpected error.");
        }
    }

    private void handleTrust(Player truster, @Nullable Faction trusterFaction, String targetName) {
        if (trusterFaction == null) {
            truster.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = trusterFaction.getRank(truster.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            truster.sendMessage(ChatColor.RED + "You must be an admin or owner to manage trusted players.");
            return;
        }
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetName);
        String targetDisplayName = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : targetName;

        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline()) {
            truster.sendMessage(ChatColor.RED + "Player '" + targetDisplayName + "' not found.");
            return;
        }
        if (trusterFaction.getMembers().containsKey(targetOfflinePlayer.getUniqueId())) {
            truster.sendMessage(ChatColor.RED + "Faction members are automatically trusted. You don't need to add them.");
            return;
        }

        if (trusterFaction.addTrusted(targetOfflinePlayer.getUniqueId())) {
            truster.sendMessage(ChatColor.GREEN + targetDisplayName + " has been added to your faction's trusted list.");
            plugin.saveFactionsData();
        } else {
            truster.sendMessage(ChatColor.YELLOW + targetDisplayName + " is already trusted or is a member.");
        }
    }

    private void handleUntrust(Player untruster, @Nullable Faction untrusterFaction, String targetName) {
        if (untrusterFaction == null) {
            untruster.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = untrusterFaction.getRank(untruster.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            untruster.sendMessage(ChatColor.RED + "You must be an admin or owner to manage trusted players.");
            return;
        }
        OfflinePlayer targetOfflinePlayer = Bukkit.getOfflinePlayer(targetName);
        String targetDisplayName = targetOfflinePlayer.getName() != null ? targetOfflinePlayer.getName() : targetName;

        if (!targetOfflinePlayer.hasPlayedBefore() && !targetOfflinePlayer.isOnline() && !untrusterFaction.getTrustedPlayers().contains(targetOfflinePlayer.getUniqueId())) {
            untruster.sendMessage(ChatColor.RED + "Player '" + targetDisplayName + "' not found or was not on the trusted list.");
            return;
        }

        if (untrusterFaction.removeTrusted(targetOfflinePlayer.getUniqueId())) {
            untruster.sendMessage(ChatColor.GREEN + targetDisplayName + " has been removed from your faction's trusted list.");
            plugin.saveFactionsData();
        } else {
            untruster.sendMessage(ChatColor.RED + targetDisplayName + " was not on the trusted list.");
        }
    }

    private void handleVault(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You must be in a faction to use the vault.");
            return;
        }
        player.openInventory(playerFaction.getVault());
        player.sendMessage(ChatColor.GREEN + "Opening faction vault for " + playerFaction.getName() + "...");
    }

    private void handleAllyRequest(Player requesterPlayer, @Nullable Faction requesterFaction, String targetFactionName) {
        if (requesterFaction == null) {
            requesterPlayer.sendMessage(ChatColor.RED + "You must be in a faction to manage alliances.");
            return;
        }
        FactionRank rank = requesterFaction.getRank(requesterPlayer.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            requesterPlayer.sendMessage(ChatColor.RED + "You must be an admin or owner to manage alliances.");
            return;
        }
        Faction targetFaction = plugin.getFaction(targetFactionName);
        if (targetFaction == null) {
            requesterPlayer.sendMessage(ChatColor.RED + "Faction '" + targetFactionName + "' not found.");
            return;
        }
        if (requesterFaction.equals(targetFaction)) {
            requesterPlayer.sendMessage(ChatColor.RED + "You cannot ally with your own faction.");
            return;
        }
        if (requesterFaction.isAlly(targetFaction.getNameKey())) {
            requesterPlayer.sendMessage(ChatColor.YELLOW + "You are already allied with " + targetFaction.getName() + ".");
            return;
        }
        if (requesterFaction.isEnemy(targetFaction.getNameKey())) {
            requesterPlayer.sendMessage(ChatColor.RED + "You are enemies with " + targetFaction.getName() + ". You must become neutral first.");
            return;
        }

        if (plugin.sendAllyRequest(requesterFaction, targetFaction)) {
            requesterPlayer.sendMessage(ChatColor.GREEN + "Ally request sent to " + targetFaction.getName() + ".");
            plugin.notifyFaction(targetFaction, ChatColor.AQUA + requesterFaction.getName() + ChatColor.GREEN + " has requested an alliance with your faction! Use " + ChatColor.YELLOW + "/f allyaccept " + requesterFaction.getName() + ChatColor.GREEN + " to accept.", null);
        } else {
            requesterPlayer.sendMessage(ChatColor.RED + "Could not send ally request. One might already be pending or you lack power.");
        }
    }

    private void handleUnallyOrNeutral(Player player, @Nullable Faction playerFaction, String targetFactionName) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You must be in a faction.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "You must be an admin or owner to manage relations.");
            return;
        }
        Faction targetFaction = plugin.getFaction(targetFactionName);
        if (targetFaction == null) {
            player.sendMessage(ChatColor.RED + "Faction '" + targetFactionName + "' not found.");
            return;
        }
        if (playerFaction.equals(targetFaction)) {
            player.sendMessage(ChatColor.RED + "You cannot change relations with your own faction.");
            return;
        }

        if (playerFaction.isAlly(targetFaction.getNameKey())) {
            plugin.revokeAlliance(playerFaction, targetFaction);
            playerFaction.broadcastMessage(ChatColor.YELLOW + "Your faction is no longer allied with " + ChatColor.AQUA + targetFaction.getName() + ChatColor.YELLOW + ".", null);
            plugin.notifyFaction(targetFaction, ChatColor.AQUA + playerFaction.getName() + ChatColor.YELLOW + " has ended your alliance.", null);
        } else if (playerFaction.isEnemy(targetFaction.getNameKey())) {
            long timeSinceDeclaredEnemy = System.currentTimeMillis() - playerFaction.getEnemyDeclareTimestamps().getOrDefault(targetFaction.getNameKey().toLowerCase(), 0L);
            long cooldownMillis = TimeUnit.HOURS.toMillis(plugin.COOLDOWN_ENEMY_NEUTRAL_HOURS);

            if (timeSinceDeclaredEnemy < cooldownMillis) {
                long remainingMillis = cooldownMillis - timeSinceDeclaredEnemy;
                player.sendMessage(ChatColor.RED + "You cannot declare neutrality with " + targetFaction.getName() + " yet. Time remaining: " + formatTimeApprox(remainingMillis));
                return;
            }
            if (playerFaction.getCurrentPower() < plugin.COST_DECLARE_NEUTRAL) {
                player.sendMessage(ChatColor.RED + "Not enough power to declare neutrality. Cost: " + String.valueOf(plugin.COST_DECLARE_NEUTRAL));
                return;
            }
            playerFaction.removePower(plugin.COST_DECLARE_NEUTRAL);
            playerFaction.removeEnemy(targetFaction.getNameKey());
            targetFaction.removeEnemy(playerFaction.getNameKey());
            plugin.saveFactionsData();
            if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
                plugin.getDynmapManager().updateFactionRelations(playerFaction, targetFaction);
            }
            playerFaction.broadcastMessage(ChatColor.YELLOW + "Your faction is now neutral with " + ChatColor.AQUA + targetFaction.getName() + ChatColor.YELLOW + ".", null);
            plugin.notifyFaction(targetFaction, ChatColor.AQUA + playerFaction.getName() + ChatColor.YELLOW + " has declared neutrality with your faction.", null);

        } else {
            player.sendMessage(ChatColor.YELLOW + "You are already neutral with " + targetFaction.getName() + ".");
        }
    }

    private void handleAllyAccept(Player accepterPlayer, @Nullable Faction accepterFaction, String requestingFactionName) {
        if (accepterFaction == null) {
            accepterPlayer.sendMessage(ChatColor.RED + "You must be in a faction to accept an alliance.");
            return;
        }
        FactionRank rank = accepterFaction.getRank(accepterPlayer.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            accepterPlayer.sendMessage(ChatColor.RED + "You must be an admin or owner to accept alliances.");
            return;
        }
        Faction requestingFaction = plugin.getFaction(requestingFactionName);
        if (requestingFaction == null) {
            accepterPlayer.sendMessage(ChatColor.RED + "Faction '" + requestingFactionName + "' not found or the request is no longer valid.");
            return;
        }

        if (plugin.acceptAllyRequest(accepterFaction, requestingFaction)) {
            accepterFaction.broadcastMessage(ChatColor.GREEN + "Your faction is now allied with " + ChatColor.AQUA + requestingFaction.getName() + ChatColor.GREEN + "!", null);
            plugin.notifyFaction(requestingFaction, ChatColor.AQUA + accepterFaction.getName() + ChatColor.GREEN + " has accepted your alliance request!", null);
        } else {
            accepterPlayer.sendMessage(ChatColor.RED + "Could not accept alliance. The request may have expired or been revoked.");
        }
    }

    private void handleAllyRequestsList(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        Map<String, Long> requests = plugin.getPendingAllyRequestsFor(playerFaction.getNameKey());
        if (requests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no pending alliance requests.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "--- Pending Alliance Requests ---");
        long currentTime = System.currentTimeMillis();
        requests.forEach((key, expiryTime) -> {
            Faction requestingFac = plugin.getFaction(key);
            if (requestingFac != null) {
                long timeRemaining = expiryTime - currentTime; // Expiry time is absolute here
                if (timeRemaining > 0) {
                    player.sendMessage(ChatColor.YELLOW + requestingFac.getName() + ChatColor.GRAY + " (Expires in " + formatTimeApprox(timeRemaining) + ")");
                }
            }
        });
    }

    private void handleEnemyDeclare(Player player, @Nullable Faction playerFaction, String targetFactionName) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You must be in a faction to declare enemies.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "You must be an admin or owner to declare enemies.");
            return;
        }
        Faction targetFaction = plugin.getFaction(targetFactionName);
        if (targetFaction == null) {
            player.sendMessage(ChatColor.RED + "Faction '" + targetFactionName + "' not found.");
            return;
        }
        if (playerFaction.equals(targetFaction)) {
            player.sendMessage(ChatColor.RED + "You cannot declare war on yourself.");
            return;
        }
        if (playerFaction.isEnemy(targetFaction.getNameKey())) {
            player.sendMessage(ChatColor.YELLOW + "You are already enemies with " + targetFaction.getName() + ".");
            return;
        }
        if (playerFaction.getCurrentPower() < plugin.COST_DECLARE_ENEMY) {
            player.sendMessage(ChatColor.RED + "Not enough power to declare an enemy. Cost: " + String.valueOf(plugin.COST_DECLARE_ENEMY));
            return;
        }

        playerFaction.removePower(plugin.COST_DECLARE_ENEMY);
        playerFaction.addEnemy(targetFaction.getNameKey(), System.currentTimeMillis());
        targetFaction.addEnemy(playerFaction.getNameKey(), System.currentTimeMillis());
        plugin.saveFactionsData();
        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionRelations(playerFaction, targetFaction);
        }

        playerFaction.broadcastMessage(ChatColor.DARK_RED + "Your faction has declared " + ChatColor.RED + targetFaction.getName() + ChatColor.DARK_RED + " as an ENEMY!", null);
        plugin.notifyFaction(targetFaction, ChatColor.RED + playerFaction.getName() + ChatColor.DARK_RED + " has declared your faction as an ENEMY!", null);
    }

    private void handleDisband(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction to disband.");
            return;
        }
        if (!playerFaction.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the faction owner can disband the faction.");
            return;
        }
        String factionName = playerFaction.getName(); // Get name before it's gone
        plugin.disbandFactionInternal(playerFaction, false);
        Bukkit.broadcastMessage(ChatColor.GOLD + "Faction " + ChatColor.AQUA + factionName + ChatColor.GOLD + " has been disbanded by its owner, " + player.getName() + ".");
    }

    private void handleFactionChatToggle(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You must be in a faction to use faction chat.");
            return;
        }
        boolean nowInFactionChat = plugin.togglePlayerFactionChat(player.getUniqueId());
        if (nowInFactionChat) {
            player.sendMessage(ChatColor.GREEN + "You are now talking in faction chat. Type " + ChatColor.YELLOW + "/f chat" + ChatColor.GREEN + " again to switch to public chat.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "You are now talking in public chat.");
        }
    }

    private void handleOutpostCreate(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You must be in a faction to create an outpost.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "Only faction admins or the owner can create outposts.");
            return;
        }
        if (plugin.MAX_OUTPOSTS_PER_FACTION > 0 && playerFaction.getOutposts().size() >= plugin.MAX_OUTPOSTS_PER_FACTION) {
            player.sendMessage(ChatColor.RED + "Your faction has reached the maximum number of outposts (" + String.valueOf(plugin.MAX_OUTPOSTS_PER_FACTION) + ").");
            return;
        }
        if (playerFaction.getCurrentPower() < plugin.COST_CREATE_OUTPOST) {
            player.sendMessage(ChatColor.RED + "Not enough power to create an outpost. Cost: " + String.valueOf(plugin.COST_CREATE_OUTPOST));
            return;
        }

        Chunk currentChunk = player.getLocation().getChunk();
        ChunkWrapper cw = new ChunkWrapper(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ());

        if (playerFaction.getClaimedChunks().contains(cw)) {
            player.sendMessage(ChatColor.RED + "This chunk is already part of your faction's territory (main or another outpost).");
            return;
        }
        String ownerKey = plugin.getClaimedChunksMap().get(cw);
        if (ownerKey != null) {
            Faction occupyingFaction = plugin.getFaction(ownerKey);
            player.sendMessage(ChatColor.RED + "This land is already claimed by " + (occupyingFaction != null ? occupyingFaction.getName() : "another faction") + ".");
            return;
        }
        if (plugin.PREVENT_CLAIM_NEAR_SPAWN && plugin.SERVER_SPAWN_LOCATION != null) {
            Location chunkCenter = new Location(currentChunk.getWorld(), currentChunk.getX() * 16 + 8, player.getLocation().getY(), currentChunk.getZ() * 16 + 8);
            if (Objects.equals(chunkCenter.getWorld(), plugin.SERVER_SPAWN_LOCATION.getWorld()) &&
                    chunkCenter.distanceSquared(plugin.SERVER_SPAWN_LOCATION) < (double) plugin.SPAWN_PROTECTION_RADIUS * plugin.SPAWN_PROTECTION_RADIUS) {
                if (!player.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                    player.sendMessage(ChatColor.RED + "You cannot create an outpost this close to server spawn.");
                    return;
                }
            }
        }

        Outpost newOutpost = new Outpost(player.getLocation(), cw);
        if (playerFaction.addOutpost(newOutpost)) {
            if (plugin.claimChunk(playerFaction, currentChunk, false, true, newOutpost)) {
                playerFaction.removePower(plugin.COST_CREATE_OUTPOST);
                plugin.saveFactionsData();
                player.sendMessage(ChatColor.GREEN + "Outpost #" + String.valueOf(newOutpost.getOutpostID()) + " created at your location! This chunk is now its home.");
                player.sendMessage(ChatColor.GREEN + "Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower());

            } else {
                playerFaction.removeOutpostAndItsClaims(newOutpost);
                player.sendMessage(ChatColor.RED + "Failed to claim land for the outpost. Outpost creation cancelled.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create outpost. You might have reached the limit or an internal error occurred.");
        }
    }

    @Nullable
    private Outpost getTargetOutpostByIdentifier(Player player, Faction faction, @Nullable String identifier) {
        if (faction.getOutposts().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your faction has no outposts.");
            return null;
        }
        if (identifier == null || identifier.isEmpty()) {
            if (faction.getOutposts().size() == 1) {
                return faction.getOutposts().get(0);
            } else {
                player.sendMessage(ChatColor.RED + "Please specify an outpost ID. Use /f who to see outpost IDs.");
                return null;
            }
        }
        try {
            int id = Integer.parseInt(identifier);
            Outpost outpost = faction.getOutpostById(id);
            if (outpost == null) {
                player.sendMessage(ChatColor.RED + "Outpost with ID " + String.valueOf(id) + " not found.");
                return null;
            }
            return outpost;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid outpost ID format. Please use a number.");
            return null;
        }
    }

    private void handleOutpostSetHome(Player player, @Nullable Faction playerFaction, @Nullable String outpostIdentifier) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "Only faction admins or the owner can set outpost homes.");
            return;
        }
        Outpost targetOutpost = getTargetOutpostByIdentifier(player, playerFaction, outpostIdentifier);
        if (targetOutpost == null) return;

        Chunk currentChunk = player.getLocation().getChunk();
        ChunkWrapper cw = new ChunkWrapper(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ());

        if (!targetOutpost.getOutpostSpecificClaims().contains(cw)) {
            player.sendMessage(ChatColor.RED + "You can only set an outpost's home within its own claimed territory.");
            return;
        }

        targetOutpost.setOutpostSpawnLocation(player.getLocation());
        targetOutpost.addClaim(cw); // Ensure the new spawn chunk is explicitly part of its claims
        playerFaction.addClaim(cw, true, targetOutpost); // Ensure faction's global claims are updated

        plugin.saveFactionsData();
        player.sendMessage(ChatColor.GREEN + "Outpost #" + String.valueOf(targetOutpost.getOutpostID()) + " home has been set to your current location.");
    }

    private void handleOutpostHome(Player player, @Nullable Faction playerFaction, @Nullable String outpostIdentifier) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        Outpost targetOutpost = getTargetOutpostByIdentifier(player, playerFaction, outpostIdentifier);
        if (targetOutpost == null) return;

        Location outpostSpawn = targetOutpost.getOutpostSpawnLocation();
        if (outpostSpawn == null) {
            player.sendMessage(ChatColor.RED + "Outpost #" + String.valueOf(targetOutpost.getOutpostID()) + " does not have a valid home location set.");
            return;
        }
        if (outpostSpawn.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Outpost #" + String.valueOf(targetOutpost.getOutpostID()) + " home is in an unloaded world.");
            return;
        }
        plugin.initiateTeleportWarmup(player, outpostSpawn, ChatColor.GREEN + "Teleporting to Outpost #" + String.valueOf(targetOutpost.getOutpostID()) + "...");
    }

    private void handleOutpostDelete(Player player, @Nullable Faction playerFaction, @Nullable String outpostIdentifier) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You are not in a faction.");
            return;
        }
        FactionRank rank = playerFaction.getRank(player.getUniqueId());
        if (rank != FactionRank.OWNER && rank != FactionRank.ADMIN) {
            player.sendMessage(ChatColor.RED + "Only faction admins or the owner can delete outposts.");
            return;
        }
        Outpost targetOutpost = getTargetOutpostByIdentifier(player, playerFaction, outpostIdentifier);
        if (targetOutpost == null) return;

        int outpostOldId = targetOutpost.getOutpostID();
        if (playerFaction.removeOutpostAndItsClaims(targetOutpost)) {
            plugin.saveFactionsData();
            if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()){
                plugin.getDynmapManager().updateFactionClaimsVisual(playerFaction);
            }
            player.sendMessage(ChatColor.GREEN + "Outpost #" + String.valueOf(outpostOldId) + " and all its claims have been deleted.");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to delete outpost #" + String.valueOf(outpostOldId) + ". It might not exist anymore.");
        }
    }

    private String formatTime(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private String formatTimeApprox(long millis) {
        if (millis < 0) return "expired";
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        if (seconds < 1) return "<1s"; // Handle very short durations
        if (seconds < 60) return String.valueOf(seconds) + "s";
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 60) return String.valueOf(minutes) + "m " + String.valueOf(seconds % 60) + "s";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) return String.valueOf(hours) + "h " + String.valueOf(minutes % 60) + "m";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        return String.valueOf(days) + "d " + String.valueOf(hours % 24) + "h";
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "help", "create", "list", "who", "info", "invite", "uninvite", "kick", "leave", "accept",
                    "claim", "unclaim", "sethome", "home", "power", "promote", "demote", "leader", "owner",
                    "trust", "untrust", "vault", "ally", "unally", "neutral", "allyaccept", "allyrequests",
                    "enemy", "disband", "chat", "c", "outpost"
            );
            subCommands.stream().filter(s -> s.startsWith(currentArg)).forEach(completions::add);
        } else if (args.length >= 2) {
            String mainSubCmd = args[0].toLowerCase();

            if (mainSubCmd.equals("outpost")) {
                if (args.length == 2) {
                    Arrays.asList("create", "sethome", "home", "delete", "help").stream()
                            .filter(s -> s.startsWith(currentArg)).forEach(completions::add);
                } else if (args.length == 3 && Arrays.asList("sethome", "home", "delete").contains(args[1].toLowerCase())) {
                    if (playerFaction != null) {
                        playerFaction.getOutposts().stream()
                                .map(outpost -> String.valueOf(outpost.getOutpostID()))
                                .filter(idStr -> idStr.startsWith(currentArg))
                                .forEach(completions::add);
                    }
                }
            } else if (Arrays.asList("invite", "uninvite", "kick", "promote", "demote", "leader", "owner", "trust", "untrust").contains(mainSubCmd) && args.length == 2) {
                if (Arrays.asList("kick", "promote", "demote", "leader", "owner").contains(mainSubCmd) && playerFaction != null) {
                    playerFaction.getMembers().keySet().stream()
                            .map(Bukkit::getOfflinePlayer)
                            .filter(Objects::nonNull)
                            .map(OfflinePlayer::getName)
                            .filter(name -> name != null && name.toLowerCase().startsWith(currentArg))
                            .forEach(completions::add);
                } else {
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.equals(player))
                            .map(Player::getName)
                            .filter(name -> name != null && name.toLowerCase().startsWith(currentArg))
                            .forEach(completions::add);
                }
            } else if (Arrays.asList("who", "info", "home", "ally", "unally", "neutral", "enemy", "accept", "allyaccept").contains(mainSubCmd) && args.length == 2) {
                plugin.getFactionsByNameKey().values().stream()
                        .map(Faction::getName)
                        .filter(name -> name != null && name.toLowerCase().startsWith(currentArg))
                        .forEach(completions::add);
            } else if (mainSubCmd.equals("power") && args.length == 2) {
                plugin.getFactionsByNameKey().values().stream()
                        .map(Faction::getName)
                        .filter(name -> name != null && name.toLowerCase().startsWith(currentArg))
                        .forEach(completions::add);
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name != null && name.toLowerCase().startsWith(currentArg))
                        .forEach(completions::add);
            }
        }
        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions.stream().distinct().collect(Collectors.toList());
    }
}
