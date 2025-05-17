package gg.goatedcraft.gfactions;

import gg.goatedcraft.gfactions.commands.AdminFactionCommand;
import gg.goatedcraft.gfactions.commands.FactionCommand;
import gg.goatedcraft.gfactions.data.ChunkWrapper;
import gg.goatedcraft.gfactions.data.Faction;
import gg.goatedcraft.gfactions.data.FactionRank;
import gg.goatedcraft.gfactions.data.Outpost;
import gg.goatedcraft.gfactions.data.TeleportRequest;
import gg.goatedcraft.gfactions.integration.DynmapManager;
import gg.goatedcraft.gfactions.listeners.PlayerChatListener; // New Listener
import gg.goatedcraft.gfactions.listeners.PlayerClaimBoundaryListener;
import gg.goatedcraft.gfactions.listeners.PlayerDeathListener;
import gg.goatedcraft.gfactions.listeners.PlayerTeleportListener;
import gg.goatedcraft.gfactions.listeners.ZoneProtectionListener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class GFactionsPlugin extends JavaPlugin {

    // --- Data Maps ---
    private final Map<String, Faction> factionsByNameKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerFactionMembership = new ConcurrentHashMap<>();
    private final Map<ChunkWrapper, String> claimedChunks = new ConcurrentHashMap<>(); // Faction NameKey
    private final Map<String, Map<UUID, Long>> pendingMemberInvites = new ConcurrentHashMap<>(); // FactionKey -> InvitedUUID -> Timestamp
    private final Map<String, Map<String, Long>> pendingAllyRequests = new ConcurrentHashMap<>(); // TargetFactionKey -> RequesterFactionKey -> Timestamp
    private final Map<UUID, TeleportRequest> activeTeleportRequests = new ConcurrentHashMap<>();
    private final Set<UUID> playersInFactionChat = ConcurrentHashMap.newKeySet();


    // --- Tasks & Managers ---
    private BukkitTask powerRegenTask;
    private DynmapManager dynmapManager;

    // --- Configurable Settings (Many new ones) ---
    public int POWER_INITIAL;
    public int POWER_PER_MEMBER_BONUS;
    public int POWER_REGENERATION_AMOUNT;
    public long POWER_REGENERATION_INTERVAL_TICKS;
    public int POWER_LOSS_ON_DEATH_BY_ENEMY;

    public int COST_CLAIM_CHUNK;
    public int COST_OVERCLAIM_CHUNK; // Now distinct
    public int COST_DECLARE_ENEMY;
    public int COST_DECLARE_NEUTRAL;
    public int COST_SEND_ALLY_REQUEST;
    public int COST_PROMOTE_MEMBER;
    public int COST_TRUST_PLAYER;
    public int COST_CREATE_OUTPOST;

    public long COOLDOWN_ENEMY_NEUTRAL_HOURS;
    public long EXPIRATION_MEMBER_INVITE_MINUTES;
    public long EXPIRATION_ALLY_REQUEST_MINUTES;
    public long TELEPORT_WARMUP_SECONDS;

    public int TITLE_FADE_IN_TICKS;
    public int TITLE_STAY_TICKS;
    public int TITLE_FADE_OUT_TICKS;
    public long TITLE_DISPLAY_COOLDOWN_SECONDS;

    public boolean DYNMAP_ENABLED;
    public String DYNMAP_MARKERSET_LABEL;
    public int DYNMAP_STROKE_WEIGHT;
    public double DYNMAP_STROKE_OPACITY;
    public String DYNMAP_STROKE_COLOR; // New
    public double DYNMAP_FILL_OPACITY;
    public int DYNMAP_COLOR_DEFAULT_CLAIM;
    public int DYNMAP_COLOR_ENEMY_CLAIM;
    public int DYNMAP_COLOR_ALLY_CLAIM;
    public int DYNMAP_COLOR_NEUTRAL_CLAIM; // New

    public int FACTION_NAME_MIN_LENGTH;
    public int FACTION_NAME_MAX_LENGTH;
    public Pattern FACTION_NAME_PATTERN;
    public String FACTION_CHAT_FORMAT; // New

    public int MAX_CLAIMS_PER_FACTION;
    public int MAX_MEMBERS_PER_FACTION;
    public int MAX_OUTPOSTS_PER_FACTION; // New

    public boolean PREVENT_CLAIM_NEAR_SPAWN; // New
    public int SPAWN_PROTECTION_RADIUS;   // New
    public Location SERVER_SPAWN_LOCATION; // New, determined on enable

    public String MESSAGE_ENTERING_WILDERNESS;
    public String MESSAGE_ENTERING_FACTION_TERRITORY;


    @Override
    public void onEnable() {
        getLogger().info(getName() + " is enabling...");
        saveDefaultConfig();
        loadPluginConfig(); // SERVER_SPAWN_LOCATION is determined here or shortly after worlds load
        loadFactionsData();

        // Determine server spawn location (usually default world's spawn)
        World defaultWorld = Bukkit.getWorlds().get(0); // Assuming first world is the primary/spawn world
        if (defaultWorld != null) {
            SERVER_SPAWN_LOCATION = defaultWorld.getSpawnLocation();
            getLogger().info("Server spawn location identified at: " + SERVER_SPAWN_LOCATION.toString());
        } else {
            getLogger().severe("Could not determine server spawn location! Spawn protection might not work correctly.");
            // PREVENT_CLAIM_NEAR_SPAWN should probably be forced false if this happens.
            PREVENT_CLAIM_NEAR_SPAWN = false;
        }


        FactionCommand factionCommand = new FactionCommand(this);
        Objects.requireNonNull(getCommand("faction"), "Faction command not found in plugin.yml").setExecutor(factionCommand);
        Objects.requireNonNull(getCommand("faction"), "Faction command not found in plugin.yml").setTabCompleter(factionCommand);

        AdminFactionCommand adminFactionCommand = new AdminFactionCommand(this);
        Objects.requireNonNull(getCommand("factionadmin"), "FactionAdmin command not found in plugin.yml").setExecutor(adminFactionCommand);
        Objects.requireNonNull(getCommand("factionadmin"), "FactionAdmin command not found in plugin.yml").setTabCompleter(adminFactionCommand);

        getServer().getPluginManager().registerEvents(new ZoneProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerClaimBoundaryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerTeleportListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this); // Register new chat listener

        startPowerRegeneration();

        if (DYNMAP_ENABLED) {
            this.dynmapManager = new DynmapManager(this);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Plugin dynmapPluginHook = getServer().getPluginManager().getPlugin("dynmap");
                if (dynmapPluginHook != null && dynmapPluginHook.isEnabled()) {
                    getLogger().info("Attempting to activate Dynmap integration for " + getName() + "...");
                    dynmapManager.activate();
                } else {
                    getLogger().info("Dynmap plugin not found or not enabled. Dynmap integration will remain disabled.");
                    DYNMAP_ENABLED = false;
                }
            }, 20L); // Delay slightly to ensure Dynmap is fully loaded
        } else {
            getLogger().info("Dynmap integration is disabled in GFactions configuration.");
        }

        getLogger().info(getName() + " has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info(getName() + " is disabling...");
        if (powerRegenTask != null && !powerRegenTask.isCancelled()) {
            powerRegenTask.cancel();
            powerRegenTask = null;
        }
        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.deactivate();
        }

        activeTeleportRequests.values().forEach(request -> Bukkit.getScheduler().cancelTask(request.getTaskId()));
        activeTeleportRequests.clear();
        playersInFactionChat.clear();

        saveFactionsData();
        getLogger().info(getName() + " has been disabled.");
    }

    public void loadPluginConfig() {
        FileConfiguration config = getConfig();
        config.options().copyDefaults(true); // Ensure defaults from JAR are copied if missing

        POWER_INITIAL = config.getInt("power.initial", 100);
        POWER_PER_MEMBER_BONUS = config.getInt("power.per_member_bonus", 100);
        POWER_REGENERATION_AMOUNT = config.getInt("power.regeneration_amount", 1);
        POWER_REGENERATION_INTERVAL_TICKS = config.getLong("power.regeneration_interval_hours", 1) * 60 * 60 * 20;
        POWER_LOSS_ON_DEATH_BY_ENEMY = config.getInt("power.loss_on_death_by_enemy", 50);

        COST_CLAIM_CHUNK = config.getInt("power.cost.claim_chunk", 5);
        COST_OVERCLAIM_CHUNK = config.getInt("power.cost.overclaim_chunk", 20); // Updated default
        COST_DECLARE_ENEMY = config.getInt("power.cost.declare_enemy", 10);
        COST_DECLARE_NEUTRAL = config.getInt("power.cost.declare_neutral", 10);
        COST_SEND_ALLY_REQUEST = config.getInt("power.cost.send_ally_request", 10);
        COST_PROMOTE_MEMBER = config.getInt("power.cost.promote_member", 2);
        COST_TRUST_PLAYER = config.getInt("power.cost.trust_player", 1);
        COST_CREATE_OUTPOST = config.getInt("power.cost.create_outpost", 50);


        COOLDOWN_ENEMY_NEUTRAL_HOURS = config.getLong("cooldowns.enemy_neutral_declaration_hours", 24);
        EXPIRATION_MEMBER_INVITE_MINUTES = config.getLong("cooldowns.member_invite_expiration_minutes", 5);
        EXPIRATION_ALLY_REQUEST_MINUTES = config.getLong("cooldowns.ally_request_expiration_minutes", 5);

        TELEPORT_WARMUP_SECONDS = config.getLong("teleport.warmup_seconds", 5);

        TITLE_FADE_IN_TICKS = config.getInt("titles.fade_in_ticks", 5);
        TITLE_STAY_TICKS = config.getInt("titles.stay_ticks", 30);
        TITLE_FADE_OUT_TICKS = config.getInt("titles.fade_out_ticks", 5);
        TITLE_DISPLAY_COOLDOWN_SECONDS = config.getLong("titles.display_cooldown_seconds", 1);

        DYNMAP_ENABLED = config.getBoolean("dynmap.enabled", true);
        DYNMAP_MARKERSET_LABEL = config.getString("dynmap.markerset_label", "Faction Claims");
        DYNMAP_STROKE_WEIGHT = config.getInt("dynmap.style.stroke_weight", 2);
        DYNMAP_STROKE_OPACITY = config.getDouble("dynmap.style.stroke_opacity", 0.80);
        DYNMAP_STROKE_COLOR = config.getString("dynmap.style.stroke_color", "0x000000");
        DYNMAP_FILL_OPACITY = config.getDouble("dynmap.style.fill_opacity", 0.35);
        DYNMAP_COLOR_NEUTRAL_CLAIM = getColorFromHexString(config.getString("dynmap.style.neutral_claim_color", "0xFFFF00"), 0xFFFF00);


        try {
            DYNMAP_COLOR_DEFAULT_CLAIM = Integer.decode(config.getString("dynmap.style.default_claim_color", "0x00FF00"));
            DYNMAP_COLOR_ENEMY_CLAIM = Integer.decode(config.getString("dynmap.style.enemy_claim_color", "0xFF0000"));
            DYNMAP_COLOR_ALLY_CLAIM = Integer.decode(config.getString("dynmap.style.ally_claim_color", "0x00FFFF"));
            DYNMAP_COLOR_NEUTRAL_CLAIM = Integer.decode(config.getString("dynmap.style.neutral_claim_color", "0xFFFF00"));

        } catch (NumberFormatException e) {
            getLogger().warning("Invalid hex color format in Dynmap style settings. Using internal defaults.");
            DYNMAP_COLOR_DEFAULT_CLAIM = 0x00FF00; // Green
            DYNMAP_COLOR_ENEMY_CLAIM = 0xFF0000;   // Red
            DYNMAP_COLOR_ALLY_CLAIM = 0x00FFFF;    // Cyan
            DYNMAP_COLOR_NEUTRAL_CLAIM = 0xFFFF00; // Yellow
        }

        FACTION_NAME_MIN_LENGTH = config.getInt("faction_details.name_min_length", 3);
        FACTION_NAME_MAX_LENGTH = config.getInt("faction_details.name_max_length", 16);
        String regex = config.getString("faction_details.name_regex", "^[a-zA-Z0-9_]+$");
        try {
            if (regex == null || regex.isEmpty()) {
                regex = "^[a-zA-Z0-9_]{" + FACTION_NAME_MIN_LENGTH + "," + FACTION_NAME_MAX_LENGTH + "}$";
            }
            FACTION_NAME_PATTERN = Pattern.compile(regex);
        } catch (Exception e) {
            getLogger().warning("Invalid faction_name_regex: " + e.getMessage() + ". Using default alphanumeric pattern.");
            FACTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{" + FACTION_NAME_MIN_LENGTH + "," + FACTION_NAME_MAX_LENGTH + "}$");
        }
        MAX_OUTPOSTS_PER_FACTION = config.getInt("faction_details.max_outposts", 1);
        FACTION_CHAT_FORMAT = ChatColor.translateAlternateColorCodes('&', config.getString("faction_details.faction_chat_format", "&8[&a{FACTION_NAME}&8] &f{PLAYER_NAME}: &e{MESSAGE}"));


        MAX_CLAIMS_PER_FACTION = config.getInt("claiming.max_claims_per_faction", 0);
        MAX_MEMBERS_PER_FACTION = config.getInt("claiming.max_members_per_faction", 0);
        PREVENT_CLAIM_NEAR_SPAWN = config.getBoolean("claiming.prevent_claim_near_spawn", true);
        SPAWN_PROTECTION_RADIUS = config.getInt("claiming.spawn_protection_radius", 50);

        MESSAGE_ENTERING_WILDERNESS = ChatColor.translateAlternateColorCodes('&',config.getString("messages.entering_wilderness", "&7Now entering Wilderness"));
        MESSAGE_ENTERING_FACTION_TERRITORY = ChatColor.translateAlternateColorCodes('&',config.getString("messages.entering_faction_territory", "&7Now entering {FACTION_RELATION_COLOR}{FACTION_NAME}"));


        getLogger().info(getName() + " configuration has been loaded.");
    }

    private int getColorFromHexString(String hex, int defaultColor) {
        if (hex == null) return defaultColor;
        try {
            return Integer.decode(hex);
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid hex color string '" + hex + "'. Defaulting.");
            return defaultColor;
        }
    }


    private void startPowerRegeneration() {
        if (POWER_REGENERATION_AMOUNT <= 0 || POWER_REGENERATION_INTERVAL_TICKS <= 0) {
            getLogger().info("Faction power regeneration is disabled (amount or interval is zero/negative in config).");
            return;
        }
        powerRegenTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (factionsByNameKey.isEmpty()) return;
            for (Faction faction : factionsByNameKey.values()) {
                if (faction.getCurrentPower() < faction.getMaxPower()) {
                    faction.addPower(POWER_REGENERATION_AMOUNT);
                }
            }
        }, POWER_REGENERATION_INTERVAL_TICKS, POWER_REGENERATION_INTERVAL_TICKS);
        double hours = POWER_REGENERATION_INTERVAL_TICKS / (20.0 * 3600.0);
        getLogger().info("Faction power regeneration task started: +" + POWER_REGENERATION_AMOUNT + " power every " + String.format("%.2f", hours) + " hours for all factions needing it.");
    }

    public boolean createFaction(Player owner, String name) {
        String nameKey = name.toLowerCase();
        if (factionsByNameKey.containsKey(nameKey)) return false;
        if (playerFactionMembership.containsKey(owner.getUniqueId())) return false;

        // Auto-claim current chunk and set home
        Chunk initialChunk = owner.getLocation().getChunk();
        ChunkWrapper initialChunkWrapper = new ChunkWrapper(initialChunk.getWorld().getName(), initialChunk.getX(), initialChunk.getZ());
        Location initialHome = owner.getLocation();

        Faction faction = new Faction(name, owner.getUniqueId(), initialHome, this); // Pass initial home
        faction.addClaim(initialChunkWrapper); // Add the first claim (spawnblock chunk)
        claimedChunks.put(initialChunkWrapper, nameKey);
        // Power for initial claim should be handled by createFaction logic or pre-checked

        factionsByNameKey.put(nameKey, faction);
        playerFactionMembership.put(owner.getUniqueId(), nameKey);
        getLogger().info("Faction '" + name + "' created by player " + owner.getName() + " at chunk " + initialChunkWrapper.toString());
        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.addFactionToMap(faction); // Initial map update for the new faction
            dynmapManager.updateClaimOnMap(faction, initialChunkWrapper); // Specifically update the first claim
        }
        return true;
    }


    public Faction getFaction(String nameOrKey) {
        if (nameOrKey == null || nameOrKey.trim().isEmpty()) return null;
        return factionsByNameKey.get(nameOrKey.toLowerCase());
    }

    public Faction getFactionByPlayer(UUID playerUUID) {
        if (playerUUID == null) return null;
        String factionNameKey = playerFactionMembership.get(playerUUID);
        return (factionNameKey != null) ? getFaction(factionNameKey) : null;
    }

    public boolean transferOwnership(Faction faction, Player currentOwner, OfflinePlayer newOwnerOfflinePlayer) {
        if (faction == null || newOwnerOfflinePlayer == null || currentOwner == null) return false;
        if (!faction.isOwner(currentOwner.getUniqueId())) return false;

        UUID newOwnerUUID = newOwnerOfflinePlayer.getUniqueId();
        if (!faction.getMembers().containsKey(newOwnerUUID) || faction.isOwner(newOwnerUUID)) {
            return false;
        }
        faction.setOwnerUUID(newOwnerUUID);
        getLogger().info("Faction '" + faction.getName() + "' ownership transferred from " + currentOwner.getName() + " to " + newOwnerOfflinePlayer.getName() + ".");
        return true;
    }

    public boolean sendMemberInvite(Faction invitingFaction, UUID invitedPlayerUUID) {
        if (playerFactionMembership.containsKey(invitedPlayerUUID)) return false;
        Map<UUID, Long> invitesFromThisFaction = pendingMemberInvites.get(invitingFaction.getNameKey());
        if (invitesFromThisFaction != null && invitesFromThisFaction.containsKey(invitedPlayerUUID)) {
            if (System.currentTimeMillis() - invitesFromThisFaction.get(invitedPlayerUUID) < EXPIRATION_MEMBER_INVITE_MINUTES * 60000L) {
                return false; // Already an active invite
            }
        }
        pendingMemberInvites.computeIfAbsent(invitingFaction.getNameKey(), k -> new ConcurrentHashMap<>())
                .put(invitedPlayerUUID, System.currentTimeMillis());
        return true;
    }

    public void revokeMemberInvite(Faction invitingFaction, OfflinePlayer invitedOfflinePlayer) {
        if (invitingFaction == null || invitedOfflinePlayer == null) return;
        Map<UUID, Long> invites = pendingMemberInvites.get(invitingFaction.getNameKey());
        if (invites != null) {
            if (invites.remove(invitedOfflinePlayer.getUniqueId()) != null && invites.isEmpty()) {
                pendingMemberInvites.remove(invitingFaction.getNameKey());
            }
        }
    }

    public boolean acceptMemberInvite(UUID playerUUID, String factionNameKeyToJoin) {
        Faction factionToJoin = getFaction(factionNameKeyToJoin);
        if (factionToJoin == null) return false;

        Map<UUID, Long> invitesFromFaction = pendingMemberInvites.get(factionToJoin.getNameKey());
        if (invitesFromFaction == null || !invitesFromFaction.containsKey(playerUUID)) return false;
        if (System.currentTimeMillis() - invitesFromFaction.get(playerUUID) > EXPIRATION_MEMBER_INVITE_MINUTES * 60000L) {
            invitesFromFaction.remove(playerUUID);
            if (invitesFromFaction.isEmpty()) pendingMemberInvites.remove(factionToJoin.getNameKey());
            return false; // Expired
        }

        if (MAX_MEMBERS_PER_FACTION > 0 && factionToJoin.getMembers().size() >= (MAX_MEMBERS_PER_FACTION +1 )) { // +1 for owner
            return false; // Faction full
        }

        if (factionToJoin.addMember(playerUUID, FactionRank.MEMBER)) {
            playerFactionMembership.put(playerUUID, factionToJoin.getNameKey());
            invitesFromFaction.remove(playerUUID);
            if (invitesFromFaction.isEmpty()) pendingMemberInvites.remove(factionToJoin.getNameKey());
            return true;
        }
        return false;
    }
    public Map<UUID, Long> getPendingMemberInvitesForFaction(String inviterFactionKey) {
        Map<UUID, Long> invites = pendingMemberInvites.getOrDefault(inviterFactionKey.toLowerCase(), Collections.emptyMap());
        // Filter out expired invites if necessary, or rely on accept logic to do so
        return invites.entrySet().stream()
                .filter(entry -> System.currentTimeMillis() - entry.getValue() < EXPIRATION_MEMBER_INVITE_MINUTES * 60000L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    public boolean removePlayerFromFaction(Faction faction, UUID playerUUID, boolean isKick) {
        if (faction.isOwner(playerUUID)) return false; // Owner cannot be removed this way
        if (faction.removeMember(playerUUID)) {
            playerFactionMembership.remove(playerUUID);
            return true;
        }
        return false;
    }

    public boolean claimChunk(Faction faction, Chunk chunk, boolean isOverclaim, boolean isOutpostCreation) {
        ChunkWrapper chunkWrapper = new ChunkWrapper(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        int claimCost = isOverclaim ? COST_OVERCLAIM_CHUNK : COST_CLAIM_CHUNK;
        if(isOutpostCreation) claimCost = COST_CREATE_OUTPOST;


        if (faction.getCurrentPower() < claimCost) return false; // Not enough power

        // Adjacency check for non-outpost creation claims
        if (!isOutpostCreation) {
            // If it's the faction's very first claim (spawnblock during creation), adjacency doesn't apply yet.
            // Faction.createFaction already adds the first claim.
            // This check is for subsequent claims.
            if (!faction.getClaimedChunks().isEmpty() && !faction.isChunkAdjacentToExistingClaim(chunkWrapper)) {
                Player owner = Bukkit.getPlayer(faction.getOwnerUUID());
                if(owner != null && owner.isOnline()){ // Send message if possible
                    owner.sendMessage(ChatColor.RED + "You can only claim chunks adjacent to your existing claims or create an outpost.");
                }
                return false; // Must be adjacent if not an outpost
            }
            // Further check: ensure it's connected to a spawn block (main or outpost)
            if (!faction.getClaimedChunks().isEmpty() && !faction.isOutpostChunk(chunkWrapper) && !faction.isConnectedToSpawnBlock(chunkWrapper, this)) {
                Player owner = Bukkit.getPlayer(faction.getOwnerUUID());
                if(owner != null && owner.isOnline()){
                    owner.sendMessage(ChatColor.RED + "This claim would not be connected to your faction's spawn block or an outpost.");
                }
                return false;
            }
        }


        if (MAX_CLAIMS_PER_FACTION > 0 && faction.getClaimedChunks().size() >= MAX_CLAIMS_PER_FACTION) {
            String existingOwnerKey = claimedChunks.get(chunkWrapper);
            if (existingOwnerKey == null || !isOverclaim) {
                if(!isOverclaim || (existingOwnerKey != null && existingOwnerKey.equalsIgnoreCase(faction.getNameKey()))){
                    return false;
                }
            }
        }

        String existingOwnerNameKey = claimedChunks.get(chunkWrapper);
        if (existingOwnerNameKey != null) { // Chunk is already claimed
            if (existingOwnerNameKey.equalsIgnoreCase(faction.getNameKey())) return true; // Already owns it

            if (!isOverclaim && !isOutpostCreation) return false; // Cannot claim if owned and not overclaiming (unless it's special outpost)

            Faction previousOwnerFaction = getFaction(existingOwnerNameKey);
            if (previousOwnerFaction != null) {
                previousOwnerFaction.removeClaim(chunkWrapper); // This will trigger home relocation if it was their spawnblock
                // The removeClaim in Faction class now handles spawnblock relocation logic.
                claimedChunks.remove(chunkWrapper); // Also remove from central map here
                if (dynmapManager != null && dynmapManager.isEnabled()) {
                    dynmapManager.removeClaimFromMap(chunkWrapper); // Update Dynmap for the chunk becoming unclaimed by previous
                }
            } else {
                claimedChunks.remove(chunkWrapper); // Claimed by a ghost faction, just remove from map
            }
        }

        faction.addClaim(chunkWrapper);
        claimedChunks.put(chunkWrapper, faction.getNameKey());
        faction.removePower(claimCost);

        if (isOutpostCreation) {
            // This chunk becomes an outpost. The FactionCommand will handle creating the Outpost object.
        }

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateClaimOnMap(faction, chunkWrapper); // Update map for new owner
        }
        return true;
    }


    public void unclaimChunkPlayer(Faction faction, ChunkWrapper chunkWrapper) {
        if (faction == null || chunkWrapper == null) return;
        String ownerKey = claimedChunks.get(chunkWrapper);
        if (ownerKey == null || !ownerKey.equals(faction.getNameKey())) return;

        if (chunkWrapper.equals(faction.getSpawnBlockChunk())) {
            // Prevent unclaiming the main spawnblock chunk this way. Must use /f sethome elsewhere or disband.
            // Or, if allowed, trigger home relocation. For now, prevent direct unclaim of main spawn.
            Player player = Bukkit.getPlayer(faction.getOwnerUUID()); // Notify owner or relevant player
            if (player != null) player.sendMessage(ChatColor.RED + "You cannot unclaim your main faction spawnblock chunk directly. Use /f sethome elsewhere first or disband.");
            return;
        }

        // If it's an outpost chunk, remove the outpost object as well
        if (faction.isOutpostChunk(chunkWrapper)) {
            faction.removeOutpost(chunkWrapper); // This removes from Faction's outpost list
            // The chunk will be removed from claimedChunks and faction's claimed set below
        }


        faction.removeClaim(chunkWrapper); // Removes from faction's internal set
        claimedChunks.remove(chunkWrapper); // Removes from global map

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.removeClaimFromMap(chunkWrapper);
        }
    }


    public boolean sendAllyRequest(Faction requesterFaction, Faction targetFaction) {
        if (requesterFaction == null || targetFaction == null || requesterFaction.equals(targetFaction) ||
                requesterFaction.isAlly(targetFaction.getNameKey()) || requesterFaction.isEnemy(targetFaction.getNameKey()))
            return false;

        Map<String, Long> requestsToTarget = pendingAllyRequests.get(targetFaction.getNameKey());
        if (requestsToTarget != null && requestsToTarget.containsKey(requesterFaction.getNameKey())) {
            if (System.currentTimeMillis() - requestsToTarget.get(requesterFaction.getNameKey()) < EXPIRATION_ALLY_REQUEST_MINUTES * 60000L) {
                return false; // Already an active request
            }
        }
        pendingAllyRequests.computeIfAbsent(targetFaction.getNameKey(), k -> new ConcurrentHashMap<>())
                .put(requesterFaction.getNameKey(), System.currentTimeMillis());
        return true;
    }

    public boolean acceptAllyRequest(Faction acceptingFaction, Faction requestingFaction) {
        if (acceptingFaction == null || requestingFaction == null) return false;
        Map<String, Long> requestsForAcceptingFaction = pendingAllyRequests.get(acceptingFaction.getNameKey());
        if (requestsForAcceptingFaction == null || !requestsForAcceptingFaction.containsKey(requestingFaction.getNameKey()))
            return false;

        if (System.currentTimeMillis() - requestsForAcceptingFaction.get(requestingFaction.getNameKey()) > EXPIRATION_ALLY_REQUEST_MINUTES * 60000L) {
            requestsForAcceptingFaction.remove(requestingFaction.getNameKey());
            if (requestsForAcceptingFaction.isEmpty()) pendingAllyRequests.remove(acceptingFaction.getNameKey());
            return false; // Expired
        }

        acceptingFaction.addAlly(requestingFaction.getNameKey());
        requestingFaction.addAlly(acceptingFaction.getNameKey());
        requestsForAcceptingFaction.remove(requestingFaction.getNameKey());
        if (requestsForAcceptingFaction.isEmpty()) pendingAllyRequests.remove(acceptingFaction.getNameKey());

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionAppearance(acceptingFaction);
            dynmapManager.updateFactionAppearance(requestingFaction);
        }
        return true;
    }
    public Map<String, Long> getPendingAllyRequestsFor(String targetFactionNameKey) {
        Map<String, Long> requests = pendingAllyRequests.getOrDefault(targetFactionNameKey.toLowerCase(), Collections.emptyMap());
        return requests.entrySet().stream()
                .filter(entry -> System.currentTimeMillis() - entry.getValue() < EXPIRATION_ALLY_REQUEST_MINUTES * 60000L)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    public void revokeAlliance(Faction faction1, Faction faction2) {
        if (faction1 == null || faction2 == null) return;
        faction1.removeAlly(faction2.getNameKey());
        faction2.removeAlly(faction1.getNameKey());
        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionAppearance(faction1);
            dynmapManager.updateFactionAppearance(faction2);
        }
    }


    @SuppressWarnings("unchecked")
    private void loadFactionsData() {
        FileConfiguration config = getConfig();
        factionsByNameKey.clear();
        playerFactionMembership.clear();
        claimedChunks.clear();
        pendingMemberInvites.clear();
        pendingAllyRequests.clear();

        if (!config.isConfigurationSection("factions")) {
            getLogger().info("No 'factions' section found in config.yml. No faction data loaded.");
            return;
        }
        getLogger().info("Loading " + getName() + " faction data from config.yml...");
        ConfigurationSection factionsSection = config.getConfigurationSection("factions");
        if (factionsSection == null) return;

        for (String factionNameKey : factionsSection.getKeys(false)) {
            String path = "factions." + factionNameKey;
            String originalName = config.getString(path + ".originalName");
            String ownerUUIDString = config.getString(path + ".owner");

            if (originalName == null || ownerUUIDString == null) {
                getLogger().warning("Skipping loading faction '" + factionNameKey + "': Missing originalName or owner UUID in config.");
                continue;
            }
            UUID ownerUUID;
            try {
                ownerUUID = UUID.fromString(ownerUUIDString);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid owner UUID for faction '" + originalName + "'. Skipping load.");
                continue;
            }

            // Load home location for Faction constructor
            Location homeLoc = null;
            if (config.isConfigurationSection(path + ".home")) {
                String worldName = config.getString(path + ".home.world");
                World world = (worldName != null) ? Bukkit.getWorld(worldName) : null;
                if (world != null) {
                    homeLoc = new Location(world,
                            config.getDouble(path + ".home.x"), config.getDouble(path + ".home.y"), config.getDouble(path + ".home.z"),
                            (float) config.getDouble(path + ".home.yaw"), (float) config.getDouble(path + ".home.pitch"));
                } else if (worldName != null) {
                    getLogger().warning("World '" + worldName + "' for home of faction '" + originalName + "' not found. Home will be null initially.");
                }
            } else {
                getLogger().warning("Faction '" + originalName + "' has no home location in config. This is problematic for the spawnblock system.");
                // Could try to assign a default from first claim, or mark as needing admin intervention.
                // For now, constructor handles null, but it's not ideal.
            }


            Faction faction = new Faction(originalName, ownerUUID, homeLoc, this); // Pass homeLoc
            faction.lateInitPluginReference(this); // Still important

            ConfigurationSection membersSection = config.getConfigurationSection(path + ".members");
            if (membersSection != null) {
                for (String uuidStr : membersSection.getKeys(false)) {
                    try {
                        UUID memberUUID = UUID.fromString(uuidStr);
                        FactionRank rank = FactionRank.fromString(membersSection.getString(uuidStr));
                        if (!memberUUID.equals(ownerUUID)) { // Owner is added by constructor
                            faction.getMembers().put(memberUUID, rank); // Use direct put for loading
                        }
                        playerFactionMembership.put(memberUUID, faction.getNameKey());
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("Invalid member UUID or Rank for faction '" + originalName + "', member entry: '" + uuidStr + "'.");
                    }
                }
            }
            faction.updatePowerOnMemberChangeAndLoad(); // Recalculate max power
            int loadedPower = config.getInt(path + ".power", faction.getMaxPower());
            faction.setCurrentPower(Math.min(loadedPower, faction.getMaxPower()));


            config.getStringList(path + ".claims").forEach(claimStr -> {
                ChunkWrapper cw = ChunkWrapper.fromString(claimStr);
                if (cw != null) {
                    faction.addClaim(cw);
                    claimedChunks.put(cw, faction.getNameKey());
                } else {
                    getLogger().warning("Invalid claim string '" + claimStr + "' for faction " + originalName + ".");
                }
            });

            // Load Outposts
            List<String> outpostStrings = config.getStringList(path + ".outposts");
            for(String outpostStr : outpostStrings) {
                Outpost outpost = Outpost.deserialize(outpostStr);
                if(outpost != null) {
                    faction.addOutpost(outpost); // This also adds the outpost chunk to claimedChunks
                    // Ensure the main claimedChunks map is also updated for outpost chunks if not already by addClaim
                    if (!claimedChunks.containsKey(outpost.getChunkWrapper())) {
                        claimedChunks.put(outpost.getChunkWrapper(), faction.getNameKey());
                    }
                } else {
                    getLogger().warning("Could not deserialize outpost string: " + outpostStr + " for faction " + originalName);
                }
            }


            config.getStringList(path + ".enemies").forEach(enemyKey -> faction.getEnemyFactionKeys().add(enemyKey.toLowerCase()));
            config.getStringList(path + ".allies").forEach(allyKey -> faction.getAllyFactionKeys().add(allyKey.toLowerCase()));
            ConfigurationSection enemyTimeSection = config.getConfigurationSection(path + ".enemyTimestamps");
            if (enemyTimeSection != null) {
                enemyTimeSection.getKeys(false).forEach(enemyKey ->
                        faction.getEnemyDeclareTimestamps().put(enemyKey.toLowerCase(), enemyTimeSection.getLong(enemyKey)));
            }
            config.getStringList(path + ".trustedPlayers").forEach(uuidStr -> {
                try { faction.addTrusted(UUID.fromString(uuidStr)); } catch (IllegalArgumentException e) { /* Log */ }
            });

            List<?> rawVaultList = config.getList(path + ".vaultContents");
            if (rawVaultList != null) {
                ItemStack[] vaultItems = new ItemStack[Faction.VAULT_SIZE];
                for (int i = 0; i < rawVaultList.size() && i < Faction.VAULT_SIZE; i++) {
                    if (rawVaultList.get(i) instanceof Map) {
                        try {
                            vaultItems[i] = ItemStack.deserialize((Map<String, Object>) rawVaultList.get(i));
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Error deserializing vault item for " + originalName + " at slot " + i + ".", e);
                            vaultItems[i] = null;
                        }
                    } else if (rawVaultList.get(i) != null) {
                        getLogger().warning("Vault item for " + originalName + " at slot " + i + " is not a Map.");
                    }
                }
                faction.setVaultContents(vaultItems);
            }

            factionsByNameKey.put(faction.getNameKey(), faction);
            getLogger().info("Loaded faction: " + faction.getName() + " (Owner: " + (Bukkit.getOfflinePlayer(ownerUUID).getName() !=null ? Bukkit.getOfflinePlayer(ownerUUID).getName() : ownerUUIDString.substring(0,8) ) + ", P: " + faction.getCurrentPower() + "/" + faction.getMaxPower() + ", Claims: " + faction.getClaimedChunks().size() + ", Outposts: " + faction.getOutposts().size() + ")");
        }
        getLogger().info(getName() + " faction data loaded. Total factions: " + factionsByNameKey.size());

        // Load pending ally requests
        pendingAllyRequests.clear();
        ConfigurationSection allyRequestsSection = config.getConfigurationSection("pendingAllyRequests");
        if (allyRequestsSection != null) {
            allyRequestsSection.getKeys(false).forEach(targetFactionKey -> {
                ConfigurationSection requesterMapSection = allyRequestsSection.getConfigurationSection(targetFactionKey);
                if (requesterMapSection != null) {
                    Map<String, Long> requesterMap = new ConcurrentHashMap<>();
                    requesterMapSection.getKeys(false).forEach(requesterKey -> {
                        long timestamp = requesterMapSection.getLong(requesterKey);
                        if (System.currentTimeMillis() - timestamp < EXPIRATION_ALLY_REQUEST_MINUTES * 60000L) {
                            requesterMap.put(requesterKey.toLowerCase(), timestamp);
                        }
                    });
                    if (!requesterMap.isEmpty()) pendingAllyRequests.put(targetFactionKey.toLowerCase(), requesterMap);
                }
            });
        }

        // Load pending member invites
        pendingMemberInvites.clear();
        ConfigurationSection memberInvitesSection = config.getConfigurationSection("pendingMemberInvites");
        if (memberInvitesSection != null) {
            memberInvitesSection.getKeys(false).forEach(inviterFactionKey -> {
                ConfigurationSection inviteeMapSection = memberInvitesSection.getConfigurationSection(inviterFactionKey);
                if (inviteeMapSection != null) {
                    Map<UUID, Long> inviteeMap = new ConcurrentHashMap<>();
                    inviteeMapSection.getKeys(false).forEach(uuidString -> {
                        try {
                            UUID uuid = UUID.fromString(uuidString);
                            long timestamp = inviteeMapSection.getLong(uuidString);
                            if (System.currentTimeMillis() - timestamp < EXPIRATION_MEMBER_INVITE_MINUTES * 60000L) {
                                inviteeMap.put(uuid, timestamp);
                            }
                        } catch (IllegalArgumentException e) { getLogger().warning("Invalid UUID in pendingMemberInvites: " + uuidString); }
                    });
                    if (!inviteeMap.isEmpty()) pendingMemberInvites.put(inviterFactionKey.toLowerCase(), inviteeMap);
                }
            });
        }
    }


    private void saveFactionsData() {
        FileConfiguration config = getConfig();
        config.set("factions", null); // Clear old data first
        config.set("pendingAllyRequests", null);
        config.set("pendingMemberInvites", null);
        getLogger().info("Saving " + getName() + " data to config.yml...");

        if (!factionsByNameKey.isEmpty()) {
            ConfigurationSection factionsSection = config.createSection("factions");
            factionsByNameKey.forEach((nameKey, faction) -> {
                String path = nameKey;
                factionsSection.set(path + ".originalName", faction.getName());
                factionsSection.set(path + ".owner", faction.getOwnerUUID().toString());
                factionsSection.set(path + ".power", faction.getCurrentPower());

                ConfigurationSection membersSection = factionsSection.createSection(path + ".members");
                faction.getMembers().forEach((uuid, rank) -> membersSection.set(uuid.toString(), rank.name()));

                factionsSection.set(path + ".claims", faction.getClaimedChunks().stream().map(ChunkWrapper::toString).collect(Collectors.toList()));

                if (faction.getHomeLocation() != null && faction.getHomeLocation().getWorld() != null) {
                    Location home = faction.getHomeLocation();
                    factionsSection.set(path + ".home.world", home.getWorld().getName());
                    factionsSection.set(path + ".home.x", home.getX());
                    factionsSection.set(path + ".home.y", home.getY());
                    factionsSection.set(path + ".home.z", home.getZ());
                    factionsSection.set(path + ".home.yaw", home.getYaw());
                    factionsSection.set(path + ".home.pitch", home.getPitch());
                } else {
                    factionsSection.set(path + ".home", null);
                }

                // Save Outposts
                List<String> serializedOutposts = faction.getOutposts().stream()
                        .map(Outpost::serialize)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!serializedOutposts.isEmpty()) {
                    factionsSection.set(path + ".outposts", serializedOutposts);
                } else {
                    factionsSection.set(path + ".outposts", null); // Explicitly null if empty
                }


                factionsSection.set(path + ".enemies", new ArrayList<>(faction.getEnemyFactionKeys()));
                factionsSection.set(path + ".allies", new ArrayList<>(faction.getAllyFactionKeys()));
                ConfigurationSection enemyTimeSection = factionsSection.createSection(path + ".enemyTimestamps");
                faction.getEnemyDeclareTimestamps().forEach(enemyTimeSection::set);
                factionsSection.set(path + ".trustedPlayers", faction.getTrustedPlayers().stream().map(UUID::toString).collect(Collectors.toList()));

                List<Map<String, Object>> serializedVault = new ArrayList<>();
                for (ItemStack item : faction.getVaultContentsForSave()) {
                    serializedVault.add(item != null ? item.serialize() : null);
                }
                factionsSection.set(path + ".vaultContents", serializedVault);
            });
        }

        ConfigurationSection allyRequestsSection = config.createSection("pendingAllyRequests");
        pendingAllyRequests.forEach((targetFactionKey, requesterMap) -> {
            ConfigurationSection targetSection = allyRequestsSection.createSection(targetFactionKey);
            requesterMap.forEach((reqKey, time) -> {
                if (System.currentTimeMillis() - time < EXPIRATION_ALLY_REQUEST_MINUTES * 60000L) { // Only save non-expired
                    targetSection.set(reqKey, time);
                }
            });
        });
        ConfigurationSection memberInvitesSection = config.createSection("pendingMemberInvites");
        pendingMemberInvites.forEach((inviterFactionKey, inviteeMap) -> {
            ConfigurationSection inviterSection = memberInvitesSection.createSection(inviterFactionKey);
            inviteeMap.forEach((uuid, timestamp) -> {
                if (System.currentTimeMillis() - timestamp < EXPIRATION_MEMBER_INVITE_MINUTES * 60000L) { // Only save non-expired
                    inviterSection.set(uuid.toString(), timestamp);
                }
            });
        });
        saveConfig();
        getLogger().info(getName() + " data has been saved.");
    }


    public void disbandFactionInternal(Faction factionToDisband, boolean isAdminAction) {
        if (factionToDisband == null) return;
        String factionName = factionToDisband.getName();
        String factionNameKey = factionToDisband.getNameKey();
        getLogger().log(Level.INFO, (isAdminAction ? "[ADMIN ACTION] " : "") + "Disbanding faction: " + factionName + " (" + factionNameKey + ")");

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.removeFactionClaimsFromMap(factionToDisband);
        }

        // Unclaim all chunks from global map first
        new ArrayList<>(factionToDisband.getClaimedChunks()).forEach(cw -> {
            claimedChunks.remove(cw);
        });
        // Faction's internal claim list will be cleared when object is GC'd or if explicitly cleared

        factionToDisband.getMembers().keySet().forEach(memberUUID -> {
            playerFactionMembership.remove(memberUUID);
            playersInFactionChat.remove(memberUUID); // Remove from faction chat
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberUUID);
            if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                offlinePlayer.getPlayer().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Your faction '" + factionName + "' has been " + (isAdminAction ? "forcefully deleted by an administrator." : "disbanded by its owner."));
            }
        });
        factionsByNameKey.remove(factionNameKey);
        pendingMemberInvites.remove(factionNameKey);
        pendingAllyRequests.remove(factionNameKey);
        pendingAllyRequests.forEach((targetKey, requesterMap) -> requesterMap.remove(factionNameKey));

        // Remove this faction from other factions' ally/enemy lists
        for (Faction otherFaction : factionsByNameKey.values()) {
            boolean changed = otherFaction.removeEnemy(factionNameKey);
            changed |= otherFaction.removeAlly(factionNameKey);
            otherFaction.getEnemyDeclareTimestamps().remove(factionNameKey);
            if (changed && dynmapManager != null && dynmapManager.isEnabled()) {
                dynmapManager.updateFactionAppearance(otherFaction);
            }
        }
        getLogger().log(Level.INFO, "Faction " + factionName + " has been fully disbanded and data cleaned up.");
    }

    public void unclaimChunkAdmin(ChunkWrapper chunkWrapper) {
        if (chunkWrapper == null) return;
        String owningFactionKey = claimedChunks.get(chunkWrapper);
        if (owningFactionKey == null) return;

        Faction owningFaction = getFaction(owningFactionKey);
        if (owningFaction != null) {
            owningFaction.removeClaim(chunkWrapper); // This handles spawnblock relocation logic within Faction class
            getLogger().info("[ADMIN] Chunk " + chunkWrapper.toString() + " has been unclaimed from faction " + owningFaction.getName());
        } else {
            getLogger().warning("[ADMIN] Chunk " + chunkWrapper.toString() + " was claimed by a non-existent faction '" + owningFactionKey + "'. Removing from map.");
        }
        claimedChunks.remove(chunkWrapper); // Remove from global map

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.removeClaimFromMap(chunkWrapper); // Update Dynmap
        }
    }


    // --- Data Accessors ---
    public Map<String, Faction> getFactionsByNameKey() { return Collections.unmodifiableMap(factionsByNameKey); }
    public Map<ChunkWrapper, String> getClaimedChunksMap() { return Collections.unmodifiableMap(claimedChunks); }

    public String getFactionOwningChunk(Chunk chunk) {
        if (chunk == null) return null;
        return claimedChunks.get(new ChunkWrapper(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }
    public Map<UUID, TeleportRequest> getActiveTeleportRequests() { return activeTeleportRequests; }
    public DynmapManager getDynmapManager() { return dynmapManager; }

    // --- Faction Chat Management ---
    public boolean isPlayerInFactionChat(UUID playerUUID) {
        return playersInFactionChat.contains(playerUUID);
    }
    public void setPlayerFactionChat(UUID playerUUID, boolean inFactionChat) {
        if (inFactionChat) {
            playersInFactionChat.add(playerUUID);
        } else {
            playersInFactionChat.remove(playerUUID);
        }
    }


    public void initiateTeleportWarmup(Player player, Location targetLocation, String successMessage) {
        if (activeTeleportRequests.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already in the process of teleporting!");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Teleporting in " + TELEPORT_WARMUP_SECONDS + " seconds... Do not move or take damage!");
        int taskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            TeleportRequest completedRequest = activeTeleportRequests.remove(player.getUniqueId());
            if (completedRequest != null && player.isOnline()) { // Check if player still online
                Location finalCheckLoc = player.getLocation();
                // Check block coordinates and world
                if (finalCheckLoc.getBlockX() == completedRequest.getInitialBlockLocation().getBlockX() &&
                        finalCheckLoc.getBlockY() == completedRequest.getInitialBlockLocation().getBlockY() &&
                        finalCheckLoc.getBlockZ() == completedRequest.getInitialBlockLocation().getBlockZ() &&
                        Objects.equals(finalCheckLoc.getWorld(), completedRequest.getInitialBlockLocation().getWorld())) {
                    player.teleport(targetLocation);
                    player.sendMessage(ChatColor.GREEN + completedRequest.getTeleportMessage());
                } else {
                    player.sendMessage(ChatColor.RED + "Teleportation cancelled due to movement or world change.");
                }
            }
        }, TELEPORT_WARMUP_SECONDS * 20L).getTaskId();
        activeTeleportRequests.put(player.getUniqueId(), new TeleportRequest(player, targetLocation, taskId, successMessage));
    }

    public void cancelTeleport(UUID playerUUID, String cancelMessage) {
        TeleportRequest request = activeTeleportRequests.remove(playerUUID);
        if (request != null) {
            Bukkit.getScheduler().cancelTask(request.getTaskId());
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline() && cancelMessage != null && !cancelMessage.isEmpty()) {
                player.sendMessage(cancelMessage);
            }
        }
    }

    public void notifyFaction(Faction faction, String message, UUID excludePlayerUUID) {
        if (faction == null || message == null || message.isEmpty()) {
            getLogger().warning("notifyFaction called with null faction or empty message for faction: " + (faction != null ? faction.getName() : "UNKNOWN"));
            return;
        }
        for (UUID memberUUID : faction.getMemberUUIDsOnly()) {
            if (excludePlayerUUID != null && memberUUID.equals(excludePlayerUUID)) {
                continue;
            }
            Player memberPlayer = Bukkit.getPlayer(memberUUID);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                memberPlayer.sendMessage(message);
            }
        }
    }
}