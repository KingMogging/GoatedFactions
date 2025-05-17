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
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class FactionCommand implements CommandExecutor, TabCompleter {

    private final GFactionsPlugin plugin;

    public FactionCommand(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendPlayerHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

        if (subCommand.equals("outpost")) {
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
                    handleOutpostHome(player, playerFaction, outpostIdentifier); // This was the missing method error
                    break;
                case "delete":
                    handleOutpostDelete(player, playerFaction, outpostIdentifier);
                    break;
                default:
                    sendOutpostHelp(player);
                    break;
            }
            return true;
        }

        switch (subCommand) {
            case "create":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f create <name>"); return true; }
                handleCreateFaction(player, args[1]);
                break;
            case "list":
                handleListFactions(player, args.length > 1 ? args[1] : "1");
                break;
            case "who":
            case "info":
                String whoFactionName = (args.length > 1) ? args[1] : (playerFaction != null ? playerFaction.getName() : null);
                handleWhoFaction(player, whoFactionName);
                break;
            case "invite":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f invite <player>"); return true; }
                handleInvitePlayer(player, playerFaction, args[1]);
                break;
            case "uninvite":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f uninvite <player>"); return true; }
                handleUninvitePlayer(player, playerFaction, args[1]);
                break;
            case "kick":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f kick <playerName>"); return true; }
                handleKick(player, playerFaction, args[1]);
                break;
            case "leave":
                handleLeave(player, playerFaction);
                break;
            case "accept":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f accept <factionName>"); return true; }
                handleAcceptMemberInvite(player, args[1]);
                break;
            case "claim":
                handleClaimChunk(player, playerFaction);
                break;
            case "unclaim":
                handleUnclaimPlayer(player, playerFaction);
                break;
            case "sethome":
                handleSetHome(player, playerFaction);
                break;
            case "home":
                String targetHomeFactionName = (args.length > 1) ? args[1] : null;
                handleHome(player, playerFaction, targetHomeFactionName, false);
                break;
            case "power":
                handlePowerCheck(player, playerFaction);
                break;
            case "promote":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f promote <playerName>"); return true; }
                handlePromote(player, playerFaction, args[1]);
                break;
            case "demote":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f demote <playerName>"); return true; }
                handleDemote(player, playerFaction, args[1]);
                break;
            case "leader":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f leader <newLeaderName>"); return true; }
                handleLeaderTransfer(player, playerFaction, args[1]);
                break;
            case "trust":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f trust <playerName>"); return true; }
                handleTrust(player, playerFaction, args[1]);
                break;
            case "untrust":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f untrust <playerName>"); return true; }
                handleUntrust(player, playerFaction, args[1]);
                break;
            case "vault":
                handleVault(player, playerFaction);
                break;
            case "ally":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f ally <factionName>"); return true; }
                handleAllyRequest(player, playerFaction, args[1]);
                break;
            case "unally":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f unally <factionName>"); return true; }
                handleUnally(player, playerFaction, args[1]);
                break;
            case "allyaccept":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f allyaccept <requestingFactionName>"); return true; }
                handleAllyAccept(player, playerFaction, args[1]);
                break;
            case "allyrequests":
                handleAllyRequestsList(player, playerFaction);
                break;
            case "enemy":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f enemy <factionName>"); return true; }
                handleEnemyDeclare(player, playerFaction, args[1]);
                break;
            case "neutral":
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /f neutral <factionName>"); return true; }
                handleNeutralDeclare(player, playerFaction, args[1]);
                break;
            case "disband":
                handleDisband(player, playerFaction);
                break;
            case "chat":
            case "c":
                handleFactionChatToggle(player, playerFaction);
                break;
            default:
                sendPlayerHelp(player);
                break;
        }
        return true;
    }

    private void sendPlayerHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "--- " + plugin.getName() + " Player Help ---");
        player.sendMessage(ChatColor.YELLOW + "/f create <name>" + ChatColor.GRAY + " - Create a faction (claims current chunk, sets spawnblock).");
        player.sendMessage(ChatColor.YELLOW + "/f list [page]" + ChatColor.GRAY + " - List all factions.");
        player.sendMessage(ChatColor.YELLOW + "/f who [faction]" + ChatColor.GRAY + " - Show faction info (alias: /f info).");
        player.sendMessage(ChatColor.YELLOW + "/f invite <player>" + ChatColor.GRAY + " - Invite (Owner/Admin, expires " + plugin.EXPIRATION_MEMBER_INVITE_MINUTES + "m).");
        player.sendMessage(ChatColor.YELLOW + "/f uninvite <player>" + ChatColor.GRAY + " - Revoke member invite (Owner/Admin).");
        player.sendMessage(ChatColor.YELLOW + "/f kick <player>" + ChatColor.GRAY + " - Kick member (Owner/Admin).");
        player.sendMessage(ChatColor.YELLOW + "/f leave" + ChatColor.GRAY + " - Leave your current faction.");
        player.sendMessage(ChatColor.YELLOW + "/f accept <faction>" + ChatColor.GRAY + " - Accept a faction member invite.");
        player.sendMessage(ChatColor.YELLOW + "/f claim" + ChatColor.GRAY + " - Claim chunk (Owner/Admin, Cost: " + plugin.COST_CLAIM_CHUNK + "P, must be adjacent & connected to spawn/outpost).");
        player.sendMessage(ChatColor.YELLOW + "/f unclaim" + ChatColor.GRAY + " - Unclaim current chunk (Owner/Admin, cannot unclaim main spawnblock chunk).");
        player.sendMessage(ChatColor.YELLOW + "/f sethome" + ChatColor.GRAY + " - Set main faction spawnblock (Owner only, in claimed territory).");
        player.sendMessage(ChatColor.YELLOW + "/f home [faction]" + ChatColor.GRAY + " - TP to main spawnblock (" + plugin.TELEPORT_WARMUP_SECONDS + "s warmup).");
        player.sendMessage(ChatColor.YELLOW + "/f outpost ..." + ChatColor.GRAY + " - Manage faction outposts (see /f outpost help).");
        player.sendMessage(ChatColor.YELLOW + "/f power" + ChatColor.GRAY + " - Show faction power/relations.");
        player.sendMessage(ChatColor.YELLOW + "/f promote <player>" + ChatColor.GRAY + " - Promote to Admin (Owner, Cost: " + plugin.COST_PROMOTE_MEMBER + "P).");
        player.sendMessage(ChatColor.YELLOW + "/f demote <player>" + ChatColor.GRAY + " - Demote Admin to Member (Owner).");
        player.sendMessage(ChatColor.YELLOW + "/f leader <player>" + ChatColor.GRAY + " - Transfer ownership to member (Owner).");
        player.sendMessage(ChatColor.YELLOW + "/f trust <player>" + ChatColor.GRAY + " - Trust non-member (Owner/Admin, Cost: " + plugin.COST_TRUST_PLAYER + "P).");
        player.sendMessage(ChatColor.YELLOW + "/f untrust <player>" + ChatColor.GRAY + " - Untrust player (Owner/Admin).");
        player.sendMessage(ChatColor.YELLOW + "/f vault" + ChatColor.GRAY + " - Open faction vault (Owner/Admin).");
        player.sendMessage(ChatColor.YELLOW + "/f ally <faction>" + ChatColor.GRAY + " - Send ally request (Owner/Admin, Cost: " + plugin.COST_SEND_ALLY_REQUEST + "P, expires " + plugin.EXPIRATION_ALLY_REQUEST_MINUTES + "m).");
        player.sendMessage(ChatColor.YELLOW + "/f unally <faction>" + ChatColor.GRAY + " - Break alliance (Owner/Admin).");
        player.sendMessage(ChatColor.YELLOW + "/f allyaccept <faction>" + ChatColor.GRAY + " - Accept ally request (Owner/Admin).");
        player.sendMessage(ChatColor.YELLOW + "/f allyrequests" + ChatColor.GRAY + " - List pending ally requests.");
        player.sendMessage(ChatColor.YELLOW + "/f enemy <faction>" + ChatColor.GRAY + " - Declare enemy (Owner/Admin, Cost: " + plugin.COST_DECLARE_ENEMY + "P).");
        player.sendMessage(ChatColor.YELLOW + "/f neutral <faction>" + ChatColor.GRAY + " - Declare neutral (Owner/Admin, Cost: " + plugin.COST_DECLARE_NEUTRAL + "P, " + plugin.COOLDOWN_ENEMY_NEUTRAL_HOURS + "h CD).");
        player.sendMessage(ChatColor.YELLOW + "/f disband" + ChatColor.GRAY + " - Disband your faction (Owner only).");
        player.sendMessage(ChatColor.YELLOW + "/f chat (or /f c)" + ChatColor.GRAY + " - Toggle faction-only chat.");
        if (player.hasPermission("goatedfactions.admin")) {
            player.sendMessage(ChatColor.RED + "Admin commands: /fa help");
        }
    }

    private void sendOutpostHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "--- Faction Outpost Help ---");
        player.sendMessage(ChatColor.YELLOW + "/f outpost create" + ChatColor.GRAY + " - Create an outpost in current chunk (Owner/Admin, Cost: " + plugin.COST_CREATE_OUTPOST + "P, Limit: " + plugin.MAX_OUTPOSTS_PER_FACTION + ").");
        player.sendMessage(ChatColor.YELLOW + "/f outpost sethome [id]" + ChatColor.GRAY + " - Set spawn for an outpost (Owner/Admin, in outpost chunk). Default 1 if only one outpost.");
        player.sendMessage(ChatColor.YELLOW + "/f outpost home [id]" + ChatColor.GRAY + " - Teleport to an outpost's spawn (" + plugin.TELEPORT_WARMUP_SECONDS + "s warmup). Default 1 if only one outpost.");
        player.sendMessage(ChatColor.YELLOW + "/f outpost delete [id]" + ChatColor.GRAY + " - Deletes an outpost and unclaims its chunk (Owner/Admin). Default 1 if only one outpost.");
    }

    private void handleCreateFaction(Player player, String name) {
        if (plugin.getFactionByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(ChatColor.RED + "You are already in a faction.");
            return;
        }
        if (name.length() < plugin.FACTION_NAME_MIN_LENGTH || name.length() > plugin.FACTION_NAME_MAX_LENGTH) {
            player.sendMessage(ChatColor.RED + "Faction name must be " + plugin.FACTION_NAME_MIN_LENGTH + "-" + plugin.FACTION_NAME_MAX_LENGTH + " characters.");
            return;
        }
        if (!plugin.FACTION_NAME_PATTERN.matcher(name).matches()) {
            player.sendMessage(ChatColor.RED + "Faction name contains invalid characters. Allowed: letters, numbers, underscore.");
            return;
        }
        if (plugin.getFaction(name) != null) {
            player.sendMessage(ChatColor.RED + "A faction with that name already exists.");
            return;
        }

        if (plugin.PREVENT_CLAIM_NEAR_SPAWN && plugin.SERVER_SPAWN_LOCATION != null) {
            if (!player.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                Chunk currentChunk = player.getLocation().getChunk();
                Location chunkCenterForCheck = new Location(currentChunk.getWorld(), currentChunk.getX() * 16 + 8, player.getLocation().getY(), currentChunk.getZ() * 16 + 8);
                if (Objects.equals(plugin.SERVER_SPAWN_LOCATION.getWorld(), chunkCenterForCheck.getWorld()) &&
                        plugin.SERVER_SPAWN_LOCATION.distanceSquared(chunkCenterForCheck) < (plugin.SPAWN_PROTECTION_RADIUS * plugin.SPAWN_PROTECTION_RADIUS)) {
                    player.sendMessage(ChatColor.RED + "You cannot create a faction (and claim your first chunk) this close to server spawn ("+ plugin.SPAWN_PROTECTION_RADIUS +" blocks).");
                    return;
                }
            }
        }

        if (plugin.POWER_INITIAL < plugin.COST_CLAIM_CHUNK) {
            player.sendMessage(ChatColor.RED + "The server's initial faction power ("+plugin.POWER_INITIAL+") is less than the cost to claim the first chunk ("+plugin.COST_CLAIM_CHUNK+"). Faction creation aborted. Contact an admin.");
            return;
        }

        if (plugin.createFaction(player, name)) {
            Faction newFac = plugin.getFaction(name);
            if (newFac != null) {
                player.sendMessage(ChatColor.GREEN + "Faction '" + ChatColor.AQUA + name + ChatColor.GREEN + "' created! Spawnblock set and chunk claimed. (Initial Claim Cost: " + plugin.COST_CLAIM_CHUNK + "P)");
                player.sendMessage(ChatColor.GREEN + "Current Power: " + newFac.getCurrentPower() + "/" + newFac.getMaxPower());
            } else {
                player.sendMessage(ChatColor.RED + "Faction '" + name + "' was marked as created, but could not be retrieved. Please contact an admin.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Could not create faction (name taken, in faction, not enough power for initial claim, or other issue).");
        }
    }

    private void handleListFactions(Player player, String pageArg) {
        int page;
        try { page = Integer.parseInt(pageArg); if (page < 1) page = 1; }
        catch (NumberFormatException e) { page = 1; }

        List<Faction> allFactions = new ArrayList<>(plugin.getFactionsByNameKey().values());
        if (allFactions.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "There are no factions on the server yet!"); return; }

        allFactions.sort(Comparator.comparingInt(Faction::getCurrentPower).reversed()
                .thenComparingInt(Faction::getTotalSize).reversed()
                .thenComparing(Faction::getName, String.CASE_INSENSITIVE_ORDER));

        int factionsPerPage = 8;
        int totalPages = (int) Math.ceil((double) allFactions.size() / factionsPerPage);
        if (totalPages == 0) totalPages = 1;
        if (page > totalPages) page = totalPages;

        player.sendMessage(ChatColor.GOLD + "--- Faction List (Page " + page + "/" + totalPages + ") ---");
        int startIndex = (page - 1) * factionsPerPage;
        for (int i = 0; i < factionsPerPage; i++) {
            int currentIndex = startIndex + i;
            if (currentIndex >= allFactions.size()) break;
            Faction fac = allFactions.get(currentIndex);
            player.sendMessage(ChatColor.AQUA.toString() + (currentIndex + 1) + ". " + fac.getName() +
                    ChatColor.GRAY + " - P: " + ChatColor.GREEN + fac.getCurrentPower() + "/" + fac.getMaxPower() +
                    ChatColor.GRAY + ", Mbrs: " + ChatColor.WHITE + fac.getTotalSize() +
                    ChatColor.GRAY + ", Claims: " + ChatColor.WHITE + fac.getClaimedChunks().size() +
                    ChatColor.GRAY + ", Outposts: " + ChatColor.WHITE + fac.getOutposts().size());
        }
        if (page < totalPages) {
            player.sendMessage(ChatColor.YELLOW + "Type /f list " + (page + 1) + " for the next page.");
        }
    }

    private void handleWhoFaction(Player player, @Nullable String targetFactionNameArg) {
        Faction targetFaction;
        if (targetFactionNameArg == null) {
            targetFaction = plugin.getFactionByPlayer(player.getUniqueId());
            if (targetFaction == null) {
                player.sendMessage(ChatColor.RED + "You are not in a faction. Usage: /f who <factionName>");
                return;
            }
        } else {
            targetFaction = plugin.getFaction(targetFactionNameArg);
            if (targetFaction == null) {
                player.sendMessage(ChatColor.RED + "Faction '" + targetFactionNameArg + "' not found.");
                return;
            }
        }

        player.sendMessage(ChatColor.GOLD + "---------- " + ChatColor.AQUA + ChatColor.BOLD + targetFaction.getName() + ChatColor.GOLD + " ----------");
        OfflinePlayer ownerOffline = Bukkit.getOfflinePlayer(targetFaction.getOwnerUUID());
        String ownerName = ownerOffline.getName() != null ? ownerOffline.getName() : "UUID: " + targetFaction.getOwnerUUID().toString().substring(0,8) + " (Offline)";
        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + ownerName);
        player.sendMessage(ChatColor.YELLOW + "Power: " + ChatColor.GREEN + targetFaction.getCurrentPower() + ChatColor.GRAY + "/" + ChatColor.GREEN + targetFaction.getMaxPower());
        player.sendMessage(ChatColor.YELLOW + "Claims: " + ChatColor.WHITE + targetFaction.getClaimedChunks().size() +
                (plugin.MAX_CLAIMS_PER_FACTION > 0 ? ChatColor.GRAY + "/" + plugin.MAX_CLAIMS_PER_FACTION : ""));

        Location homeLoc = targetFaction.getHomeLocation();
        if (homeLoc != null && homeLoc.getWorld() != null && targetFaction.getSpawnBlockChunk() != null) {
            player.sendMessage(ChatColor.YELLOW + "Spawnblock (Home): " + ChatColor.WHITE + homeLoc.getBlockX() + ", " + homeLoc.getBlockY() + ", " + homeLoc.getBlockZ() +
                    " (" + homeLoc.getWorld().getName() + ") in Chunk: " + targetFaction.getSpawnBlockChunk().toStringShort());
        } else {
            player.sendMessage(ChatColor.YELLOW + "Spawnblock (Home): " + ChatColor.GRAY + (targetFaction.getSpawnBlockChunk() != null ? "Chunk: " + targetFaction.getSpawnBlockChunk().toStringShort() + ChatColor.RED + " (Location invalid or world unloaded!)" : ChatColor.RED + "Not set/Chunk missing!"));
        }

        if (!targetFaction.getOutposts().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Outposts (" + targetFaction.getOutposts().size() + "/" + plugin.MAX_OUTPOSTS_PER_FACTION + "):");
            for (int i = 0; i < targetFaction.getOutposts().size(); i++) {
                Outpost outpost = targetFaction.getOutposts().get(i);
                Location outpostHome = outpost.getOutpostSpawnLocation();
                if (outpostHome != null && outpostHome.getWorld() != null && outpost.getChunkWrapper() != null) {
                    player.sendMessage(ChatColor.GRAY + "  - #" + (i+1) + " at " + outpostHome.getBlockX() + "," + outpostHome.getBlockY() + "," + outpostHome.getBlockZ() + " (" + outpostHome.getWorld().getName() + ") in chunk " + outpost.getChunkWrapper().toStringShort());
                } else {
                    player.sendMessage(ChatColor.GRAY + "  - #" + (i+1) + " at chunk " + (outpost.getChunkWrapper() != null ? outpost.getChunkWrapper().toStringShort() : "Unknown") + ChatColor.RED + " (Spawn Location invalid or world unloaded!)");
                }
            }
        }

        List<String> adminNames = new ArrayList<>();
        List<String> memberNames = new ArrayList<>();
        targetFaction.getMembers().forEach((uuid, rank) -> {
            if (uuid.equals(targetFaction.getOwnerUUID())) return;
            OfflinePlayer offlineMember = Bukkit.getOfflinePlayer(uuid);
            String name = offlineMember.getName() != null ? offlineMember.getName() : "OfflinePlayer (" + uuid.toString().substring(0,4) + ")";
            if (rank == FactionRank.ADMIN) adminNames.add(name);
            else if (rank == FactionRank.MEMBER) memberNames.add(name);
        });
        if (!adminNames.isEmpty()) player.sendMessage(ChatColor.YELLOW + "Admins ("+adminNames.size()+"): " + ChatColor.WHITE + String.join(", ", adminNames));
        if (!memberNames.isEmpty()) player.sendMessage(ChatColor.YELLOW + "Members ("+memberNames.size()+"): " + ChatColor.WHITE + String.join(", ", memberNames));

        if (targetFaction.getTotalSize() == 1 && adminNames.isEmpty() && memberNames.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.GRAY + "Just the owner");
        } else if (adminNames.isEmpty() && memberNames.isEmpty() && targetFaction.getTotalSize() > 1) {
            player.sendMessage(ChatColor.YELLOW + "Members: " + ChatColor.GRAY + (targetFaction.getTotalSize() -1) + " other(s) (not detailed)");
        }

        player.sendMessage(ChatColor.YELLOW + "Total Size: " + ChatColor.WHITE + targetFaction.getTotalSize() +
                (plugin.MAX_MEMBERS_PER_FACTION > 0 ? ChatColor.GRAY + "/" + (plugin.MAX_MEMBERS_PER_FACTION + 1) : ""));

        if (!targetFaction.getAllyFactionKeys().isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "Allies (" + targetFaction.getAllyFactionKeys().size() + "): " + ChatColor.WHITE + targetFaction.getAllyFactionKeys().stream().map(key -> { Faction f = plugin.getFaction(key); return f != null ? f.getName() : key; }).collect(Collectors.joining(", ")));
        }
        if (!targetFaction.getEnemyFactionKeys().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Enemies (" + targetFaction.getEnemyFactionKeys().size() + "): " + ChatColor.WHITE + targetFaction.getEnemyFactionKeys().stream().map(key -> { Faction f = plugin.getFaction(key); return f != null ? f.getName() : key; }).collect(Collectors.joining(", ")));
        }
        if (!targetFaction.getTrustedPlayers().isEmpty()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Trusted Players (" + targetFaction.getTrustedPlayers().size() + "): " + ChatColor.WHITE + targetFaction.getTrustedPlayers().stream().map(uuid -> { OfflinePlayer op = Bukkit.getOfflinePlayer(uuid); return op.getName() != null ? op.getName() : "Offline ("+uuid.toString().substring(0,4)+")"; }).filter(Objects::nonNull).collect(Collectors.joining(", ")));
        }
        player.sendMessage(ChatColor.GOLD + "------------------------------");
    }

    private void handleInvitePlayer(Player p, @Nullable Faction pf, String invitedName) {
        if (pf == null) { p.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!pf.isOwner(p.getUniqueId()) && !pf.isAdmin(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Only the faction Owner or an Admin can invite new members."); return;
        }
        if (plugin.MAX_MEMBERS_PER_FACTION > 0 && pf.getMembers().size() >= (plugin.MAX_MEMBERS_PER_FACTION + 1)) {
            p.sendMessage(ChatColor.RED + "Your faction has reached the maximum member limit of " + plugin.MAX_MEMBERS_PER_FACTION + " (excluding owner)."); return;
        }

        Player invitedP = Bukkit.getPlayerExact(invitedName);
        if (invitedP == null) { p.sendMessage(ChatColor.RED + "Player '" + invitedName + "' not found online."); return; }
        if (p.equals(invitedP)) { p.sendMessage(ChatColor.RED + "You cannot invite yourself."); return;}
        if (plugin.getFactionByPlayer(invitedP.getUniqueId()) != null) { p.sendMessage(ChatColor.RED + invitedName + " is already in a faction."); return; }

        Map<UUID, Long> existingInvites = plugin.getPendingMemberInvitesForFaction(pf.getNameKey());
        if (existingInvites.containsKey(invitedP.getUniqueId())) {
            long timeRemaining = plugin.EXPIRATION_MEMBER_INVITE_MINUTES * 60000L - (System.currentTimeMillis() - existingInvites.get(invitedP.getUniqueId()));
            p.sendMessage(ChatColor.YELLOW + invitedName + " already has an active invite from your faction. Expires in ~" + formatTimeApprox(timeRemaining));
            return;
        }
        if (plugin.sendMemberInvite(pf, invitedP.getUniqueId())) {
            p.sendMessage(ChatColor.GREEN + "Member invitation sent to " + invitedP.getName() + ". It will expire in " + plugin.EXPIRATION_MEMBER_INVITE_MINUTES + " minutes.");
            invitedP.sendMessage(ChatColor.AQUA + p.getName() + " from faction '" + pf.getName() + "' has invited you to join. Type " + ChatColor.YELLOW + "/f accept " + pf.getName() + ChatColor.AQUA + " within " + plugin.EXPIRATION_MEMBER_INVITE_MINUTES + " minutes to accept.");
        } else { p.sendMessage(ChatColor.RED + "Could not invite " + invitedP.getName() + ". They might already have an invite or another issue occurred."); }
    }

    private void handleUninvitePlayer(Player player, @Nullable Faction playerFaction, String targetPlayerName) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the faction Owner or an Admin can uninvite players."); return;
        }

        OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUUIDToRevoke = null;

        if (targetOffline.hasPlayedBefore() || targetOffline.isOnline()) {
            if (plugin.getPendingMemberInvitesForFaction(playerFaction.getNameKey()).containsKey(targetOffline.getUniqueId())) {
                targetUUIDToRevoke = targetOffline.getUniqueId();
            }
        }

        if (targetUUIDToRevoke == null) {
            for (Map.Entry<UUID, Long> entry : plugin.getPendingMemberInvitesForFaction(playerFaction.getNameKey()).entrySet()) {
                OfflinePlayer invitedOp = Bukkit.getOfflinePlayer(entry.getKey());
                if (invitedOp.getName() != null && invitedOp.getName().equalsIgnoreCase(targetPlayerName)) {
                    targetUUIDToRevoke = entry.getKey();
                    targetOffline = invitedOp;
                    break;
                }
            }
        }

        if (targetUUIDToRevoke == null) {
            player.sendMessage(ChatColor.RED + "Player '" + targetPlayerName + "' not found or has no active invite from your faction.");
            return;
        }

        plugin.revokeMemberInvite(playerFaction, targetOffline);
        player.sendMessage(ChatColor.GREEN + "Revoked invitation for " + (targetOffline.getName() != null ? targetOffline.getName() : targetPlayerName) + ".");
        if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
            targetOffline.getPlayer().sendMessage(ChatColor.YELLOW + "Your invitation to join '" + playerFaction.getName() + "' has been revoked by " + player.getName() + ".");
        }
    }

    private void handleAcceptMemberInvite(Player p, String facName) {
        if (plugin.getFactionByPlayer(p.getUniqueId()) != null) { p.sendMessage(ChatColor.RED + "You are already in a faction."); return; }
        Faction facToJoin = plugin.getFaction(facName);
        if (facToJoin == null) { p.sendMessage(ChatColor.RED + "Faction '" + facName + "' does not exist or the name is misspelled."); return; }

        if (plugin.acceptMemberInvite(p.getUniqueId(), facToJoin.getNameKey())) {
            p.sendMessage(ChatColor.GREEN + "You have joined faction '" + ChatColor.AQUA + facToJoin.getName() + ChatColor.GREEN + "'! Current Power: " + facToJoin.getCurrentPower() + "/" + facToJoin.getMaxPower());
            plugin.notifyFaction(facToJoin, ChatColor.AQUA + p.getName() + ChatColor.GREEN + " has joined the faction! Power: " + facToJoin.getCurrentPower() + "/" + facToJoin.getMaxPower(), null);
        } else { p.sendMessage(ChatColor.RED + "Could not join faction '" + facToJoin.getName() + "'. The invite may have expired, the faction might be full, or another error occurred."); }
    }

    private void handleLeave(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (playerFaction.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "The faction Owner cannot leave. Use " + ChatColor.YELLOW + "/f disband" + ChatColor.RED + " or " + ChatColor.YELLOW + "/f leader <newLeaderName>" + ChatColor.RED + ".");
            return;
        }
        String formerFactionName = playerFaction.getName();
        if (plugin.removePlayerFromFaction(playerFaction, player.getUniqueId(), false)) {
            player.sendMessage(ChatColor.GREEN + "You have successfully left faction '" + ChatColor.AQUA + formerFactionName + ChatColor.GREEN + "'.");
            plugin.notifyFaction(playerFaction, ChatColor.YELLOW + player.getName() + " has left the faction. Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower(), player.getUniqueId());
        } else { player.sendMessage(ChatColor.RED + "An error occurred while trying to leave the faction."); }
    }

    private void handleKick(Player kicker, @Nullable Faction kickerFaction, String targetName) {
        if (kickerFaction == null) { kicker.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!kickerFaction.isOwner(kicker.getUniqueId()) && !kickerFaction.isAdmin(kicker.getUniqueId())) {
            kicker.sendMessage(ChatColor.RED + "Only the faction Owner or an Admin can kick members."); return;
        }

        OfflinePlayer targetOffline = null;
        UUID targetUUID = null;
        for(Map.Entry<UUID, FactionRank> entry : kickerFaction.getMembers().entrySet()){
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            if(op != null && op.getName() != null && op.getName().equalsIgnoreCase(targetName)){
                targetOffline = op;
                targetUUID = entry.getKey();
                break;
            }
        }

        if (targetUUID == null ) {
            kicker.sendMessage(ChatColor.RED + "Player '" + targetName + "' not found in your faction."); return;
        }

        if (kickerFaction.isOwner(targetUUID)) { kicker.sendMessage(ChatColor.RED + "You cannot kick the Owner of the faction."); return; }

        FactionRank kickerRank = kickerFaction.getRank(kicker.getUniqueId());
        FactionRank targetRank = kickerFaction.getRank(targetUUID);
        if (kickerRank == FactionRank.ADMIN && (targetRank == FactionRank.OWNER || targetRank == FactionRank.ADMIN)) {
            kicker.sendMessage(ChatColor.RED + "Admins cannot kick the Owner or other Admins. Only the Owner can kick Admins."); return;
        }

        if (plugin.removePlayerFromFaction(kickerFaction, targetUUID, true)) {
            String kickedPlayerNameDisplay = targetOffline.getName();
            kicker.sendMessage(ChatColor.GREEN + kickedPlayerNameDisplay + " has been kicked from the faction.");
            plugin.notifyFaction(kickerFaction, ChatColor.YELLOW + kickedPlayerNameDisplay + " was kicked from the faction by " + kicker.getName() + ". Power: " + kickerFaction.getCurrentPower() + "/" + kickerFaction.getMaxPower(), kicker.getUniqueId());
            if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
                targetOffline.getPlayer().sendMessage(ChatColor.RED + "You have been kicked from faction '" + kickerFaction.getName() + "' by " + kicker.getName() + ".");
            }
        } else { kicker.sendMessage(ChatColor.RED + "Could not kick " + (targetOffline != null && targetOffline.getName() != null ? targetOffline.getName() : targetName) + ". An error occurred."); }
    }

    private void handleClaimChunk(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You must be in a faction to claim land."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the faction Owner or an Admin can claim land."); return;
        }

        Chunk chunkToClaim = player.getLocation().getChunk();
        ChunkWrapper chunkWrapper = new ChunkWrapper(chunkToClaim.getWorld().getName(), chunkToClaim.getX(), chunkToClaim.getZ());

        if (plugin.PREVENT_CLAIM_NEAR_SPAWN && plugin.SERVER_SPAWN_LOCATION != null) {
            if (!player.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                Location chunkCenter = new Location(chunkToClaim.getWorld(), chunkToClaim.getX() * 16 + 8, player.getLocation().getY(), chunkToClaim.getZ() * 16 + 8);
                if (Objects.equals(plugin.SERVER_SPAWN_LOCATION.getWorld(), chunkCenter.getWorld()) &&
                        plugin.SERVER_SPAWN_LOCATION.distanceSquared(chunkCenter) < plugin.SPAWN_PROTECTION_RADIUS * plugin.SPAWN_PROTECTION_RADIUS) {
                    player.sendMessage(ChatColor.RED + "You cannot claim land this close to server spawn ("+plugin.SPAWN_PROTECTION_RADIUS+" blocks).");
                    return;
                }
            }
        }

        String currentOwnerNameKey = plugin.getClaimedChunksMap().get(chunkWrapper);
        boolean isOverclaim = false;
        Faction targetFaction = null;

        if (currentOwnerNameKey != null) {
            if (currentOwnerNameKey.equalsIgnoreCase(playerFaction.getNameKey())) {
                player.sendMessage(ChatColor.YELLOW + "Your faction already owns this chunk."); return;
            }
            isOverclaim = true;
            targetFaction = plugin.getFaction(currentOwnerNameKey);
            if (targetFaction == null) {
                player.sendMessage(ChatColor.YELLOW + "This land is claimed by an unknown or disbanded faction. Forcibly overclaiming...");
            } else {
                if (playerFaction.getCurrentPower() < plugin.COST_OVERCLAIM_CHUNK) {
                    player.sendMessage(ChatColor.RED + "Not enough power to overclaim! (Need " + plugin.COST_OVERCLAIM_CHUNK + "P, Have " + playerFaction.getCurrentPower() + "P)"); return;
                }
                if (targetFaction.getCurrentPower() > 0) {
                    player.sendMessage(ChatColor.RED + "Cannot overclaim from " + targetFaction.getName() + " as they still have " + targetFaction.getCurrentPower() + " power."); return;
                }
                if (!playerFaction.isEnemy(targetFaction.getNameKey())) {
                    player.sendMessage(ChatColor.RED + "You must declare " + targetFaction.getName() + " as an enemy to overclaim their land when they have 0 power."); return;
                }
            }
        } else {
            if (playerFaction.getCurrentPower() < plugin.COST_CLAIM_CHUNK) {
                player.sendMessage(ChatColor.RED + "Not enough power to claim! (Need " + plugin.COST_CLAIM_CHUNK + "P, Have " + playerFaction.getCurrentPower() + "P)"); return;
            }
        }

        if (!isOverclaim) {
            if (playerFaction.getClaimedChunks().isEmpty() && playerFaction.getOutposts().isEmpty()) {
                player.sendMessage(ChatColor.RED + "Your faction has no land to claim adjacent to. Create an outpost or ensure your spawnblock is set.");
                return;
            }
            if (!playerFaction.isChunkAdjacentToExistingClaim(chunkWrapper)) {
                player.sendMessage(ChatColor.RED + "You can only claim chunks adjacent to your existing territory.");
                return;
            }
            if (!playerFaction.isConnectedToSpawnBlock(chunkWrapper, plugin)) {
                player.sendMessage(ChatColor.RED + "This claim would not be connected to your faction's spawnblock or an outpost.");
                return;
            }
        }


        if (plugin.MAX_CLAIMS_PER_FACTION > 0 && playerFaction.getClaimedChunks().size() >= plugin.MAX_CLAIMS_PER_FACTION && !isOverclaim) {
            player.sendMessage(ChatColor.RED + "Your faction has reached the maximum claim limit of " + plugin.MAX_CLAIMS_PER_FACTION + " chunks."); return;
        }

        if (plugin.claimChunk(playerFaction, chunkToClaim, isOverclaim, false)) {
            String messageAction = isOverclaim ? "OVERCLAIMED" : "claimed";
            int cost = isOverclaim ? plugin.COST_OVERCLAIM_CHUNK : plugin.COST_CLAIM_CHUNK;
            String fromFactionMsg = (isOverclaim && targetFaction != null) ? " from " + targetFaction.getName() : (isOverclaim ? " from a ghost faction" : "");

            player.sendMessage(ChatColor.GREEN + "Chunk (" + chunkToClaim.getX() + "," + chunkToClaim.getZ() + ") " + messageAction + fromFactionMsg + "! (Cost: " + cost + "P)");
            plugin.notifyFaction(playerFaction, ChatColor.GREEN + player.getName() + " " + messageAction + " land at (" + chunkToClaim.getX() + "," + chunkToClaim.getZ() + "). Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower(), player.getUniqueId());

            if (isOverclaim && targetFaction != null) {
                plugin.notifyFaction(targetFaction, ChatColor.DARK_RED + "Your land at (" + chunkToClaim.getX() + "," + chunkToClaim.getZ() + ") in world " + chunkToClaim.getWorld().getName() + " has been OVERCLAIMED by " + playerFaction.getName() + "!", null);
            }
            player.sendMessage(ChatColor.YELLOW + "Faction Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower());
        } else {
            player.sendMessage(ChatColor.RED + "Could not claim chunk. Check power, adjacency, connection to spawn, claim limits, or other restrictions.");
        }
    }

    private void handleUnclaimPlayer(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the faction Owner or an Admin can unclaim land."); return;
        }
        Chunk currentChunk = player.getLocation().getChunk();
        ChunkWrapper cw = new ChunkWrapper(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ());
        String ownerKey = plugin.getClaimedChunksMap().get(cw);

        if (ownerKey == null || !ownerKey.equals(playerFaction.getNameKey())) {
            player.sendMessage(ChatColor.RED + "Your faction does not own this chunk, or it is unclaimed."); return;
        }

        if (cw.equals(playerFaction.getSpawnBlockChunk())) {
            player.sendMessage(ChatColor.RED + "You cannot unclaim your faction's main spawnblock chunk. Use /f sethome elsewhere first or disband.");
            return;
        }

        Outpost outpost = playerFaction.getOutpost(cw);
        boolean wasOutpost = outpost != null;

        plugin.unclaimChunkPlayer(playerFaction, cw);
        player.sendMessage(ChatColor.GREEN + "Chunk (" + cw.getX() + "," + cw.getZ() + ") has been unclaimed." + (wasOutpost ? " The outpost here was removed." : ""));
        plugin.notifyFaction(playerFaction, ChatColor.YELLOW + player.getName() + " has unclaimed land at (" + cw.getX() + "," + cw.getZ() + ").", player.getUniqueId());
    }

    private void handleSetHome(Player p, @Nullable Faction pf) {
        if (pf == null) { p.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!pf.isOwner(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Only the faction Owner can set the faction spawnblock (home)."); return; }

        Chunk currentChunk = p.getLocation().getChunk();
        ChunkWrapper homeChunkWrapper = new ChunkWrapper(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ());

        if (!pf.getClaimedChunks().contains(homeChunkWrapper)) {
            p.sendMessage(ChatColor.RED + "You can only set your faction spawnblock inside your claimed territory."); return;
        }

        Outpost existingOutpostInNewHomeChunk = pf.getOutpost(homeChunkWrapper);
        if (existingOutpostInNewHomeChunk != null) {
            pf.removeOutpost(homeChunkWrapper);
            p.sendMessage(ChatColor.YELLOW + "The outpost in this chunk has been removed as it's now your main spawnblock.");
        }

        pf.setHomeLocation(p.getLocation());
        p.sendMessage(ChatColor.GREEN + "Faction spawnblock (home) for '" + ChatColor.AQUA + pf.getName() + ChatColor.GREEN + "' has been set to your current location!");
        plugin.notifyFaction(pf, ChatColor.AQUA + p.getName() + ChatColor.GREEN + " has updated the faction spawnblock (home).", p.getUniqueId());

        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionClaimsVisual(pf);
        }
    }

    private void handleHome(Player p, @Nullable Faction pf, @Nullable String targetFacNameArgument, boolean isOutpostHomeCmd) {
        Faction targetFaction;
        Location homeLoc;
        String homeTypeMsg;

        if (isOutpostHomeCmd) { // This logic path is for /f outpost home
            homeTypeMsg = "outpost home";
            if (pf == null) { p.sendMessage(ChatColor.RED + "You are not in a faction to teleport to its " + homeTypeMsg + "."); return; }
            targetFaction = pf;
            if (pf.getOutposts().isEmpty()) { p.sendMessage(ChatColor.RED + "Your faction does not have any outposts set."); return; }

            Outpost targetOutpost;
            int outpostIndex = -1; // For user messages
            if (targetFacNameArgument == null && pf.getOutposts().size() == 1) {
                targetOutpost = pf.getOutposts().get(0);
                outpostIndex = 0;
            } else if (targetFacNameArgument != null) {
                try {
                    outpostIndex = Integer.parseInt(targetFacNameArgument) - 1;
                    if (outpostIndex >= 0 && outpostIndex < pf.getOutposts().size()) {
                        targetOutpost = pf.getOutposts().get(outpostIndex);
                    } else {
                        p.sendMessage(ChatColor.RED + "Invalid outpost ID. Use /f who to see outpost IDs (e.g., #1, #2)."); return;
                    }
                } catch (NumberFormatException e) {
                    p.sendMessage(ChatColor.RED + "Invalid outpost ID format. Must be a number (e.g., 1, 2...)."); return;
                }
            } else {
                p.sendMessage(ChatColor.RED + "Your faction has multiple outposts. Please specify outpost ID: /f outpost home <id>"); return;
            }

            homeLoc = targetOutpost.getOutpostSpawnLocation();
            if (homeLoc == null || homeLoc.getWorld() == null) {
                p.sendMessage(ChatColor.RED + "Your faction's outpost #" + (outpostIndex+1) + " spawn is invalid or its world is not loaded."); return;
            }
            homeTypeMsg = "outpost #"+(outpostIndex+1)+" home";

        } else { // This logic path is for /f home [faction_name] (main home)
            homeTypeMsg = "faction spawnblock (home)";
            if (targetFacNameArgument == null) {
                if (pf == null) { p.sendMessage(ChatColor.RED + "You are not in a faction to teleport to its home."); return; }
                targetFaction = pf;
            } else {
                targetFaction = plugin.getFaction(targetFacNameArgument);
                if (targetFaction == null) { p.sendMessage(ChatColor.RED + "Faction '" + targetFacNameArgument + "' not found."); return; }

                boolean canTeleport = false;
                if (pf != null && pf.equals(targetFaction)) canTeleport = true;
                else if (pf != null && pf.isAlly(targetFaction.getNameKey())) canTeleport = true;
                else if (targetFaction.isTrusted(p.getUniqueId())) canTeleport = true;

                if (!canTeleport) {
                    p.sendMessage(ChatColor.RED + "You cannot teleport to " + targetFaction.getName() + "'s " + homeTypeMsg + "."); return;
                }
            }
            homeLoc = targetFaction.getHomeLocation();
            if (homeLoc == null || homeLoc.getWorld() == null) {
                p.sendMessage(ChatColor.RED + (targetFaction.equals(pf) ? "Your" : "Faction '" + targetFaction.getName() + "'s") + " " + homeTypeMsg + " is not set or its world is not loaded."); return;
            }
        }

        String successMsg = "Teleported to " + (targetFaction.equals(pf) ? "your" : "'" + targetFaction.getName() + "'s") + " " + homeTypeMsg + "!";
        plugin.initiateTeleportWarmup(p, homeLoc, successMsg);
    }

    private void handleOutpostCreate(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You must be in a faction to create an outpost."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the faction Owner or an Admin can create outposts."); return;
        }
        if (plugin.MAX_OUTPOSTS_PER_FACTION > 0 && playerFaction.getOutposts().size() >= plugin.MAX_OUTPOSTS_PER_FACTION) {
            player.sendMessage(ChatColor.RED + "Your faction has reached the maximum outpost limit of " + plugin.MAX_OUTPOSTS_PER_FACTION + "."); return;
        }

        Chunk outpostChunk = player.getLocation().getChunk();
        ChunkWrapper outpostChunkWrapper = new ChunkWrapper(outpostChunk.getWorld().getName(), outpostChunk.getX(), outpostChunk.getZ());

        if (plugin.getClaimedChunksMap().containsKey(outpostChunkWrapper)) {
            player.sendMessage(ChatColor.RED + "This chunk is already claimed. Outposts must be in unclaimed wilderness."); return;
        }
        if (plugin.PREVENT_CLAIM_NEAR_SPAWN && plugin.SERVER_SPAWN_LOCATION != null) {
            if (!player.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                Location chunkCenter = new Location(outpostChunk.getWorld(), outpostChunk.getX() * 16 + 8, player.getLocation().getY(), outpostChunk.getZ() * 16 + 8);
                if (Objects.equals(plugin.SERVER_SPAWN_LOCATION.getWorld(),chunkCenter.getWorld()) &&
                        plugin.SERVER_SPAWN_LOCATION.distanceSquared(chunkCenter) < plugin.SPAWN_PROTECTION_RADIUS * plugin.SPAWN_PROTECTION_RADIUS) {
                    player.sendMessage(ChatColor.RED + "You cannot create an outpost this close to server spawn (" + plugin.SPAWN_PROTECTION_RADIUS + " blocks).");
                    return;
                }
            }
        }

        if (playerFaction.getCurrentPower() < plugin.COST_CREATE_OUTPOST) {
            player.sendMessage(ChatColor.RED + "Not enough power to create an outpost! (Need " + plugin.COST_CREATE_OUTPOST + "P, Have " + playerFaction.getCurrentPower() + "P)"); return;
        }

        if (plugin.claimChunk(playerFaction, outpostChunk, false, true)) {
            Outpost newOutpost = new Outpost(player.getLocation());
            playerFaction.addOutpost(newOutpost);

            player.sendMessage(ChatColor.GREEN + "Outpost created in chunk " + outpostChunkWrapper.toStringShort() + "! Spawn set to your location. (Cost: " + plugin.COST_CREATE_OUTPOST + "P)");
            plugin.notifyFaction(playerFaction, ChatColor.GREEN + player.getName() + " created a new outpost! Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower(), player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Faction Power: " + playerFaction.getCurrentPower() + "/" + playerFaction.getMaxPower());
        } else {
            player.sendMessage(ChatColor.RED + "Could not create outpost. The chunk might have been claimed, or an error occurred.");
        }
    }

    private void handleOutpostSetHome(Player player, @Nullable Faction playerFaction, @Nullable String outpostIdentifier) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only Owner or Admin can set outpost homes."); return;
        }
        if (playerFaction.getOutposts().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your faction has no outposts."); return;
        }

        Outpost targetOutpost;
        int outpostIndex = -1;
        if (outpostIdentifier == null && playerFaction.getOutposts().size() == 1) {
            targetOutpost = playerFaction.getOutposts().get(0);
            outpostIndex = 0;
        } else if (outpostIdentifier != null) {
            try {
                outpostIndex = Integer.parseInt(outpostIdentifier) - 1;
                if (outpostIndex >= 0 && outpostIndex < playerFaction.getOutposts().size()) {
                    targetOutpost = playerFaction.getOutposts().get(outpostIndex);
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid outpost ID. Use /f who to see outpost IDs."); return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid outpost ID format. Must be a number. Use /f who to see outpost IDs."); return;
            }
        } else {
            player.sendMessage(ChatColor.RED + "Your faction has multiple outposts. Please specify outpost ID: /f outpost sethome <id>"); return;
        }

        Chunk currentChunk = player.getLocation().getChunk();
        if (!targetOutpost.getChunkWrapper().equals(new ChunkWrapper(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ()))) {
            player.sendMessage(ChatColor.RED + "You must be inside the outpost's claimed chunk ("+targetOutpost.getChunkWrapper().toStringShort()+") to set its home."); return;
        }

        targetOutpost.setOutpostSpawnLocation(player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Outpost #" + (outpostIndex + 1) + " spawn has been set to your current location!");
        plugin.notifyFaction(playerFaction, ChatColor.AQUA + player.getName() + " updated spawn for outpost #" + (outpostIndex + 1) + ".", player.getUniqueId());
        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionClaimsVisual(playerFaction);
        }
    }

    // This is the method that was reported as missing.
    private void handleOutpostHome(Player player, @Nullable Faction playerFaction, @Nullable String outpostIdentifier) {
        // This method body is identical to the one in handleHome when isOutpostHomeCmd is true.
        // It's called specifically by '/f outpost home'
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction to teleport to its outpost home."); return; }

        if (playerFaction.getOutposts().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your faction does not have any outposts set."); return;
        }

        Outpost targetOutpost;
        int outpostIndex = -1; // For user messages

        if (outpostIdentifier == null && playerFaction.getOutposts().size() == 1) {
            targetOutpost = playerFaction.getOutposts().get(0);
            outpostIndex = 0;
        } else if (outpostIdentifier != null) {
            try {
                outpostIndex = Integer.parseInt(outpostIdentifier) - 1;
                if (outpostIndex >= 0 && outpostIndex < playerFaction.getOutposts().size()) {
                    targetOutpost = playerFaction.getOutposts().get(outpostIndex);
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid outpost ID. Use /f who to see outpost IDs (e.g., #1, #2)."); return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid outpost ID format. Must be a number (e.g., 1, 2...)."); return;
            }
        } else { // No ID, multiple outposts exist
            player.sendMessage(ChatColor.RED + "Your faction has multiple outposts. Please specify outpost ID: /f outpost home <id>"); return;
        }

        Location outpostHomeLoc = targetOutpost.getOutpostSpawnLocation();
        if (outpostHomeLoc == null || outpostHomeLoc.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Your faction's outpost #" + (outpostIndex+1) + " spawn is invalid or its world is not loaded."); return;
        }
        String homeTypeMsg = "outpost #"+(outpostIndex+1)+" home";

        String successMsg = "Teleported to your " + homeTypeMsg + "!"; // Simplified for own outpost
        plugin.initiateTeleportWarmup(player, outpostHomeLoc, successMsg);
    }

    private void handleOutpostDelete(Player player, @Nullable Faction playerFaction, @Nullable String outpostIdentifier) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only Owner or Admin can delete outposts."); return;
        }
        if (playerFaction.getOutposts().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your faction has no outposts to delete."); return;
        }

        Outpost targetOutpost;
        int outpostIndex = -1;

        if (outpostIdentifier == null && playerFaction.getOutposts().size() == 1) {
            targetOutpost = playerFaction.getOutposts().get(0);
            outpostIndex = 0;
        } else if (outpostIdentifier != null) {
            try {
                outpostIndex = Integer.parseInt(outpostIdentifier) - 1;
                if (outpostIndex >= 0 && outpostIndex < playerFaction.getOutposts().size()) {
                    targetOutpost = playerFaction.getOutposts().get(outpostIndex);
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid outpost ID. Use /f who to see outpost IDs."); return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid outpost ID format. Must be a number. Use /f who to see outpost IDs."); return;
            }
        } else {
            player.sendMessage(ChatColor.RED + "Your faction has multiple outposts. Please specify outpost ID to delete: /f outpost delete <id>"); return;
        }

        ChunkWrapper outpostChunkWrapper = targetOutpost.getChunkWrapper();

        if (playerFaction.removeOutpost(outpostChunkWrapper)) {
            playerFaction.getClaimedChunks().remove(outpostChunkWrapper);
            plugin.getClaimedChunksMap().remove(outpostChunkWrapper);

            if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
                plugin.getDynmapManager().removeClaimFromMap(outpostChunkWrapper);
                plugin.getDynmapManager().updateFactionClaimsVisual(playerFaction);
            }
            player.sendMessage(ChatColor.GREEN + "Outpost #" + (outpostIndex + 1) + " at chunk " + outpostChunkWrapper.toStringShort() + " has been deleted and unclaimed.");
            plugin.notifyFaction(playerFaction, ChatColor.YELLOW + player.getName() + " deleted outpost #" + (outpostIndex + 1) + ".", player.getUniqueId());
        } else {
            player.sendMessage(ChatColor.RED + "Error deleting outpost #" + (outpostIndex+1) + ". It might not exist or an internal error occurred.");
        }
    }

    private void handlePowerCheck(Player p, @Nullable Faction pf) {
        if (pf == null) { p.sendMessage(ChatColor.RED + "You are not in a faction. Use /f who <factionName> to see other factions' power."); return; }
        p.sendMessage(ChatColor.AQUA + "--- " + pf.getName() + " Diplomatic & Power Status ---");
        p.sendMessage(ChatColor.YELLOW + "Current Power: " + ChatColor.GREEN + pf.getCurrentPower() + ChatColor.YELLOW + " / Max Power: " + ChatColor.GREEN + pf.getMaxPower());

        if (!pf.getEnemyFactionKeys().isEmpty()) {
            p.sendMessage(ChatColor.RED + "Enemies: " + pf.getEnemyFactionKeys().stream().map(nameKey -> {
                Faction enemyFac = plugin.getFaction(nameKey);
                String enemyName = (enemyFac != null ? enemyFac.getName() : nameKey);
                long enemyDeclareTime = pf.getEnemyDeclareTimestamps().getOrDefault(nameKey.toLowerCase(), 0L);
                long cooldownMillis = plugin.COOLDOWN_ENEMY_NEUTRAL_HOURS * 3600000L;
                long remainingCooldown = (enemyDeclareTime != 0) ? cooldownMillis - (System.currentTimeMillis() - enemyDeclareTime) : 0;
                String cdString = (remainingCooldown > 0) ? ChatColor.GRAY + " (CD: " + formatTime(remainingCooldown) + ")" : "";
                return enemyName + cdString;
            }).collect(Collectors.joining(", ")));
        } else { p.sendMessage(ChatColor.GREEN + "Enemies: None"); }

        if (!pf.getAllyFactionKeys().isEmpty()) {
            p.sendMessage(ChatColor.AQUA + "Allies: " + pf.getAllyFactionKeys().stream().map(key -> { Faction f = plugin.getFaction(key); return f != null ? f.getName() : key; }).collect(Collectors.joining(", ")));
        } else { p.sendMessage(ChatColor.AQUA + "Allies: None"); }
    }

    private void handlePromote(Player promoter, @Nullable Faction promoterFaction, String targetName) {
        if (promoterFaction == null) { promoter.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!promoterFaction.isOwner(promoter.getUniqueId())) {
            promoter.sendMessage(ChatColor.RED + "Only the faction Owner can promote members."); return;
        }
        if (promoterFaction.getCurrentPower() < plugin.COST_PROMOTE_MEMBER) {
            promoter.sendMessage(ChatColor.RED + "Your faction does not have enough power! (Need " + plugin.COST_PROMOTE_MEMBER + "P, You have " + promoterFaction.getCurrentPower() + "P)"); return;
        }

        OfflinePlayer targetOffline = null; UUID targetUUID = null;
        for(Map.Entry<UUID, FactionRank> entry : promoterFaction.getMembers().entrySet()){
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            if(op != null && op.getName() != null && op.getName().equalsIgnoreCase(targetName)){ targetOffline = op; targetUUID = entry.getKey(); break; }
        }

        if (targetUUID == null ) { promoter.sendMessage(ChatColor.RED + "Player '" + targetName + "' not found in your faction."); return; }
        if (promoterFaction.getRank(targetUUID) == FactionRank.ADMIN) { promoter.sendMessage(ChatColor.YELLOW + (targetOffline != null && targetOffline.getName() != null ? targetOffline.getName() : targetName) + " is already an Admin."); return; }
        if (promoterFaction.isOwner(targetUUID)) { promoter.sendMessage(ChatColor.RED + "You cannot promote the Owner."); return; }

        if (promoterFaction.promotePlayer(targetUUID)) {
            promoterFaction.removePower(plugin.COST_PROMOTE_MEMBER);
            String promotedNameDisplay = targetOffline.getName() != null ? targetOffline.getName() : targetName;
            promoter.sendMessage(ChatColor.GREEN + promotedNameDisplay + " has been promoted to Admin! (Cost: " + plugin.COST_PROMOTE_MEMBER + "P)");
            plugin.notifyFaction(promoterFaction, ChatColor.AQUA + promotedNameDisplay + " has been promoted to Admin by " + promoter.getName() + ".", promoter.getUniqueId());
            if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
                targetOffline.getPlayer().sendMessage(ChatColor.AQUA + "You have been promoted to Admin in faction '" + promoterFaction.getName() + "'!");
            }
            promoter.sendMessage(ChatColor.YELLOW + "Faction Power: " + promoterFaction.getCurrentPower() + "/" + promoterFaction.getMaxPower());
        } else { promoter.sendMessage(ChatColor.RED + "Failed to promote " + (targetOffline != null && targetOffline.getName() != null ? targetOffline.getName() : targetName) + "."); }
    }

    private void handleDemote(Player demoter, @Nullable Faction demoterFaction, String targetName) {
        if (demoterFaction == null) { demoter.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!demoterFaction.isOwner(demoter.getUniqueId())) {
            demoter.sendMessage(ChatColor.RED + "Only the faction Owner can demote Admins."); return;
        }
        OfflinePlayer targetOffline = null; UUID targetUUID = null;
        for(Map.Entry<UUID, FactionRank> entry : demoterFaction.getMembers().entrySet()){
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            if(op != null && op.getName() != null && op.getName().equalsIgnoreCase(targetName)){ targetOffline = op; targetUUID = entry.getKey(); break; }
        }
        if (targetUUID == null) { demoter.sendMessage(ChatColor.RED + "Player '" + targetName + "' not found in your faction or is not an Admin."); return; }
        if (demoterFaction.getRank(targetUUID) != FactionRank.ADMIN) { demoter.sendMessage(ChatColor.RED + (targetOffline != null && targetOffline.getName() != null ? targetOffline.getName() : targetName) + " is not an Admin."); return; }
        if (demoterFaction.isOwner(targetUUID)) { demoter.sendMessage(ChatColor.RED + "You cannot demote the Owner."); return;}

        if (demoterFaction.demotePlayer(targetUUID)) {
            String demotedNameDisplay = targetOffline.getName() != null ? targetOffline.getName() : targetName;
            demoter.sendMessage(ChatColor.GREEN + demotedNameDisplay + " has been demoted to Member.");
            plugin.notifyFaction(demoterFaction, ChatColor.YELLOW + demotedNameDisplay + " has been demoted to Member by " + demoter.getName() + ".", demoter.getUniqueId());
            if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
                targetOffline.getPlayer().sendMessage(ChatColor.YELLOW + "You have been demoted to Member in faction '" + demoterFaction.getName() + "'.");
            }
        } else { demoter.sendMessage(ChatColor.RED + "Failed to demote " + (targetOffline != null && targetOffline.getName() != null ? targetOffline.getName() : targetName) + "."); }
    }

    private void handleLeaderTransfer(Player currentOwner, @Nullable Faction faction, String newLeaderName) {
        if (faction == null) { currentOwner.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!faction.isOwner(currentOwner.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + "Only the current Owner can transfer leadership."); return;
        }
        OfflinePlayer newLeaderOffline = null; UUID newLeaderUUID = null;
        for(Map.Entry<UUID, FactionRank> entry : faction.getMembers().entrySet()){
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            if(op != null && op.getName() != null && op.getName().equalsIgnoreCase(newLeaderName)){ newLeaderOffline = op; newLeaderUUID = entry.getKey(); break; }
        }
        if (newLeaderUUID == null) { currentOwner.sendMessage(ChatColor.RED + "Player '" + newLeaderName + "' not found in your faction."); return; }
        if (currentOwner.getUniqueId().equals(newLeaderUUID)) { currentOwner.sendMessage(ChatColor.RED + "You are already the leader."); return; }
        if (!faction.getMembers().containsKey(newLeaderUUID) || faction.isOwner(newLeaderUUID)) {
            currentOwner.sendMessage(ChatColor.RED + (newLeaderOffline != null && newLeaderOffline.getName() != null ? newLeaderOffline.getName() : newLeaderName) + " must be a non-owner member of your faction."); return;
        }

        if (plugin.transferOwnership(faction, currentOwner, newLeaderOffline)) {
            String newLeaderNameDisplay = newLeaderOffline.getName() != null ? newLeaderOffline.getName() : newLeaderName;
            currentOwner.sendMessage(ChatColor.GREEN + "Ownership of '" + faction.getName() + "' transferred to " + newLeaderNameDisplay + ".");
            plugin.notifyFaction(faction, ChatColor.GOLD + "" + ChatColor.BOLD + currentOwner.getName() + " transferred ownership to " + newLeaderNameDisplay + "!", currentOwner.getUniqueId());
            if (newLeaderOffline.isOnline() && newLeaderOffline.getPlayer() != null) {
                newLeaderOffline.getPlayer().sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "You are now the Owner of faction '" + faction.getName() + "'!");
            }
        } else { currentOwner.sendMessage(ChatColor.RED + "Failed to transfer ownership. Ensure target is a valid, non-owner member."); }
    }

    private void handleTrust(Player truster, @Nullable Faction trusterFaction, String targetName) {
        if (trusterFaction == null) { truster.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        if (!trusterFaction.isOwner(truster.getUniqueId()) && !trusterFaction.isAdmin(truster.getUniqueId())) {
            truster.sendMessage(ChatColor.RED + "Only Owner or Admin can trust."); return;
        }
        if (trusterFaction.getCurrentPower() < plugin.COST_TRUST_PLAYER) {
            truster.sendMessage(ChatColor.RED + "Not enough power! (Need " + plugin.COST_TRUST_PLAYER + "P)"); return;
        }
        OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetName);
        if (!targetOffline.hasPlayedBefore() && !targetOffline.isOnline() && targetOffline.getName() == null) {
            truster.sendMessage(ChatColor.RED + "Player '" + targetName + "' not found or has never played."); return;
        }
        UUID targetUUID = targetOffline.getUniqueId();
        if (trusterFaction.isMemberOrHigher(targetUUID)) { truster.sendMessage(ChatColor.RED + (targetOffline.getName() != null ? targetOffline.getName() : targetName) + " is already a member."); return; }
        if (trusterFaction.isTrusted(targetUUID)) { truster.sendMessage(ChatColor.YELLOW + (targetOffline.getName() != null ? targetOffline.getName() : targetName) + " is already trusted."); return; }

        if (trusterFaction.addTrusted(targetUUID)) {
            trusterFaction.removePower(plugin.COST_TRUST_PLAYER);
            truster.sendMessage(ChatColor.GREEN + (targetOffline.getName() != null ? targetOffline.getName() : targetName) + " is now trusted! (Cost: " + plugin.COST_TRUST_PLAYER + "P)");
            plugin.notifyFaction(trusterFaction, ChatColor.LIGHT_PURPLE + (targetOffline.getName() != null ? targetOffline.getName() : targetName) + " is now trusted (by "+truster.getName()+").", truster.getUniqueId());
            if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
                targetOffline.getPlayer().sendMessage(ChatColor.AQUA + "You are now trusted by faction '" + trusterFaction.getName() + "'!");
            }
            truster.sendMessage(ChatColor.YELLOW + "Faction Power: " + trusterFaction.getCurrentPower() + "/" + trusterFaction.getMaxPower());
        } else { truster.sendMessage(ChatColor.RED + "Failed to trust " + (targetOffline.getName() != null ? targetOffline.getName() : targetName) + "."); }
    }

    private void handleUntrust(Player untruster, @Nullable Faction untrusterFaction, String targetName) {
        if (untrusterFaction == null) { untruster.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        if (!untrusterFaction.isOwner(untruster.getUniqueId()) && !untrusterFaction.isAdmin(untruster.getUniqueId())) {
            untruster.sendMessage(ChatColor.RED + "Only Owner or Admin can untrust."); return;
        }
        OfflinePlayer targetOffline = null; UUID targetUUID = null;
        for(UUID trustedUUID : untrusterFaction.getTrustedPlayers()){
            OfflinePlayer op = Bukkit.getOfflinePlayer(trustedUUID);
            if(op != null && op.getName() != null && op.getName().equalsIgnoreCase(targetName)){ targetOffline = op; targetUUID = trustedUUID; break; }
        }
        if (targetUUID == null) { untruster.sendMessage(ChatColor.RED + "Player '" + targetName + "' not found among trusted for your faction."); return; }

        if (untrusterFaction.removeTrusted(targetUUID)) {
            untruster.sendMessage(ChatColor.GREEN + targetOffline.getName() + " is no longer trusted.");
            plugin.notifyFaction(untrusterFaction, ChatColor.YELLOW + targetOffline.getName() + " is no longer trusted (by "+untruster.getName()+").", untruster.getUniqueId());
            if (targetOffline.isOnline() && targetOffline.getPlayer() != null) {
                targetOffline.getPlayer().sendMessage(ChatColor.YELLOW + "You are no longer trusted by faction '" + untrusterFaction.getName() + "'.");
            }
        } else { untruster.sendMessage(ChatColor.RED + "Failed to untrust " + targetOffline.getName() + "."); }
    }

    private void handleVault(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only Owner or Admin can access the vault."); return;
        }
        player.openInventory(playerFaction.getVault());
        player.sendMessage(ChatColor.GREEN + "Opened faction vault for '" + playerFaction.getName() + "'.");
    }

    private void handleAllyRequest(Player requesterPlayer, @Nullable Faction requesterFaction, String targetFactionName) {
        if (requesterFaction == null) { requesterPlayer.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        if (!requesterFaction.isOwner(requesterPlayer.getUniqueId()) && !requesterFaction.isAdmin(requesterPlayer.getUniqueId())) {
            requesterPlayer.sendMessage(ChatColor.RED + "Only Owner or Admin can send ally requests."); return;
        }
        Faction targetFaction = plugin.getFaction(targetFactionName);
        if (targetFaction == null) { requesterPlayer.sendMessage(ChatColor.RED + "Faction '" + targetFactionName + "' not found."); return; }
        if (requesterFaction.equals(targetFaction)) { requesterPlayer.sendMessage(ChatColor.RED + "Cannot ally own faction."); return; }
        if (requesterFaction.isAlly(targetFaction.getNameKey())) { requesterPlayer.sendMessage(ChatColor.YELLOW + "Already allied with " + targetFaction.getName() + "."); return; }
        if (requesterFaction.isEnemy(targetFaction.getNameKey())) { requesterPlayer.sendMessage(ChatColor.RED + "Cannot ally an enemy. Declare neutral first."); return; }

        Map<String, Long> requestsToRequesterFaction = plugin.getPendingAllyRequestsFor(requesterFaction.getNameKey());
        if (requestsToRequesterFaction.containsKey(targetFaction.getNameKey())) {
            if (plugin.acceptAllyRequest(requesterFaction, targetFaction)) {
                requesterPlayer.sendMessage(ChatColor.GREEN + targetFaction.getName() + " also sent you an ally request! Alliance formed!");
                plugin.notifyFaction(requesterFaction, ChatColor.AQUA + "Alliance formed with " + targetFaction.getName() + "!", null);
                plugin.notifyFaction(targetFaction, ChatColor.AQUA + "Alliance formed with " + requesterFaction.getName() + " (they accepted your pending request)!", null);
            } else { requesterPlayer.sendMessage(ChatColor.RED + "Error forming alliance despite mutual requests."); }
            return;
        }

        Map<String, Long> requestsToTargetFaction = plugin.getPendingAllyRequestsFor(targetFaction.getNameKey());
        if (requestsToTargetFaction.containsKey(requesterFaction.getNameKey())) {
            long timeRemaining = plugin.EXPIRATION_ALLY_REQUEST_MINUTES * 60000L - (System.currentTimeMillis() - requestsToTargetFaction.get(requesterFaction.getNameKey()));
            requesterPlayer.sendMessage(ChatColor.YELLOW + "Ally request already sent to " + targetFaction.getName() + ". Expires in ~" + formatTimeApprox(timeRemaining)); return;
        }
        if (requesterFaction.getCurrentPower() < plugin.COST_SEND_ALLY_REQUEST) {
            requesterPlayer.sendMessage(ChatColor.RED + "Not enough power! (Need " + plugin.COST_SEND_ALLY_REQUEST + "P)"); return;
        }
        if (plugin.sendAllyRequest(requesterFaction, targetFaction)) {
            requesterFaction.removePower(plugin.COST_SEND_ALLY_REQUEST);
            requesterPlayer.sendMessage(ChatColor.GREEN + "Ally request sent to " + targetFaction.getName() + "! (Cost: " + plugin.COST_SEND_ALLY_REQUEST + "P). Expires in " + plugin.EXPIRATION_ALLY_REQUEST_MINUTES + "m.");
            plugin.notifyFaction(targetFaction, ChatColor.AQUA + "Faction '" + requesterFaction.getName() + "' sent an ally request! Use /f allyaccept " + requesterFaction.getName(), null);
            requesterPlayer.sendMessage(ChatColor.YELLOW + "Faction Power: " + requesterFaction.getCurrentPower() + "/" + requesterFaction.getMaxPower());
        } else { requesterPlayer.sendMessage(ChatColor.RED + "Could not send ally request."); }
    }

    private void handleUnally(Player player, @Nullable Faction playerFaction, String targetFactionName) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        if (!playerFaction.isOwner(player.getUniqueId()) && !playerFaction.isAdmin(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only Owner or Admin can manage alliances."); return;
        }
        Faction targetFaction = plugin.getFaction(targetFactionName);
        if (targetFaction == null) { player.sendMessage(ChatColor.RED + "Faction '" + targetFactionName + "' not found."); return; }
        if (!playerFaction.isAlly(targetFaction.getNameKey())) { player.sendMessage(ChatColor.RED + "Not allied with " + targetFaction.getName() + "."); return; }

        plugin.revokeAlliance(playerFaction, targetFaction);
        player.sendMessage(ChatColor.GREEN + "Broken alliance with " + targetFaction.getName() + ".");
        plugin.notifyFaction(playerFaction, ChatColor.YELLOW + "Alliance with " + targetFaction.getName() + " broken by " + player.getName() + ".", player.getUniqueId());
        plugin.notifyFaction(targetFaction, ChatColor.YELLOW + "Alliance with " + playerFaction.getName() + " broken by " + player.getName() + ".", null);
    }

    private void handleAllyAccept(Player accepterPlayer, @Nullable Faction accepterFaction, String requestingFactionName) {
        if (accepterFaction == null) { accepterPlayer.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        if (!accepterFaction.isOwner(accepterPlayer.getUniqueId()) && !accepterFaction.isAdmin(accepterPlayer.getUniqueId())) {
            accepterPlayer.sendMessage(ChatColor.RED + "Only Owner or Admin can accept ally requests."); return;
        }
        Faction requestingFaction = plugin.getFaction(requestingFactionName);
        if (requestingFaction == null) { accepterPlayer.sendMessage(ChatColor.RED + "Faction '" + requestingFactionName + "' not found."); return; }

        if (plugin.acceptAllyRequest(accepterFaction, requestingFaction)) {
            accepterPlayer.sendMessage(ChatColor.GREEN + "Accepted ally request from " + requestingFaction.getName() + "! You are now allies.");
            plugin.notifyFaction(accepterFaction, ChatColor.AQUA + "Now allied with " + requestingFaction.getName() + "!", accepterPlayer.getUniqueId());
            plugin.notifyFaction(requestingFaction, ChatColor.AQUA + "Faction '" + accepterFaction.getName() + "' accepted your ally request! You are now allies.", null);
        } else { accepterPlayer.sendMessage(ChatColor.RED + "Could not accept ally request from " + requestingFaction.getName() + ". No pending request or it expired."); }
    }

    private void handleAllyRequestsList(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        Map<String, Long> requests = plugin.getPendingAllyRequestsFor(playerFaction.getNameKey());
        if (requests.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "No pending incoming ally requests."); return; }
        player.sendMessage(ChatColor.GOLD + "--- Pending Ally Requests for " + playerFaction.getName() + " ---");
        requests.forEach((requesterKey, timestamp) -> {
            Faction requesterFaction = plugin.getFaction(requesterKey);
            if (requesterFaction != null) {
                long timeLeftMillis = plugin.EXPIRATION_ALLY_REQUEST_MINUTES * 60000L - (System.currentTimeMillis() - timestamp);
                player.sendMessage(ChatColor.AQUA + "- From: " + requesterFaction.getName() + ChatColor.GRAY + " (Expires in: ~" + formatTimeApprox(timeLeftMillis) + ")");
                player.sendMessage(ChatColor.GRAY + "  To accept: /f allyaccept " + requesterFaction.getName());
            }
        });
    }

    private void handleEnemyDeclare(Player p, @Nullable Faction pf, String targetFacNameKey) {
        if (pf == null) { p.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        if (!pf.isOwner(p.getUniqueId()) && !pf.isAdmin(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Only Owner or Admin can manage relations."); return;
        }
        Faction targetFaction = plugin.getFaction(targetFacNameKey);
        if (targetFaction == null) { p.sendMessage(ChatColor.RED + "Faction '" + targetFacNameKey + "' not found."); return; }
        if (pf.equals(targetFaction)) { p.sendMessage(ChatColor.RED + "Cannot declare own faction as enemy."); return; }
        if (pf.isEnemy(targetFaction.getNameKey())) { p.sendMessage(ChatColor.YELLOW + targetFaction.getName() + " is already an enemy."); return; }
        if (pf.isAlly(targetFaction.getNameKey())) { p.sendMessage(ChatColor.RED + "Cannot declare ally an enemy. Break alliance first."); return; }
        if (pf.getCurrentPower() < plugin.COST_DECLARE_ENEMY) {
            p.sendMessage(ChatColor.RED + "Not enough power! (Need " + plugin.COST_DECLARE_ENEMY + "P)"); return;
        }
        long currentTime = System.currentTimeMillis();
        pf.removePower(plugin.COST_DECLARE_ENEMY);
        setEnemyStatus(pf, targetFaction, currentTime);
        p.sendMessage(ChatColor.GOLD + targetFaction.getName() + " is now an ENEMY! (Cost: " + plugin.COST_DECLARE_ENEMY + "P)");
        plugin.notifyFaction(pf, ChatColor.RED + targetFaction.getName() + " is now an ENEMY! (Declared by " + p.getName() + ")", p.getUniqueId());
        plugin.notifyFaction(targetFaction, ChatColor.DARK_RED + "" + ChatColor.BOLD + "Faction '" + pf.getName() + "' declared YOUR faction as ENEMY!", null);

        final List<Faction> alliesOfTarget = new ArrayList<>();
        targetFaction.getAllyFactionKeys().forEach(allyKey -> {
            Faction ally = plugin.getFaction(allyKey);
            if (ally != null && !ally.equals(pf) && !pf.isEnemy(ally.getNameKey()) && !pf.isAlly(ally.getNameKey())) {
                alliesOfTarget.add(ally);
            }
        });
        if (!alliesOfTarget.isEmpty()) {
            p.sendMessage(ChatColor.GOLD + "Also declaring war on " + targetFaction.getName() + "'s allies: " + alliesOfTarget.stream().map(Faction::getName).collect(Collectors.joining(", ")));
            for (Faction allyOfTarget : alliesOfTarget) {
                setEnemyStatus(pf, allyOfTarget, currentTime);
                plugin.notifyFaction(allyOfTarget, ChatColor.DARK_RED + "" + ChatColor.BOLD + pf.getName() + " has also declared YOUR faction an ENEMY (due to your alliance with " + targetFaction.getName() + ")!", null);
            }
        }
        p.sendMessage(ChatColor.YELLOW + "Faction Power: " + pf.getCurrentPower() + "/" + pf.getMaxPower());
    }

    private void handleNeutralDeclare(Player p, @Nullable Faction pf, String targetFacNameKey) {
        if (pf == null) { p.sendMessage(ChatColor.RED + "Not in a faction."); return; }
        if (!pf.isOwner(p.getUniqueId()) && !pf.isAdmin(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Only Owner or Admin can manage relations."); return;
        }
        Faction targetFaction = plugin.getFaction(targetFacNameKey);
        if (targetFaction == null) { p.sendMessage(ChatColor.RED + "Faction '" + targetFacNameKey + "' not found."); return; }
        if (!pf.isEnemy(targetFaction.getNameKey())) { p.sendMessage(ChatColor.YELLOW + targetFaction.getName() + " is not an enemy."); return; }

        long enemyDeclareTime = pf.getEnemyDeclareTimestamps().getOrDefault(targetFaction.getNameKey().toLowerCase(), 0L);
        long targetEnemyDeclareTime = targetFaction.getEnemyDeclareTimestamps().getOrDefault(pf.getNameKey().toLowerCase(), 0L);
        long currentTime = System.currentTimeMillis();
        long cooldownMillis = plugin.COOLDOWN_ENEMY_NEUTRAL_HOURS * 3600000L;
        long actualRemainingCooldown = Math.max(
                (enemyDeclareTime != 0) ? cooldownMillis - (currentTime - enemyDeclareTime) : 0,
                (targetEnemyDeclareTime != 0) ? cooldownMillis - (currentTime - targetEnemyDeclareTime) : 0
        );
        if (actualRemainingCooldown > 0) {
            p.sendMessage(ChatColor.RED + "Cannot declare neutral with " + targetFaction.getName() + " yet. Cooldown: " + formatTime(actualRemainingCooldown)); return;
        }
        if (pf.getCurrentPower() < plugin.COST_DECLARE_NEUTRAL) {
            p.sendMessage(ChatColor.RED + "Not enough power! (Need " + plugin.COST_DECLARE_NEUTRAL + "P)"); return;
        }
        pf.removePower(plugin.COST_DECLARE_NEUTRAL);
        setNeutralStatus(pf, targetFaction);
        p.sendMessage(ChatColor.GREEN + targetFaction.getName() + " is now NEUTRAL. (Cost: " + plugin.COST_DECLARE_NEUTRAL + "P)");
        plugin.notifyFaction(pf, ChatColor.GREEN + targetFaction.getName() + " is now NEUTRAL. (Declared by " + p.getName() + ")", p.getUniqueId());
        plugin.notifyFaction(targetFaction, ChatColor.GREEN + "Faction '" + pf.getName() + "' declared YOUR faction as NEUTRAL.", null);
        p.sendMessage(ChatColor.YELLOW + "Faction Power: " + pf.getCurrentPower() + "/" + pf.getMaxPower());
    }

    private void handleDisband(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) { player.sendMessage(ChatColor.RED + "You are not in a faction to disband."); return; }
        if (!playerFaction.isOwner(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the faction Owner can disband the faction."); return;
        }
        String disbandedFactionName = playerFaction.getName();
        plugin.disbandFactionInternal(playerFaction, false);
        player.sendMessage(ChatColor.GOLD + "You have successfully disbanded your faction '" + disbandedFactionName + "'.");
    }

    private void handleFactionChatToggle(Player player, @Nullable Faction playerFaction) {
        if (playerFaction == null) {
            player.sendMessage(ChatColor.RED + "You must be in a faction to use faction chat.");
            if (plugin.isPlayerInFactionChat(player.getUniqueId())) {
                plugin.setPlayerFactionChat(player.getUniqueId(), false);
            }
            return;
        }
        boolean nowInFactionChat = !plugin.isPlayerInFactionChat(player.getUniqueId());
        plugin.setPlayerFactionChat(player.getUniqueId(), nowInFactionChat);
        if (nowInFactionChat) {
            player.sendMessage(ChatColor.GREEN + "Faction chat toggled ON. Your messages will now only be seen by your faction (and spying OPs).");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Faction chat toggled OFF. Your messages are now public.");
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "0s";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(String.format("%02dm ", minutes));
        sb.append(String.format("%02ds", seconds));
        return sb.toString().trim();
    }

    private String formatTimeApprox(long millis) {
        if (millis <= 0) return "Expired";
        long totalSeconds = millis / 1000;
        if (totalSeconds < 60) return totalSeconds + "s";
        long minutes = totalSeconds / 60;
        if (minutes < 60) return minutes + "m " + (totalSeconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private void setEnemyStatus(Faction f1, Faction f2, long timestamp) {
        f1.addEnemy(f2.getNameKey(), timestamp);
        f2.addEnemy(f1.getNameKey(), timestamp);
        f1.removeAlly(f2.getNameKey());
        f2.removeAlly(f1.getNameKey());
        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionRelations(f1, f2);
        }
    }

    private void setNeutralStatus(Faction f1, Faction f2) {
        f1.removeEnemy(f2.getNameKey());
        f2.removeEnemy(f1.getNameKey());
        if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
            plugin.getDynmapManager().updateFactionRelations(f1, f2);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> subCommands = new ArrayList<>(Arrays.asList(
                "create", "list", "who", "info", "invite", "uninvite", "kick", "leave", "accept", "claim", "unclaim",
                "sethome", "home", "power", "promote", "demote", "leader", "trust", "untrust", "vault",
                "ally", "unally", "allyaccept", "allyrequests", "enemy", "neutral", "help", "disband", "chat", "c",
                "outpost"
        ));

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : subCommands) if (sub.startsWith(input)) completions.add(sub);
            return completions.stream().distinct().collect(Collectors.toList());
        }

        Player player = (sender instanceof Player) ? (Player) sender : null;
        Faction playerFac = (player != null) ? plugin.getFactionByPlayer(player.getUniqueId()) : null;
        String currentArgInput = args[args.length - 1].toLowerCase();
        String mainSubCmd = args[0].toLowerCase();

        if (mainSubCmd.equals("outpost")) {
            if (args.length == 2) {
                List<String> outpostActions = Arrays.asList("create", "sethome", "home", "delete", "help");
                for (String action : outpostActions) if (action.startsWith(currentArgInput)) completions.add(action);
            } else if (args.length == 3) {
                String outpostAction = args[1].toLowerCase();
                if (Arrays.asList("sethome", "home", "delete").contains(outpostAction) && playerFac != null && playerFac.getOutposts().size() > 0) {
                    for (int i = 0; i < playerFac.getOutposts().size(); i++) {
                        String outpostId = String.valueOf(i + 1);
                        if (outpostId.startsWith(currentArgInput)) completions.add(outpostId);
                    }
                }
            }
            return completions.stream().distinct().collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (mainSubCmd) {
                case "invite": case "trust":
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> player == null || !p.getUniqueId().equals(player.getUniqueId()))
                            .filter(p -> {
                                Faction targetPlayerFac = plugin.getFactionByPlayer(p.getUniqueId());
                                if (mainSubCmd.equals("invite")) return targetPlayerFac == null;
                                if (mainSubCmd.equals("trust")) {
                                    return playerFac == null || targetPlayerFac == null || !targetPlayerFac.equals(playerFac);
                                }
                                return true;
                            })
                            .filter(p -> playerFac == null || (!playerFac.isMemberOrHigher(p.getUniqueId()) && !playerFac.isTrusted(p.getUniqueId())) )
                            .filter(p -> p.getName().toLowerCase().startsWith(currentArgInput))
                            .forEach(p -> completions.add(p.getName()));
                    break;
                case "uninvite":
                    if (playerFac != null) {
                        plugin.getPendingMemberInvitesForFaction(playerFac.getNameKey()).keySet().stream()
                                .map(Bukkit::getOfflinePlayer)
                                .filter(op -> op.getName() != null && op.getName().toLowerCase().startsWith(currentArgInput))
                                .forEach(op -> completions.add(op.getName()));
                    }
                    break;
                case "kick": case "promote": case "demote": case "leader":
                    if (playerFac != null) {
                        playerFac.getMembers().keySet().stream()
                                .map(Bukkit::getOfflinePlayer)
                                .filter(op -> op != null && op.getName() != null)
                                .filter(op -> player == null || !op.getUniqueId().equals(player.getUniqueId()) || !mainSubCmd.equals("leader") )
                                .filter(op -> !playerFac.isOwner(op.getUniqueId()) || (mainSubCmd.equals("kick") && !playerFac.isOwner(op.getUniqueId()) ) )
                                .filter(op -> op.getName().toLowerCase().startsWith(currentArgInput))
                                .forEach(op -> completions.add(op.getName()));
                    }
                    break;
                case "untrust":
                    if (playerFac != null) {
                        playerFac.getTrustedPlayers().stream()
                                .map(Bukkit::getOfflinePlayer)
                                .filter(op -> op != null && op.getName() != null)
                                .filter(op -> op.getName().toLowerCase().startsWith(currentArgInput))
                                .forEach(op -> completions.add(op.getName()));
                    }
                    break;
                case "accept":
                    if (player != null) {
                        plugin.getFactionsByNameKey().values().forEach(inviterFaction -> {
                            if (plugin.getPendingMemberInvitesForFaction(inviterFaction.getNameKey()).containsKey(player.getUniqueId()) &&
                                    inviterFaction.getName().toLowerCase().startsWith(currentArgInput)) {
                                completions.add(inviterFaction.getName());
                            }
                        });
                    }
                    break;
                case "who": case "info": case "home":
                case "ally": case "unally": case "enemy": case "neutral":
                    plugin.getFactionsByNameKey().values().stream()
                            .filter(targetFac -> {
                                if (playerFac != null && playerFac.equals(targetFac)) {
                                    return !Arrays.asList("ally", "enemy", "neutral").contains(mainSubCmd);
                                }
                                if (mainSubCmd.equals("unally") && (playerFac == null || !playerFac.isAlly(targetFac.getNameKey()))) return false;
                                if (mainSubCmd.equals("neutral") && (playerFac == null || !playerFac.isEnemy(targetFac.getNameKey()))) return false;
                                if (mainSubCmd.equals("home") && player != null) {
                                    boolean isOwnOrAlly = playerFac != null && (playerFac.equals(targetFac) || playerFac.isAlly(targetFac.getNameKey()));
                                    boolean isTrustedByTarget = targetFac.isTrusted(player.getUniqueId());
                                    return isOwnOrAlly || isTrustedByTarget;
                                }
                                return true;
                            })
                            .filter(targetFac -> targetFac.getName().toLowerCase().startsWith(currentArgInput))
                            .forEach(f -> completions.add(f.getName()));
                    break;
                case "allyaccept":
                    if (playerFac != null) {
                        plugin.getPendingAllyRequestsFor(playerFac.getNameKey()).keySet().stream()
                                .map(plugin::getFaction).filter(Objects::nonNull)
                                .filter(f -> f.getName().toLowerCase().startsWith(currentArgInput))
                                .forEach(f -> completions.add(f.getName()));
                    }
                    break;
                case "list":
                    if ("<page>".startsWith(currentArgInput) || currentArgInput.isEmpty() || (currentArgInput.length() > 0 && Character.isDigit(currentArgInput.charAt(0)))) {
                        completions.add("<page>");
                    }
                    break;
                default:
                    break;
            }
        }
        return completions.stream().distinct().collect(Collectors.toList());
    }
}