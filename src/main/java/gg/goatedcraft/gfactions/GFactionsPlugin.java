package gg.goatedcraft.gfactions;

import gg.goatedcraft.gfactions.commands.AdminFactionCommand;
import gg.goatedcraft.gfactions.commands.FactionCommand;
import gg.goatedcraft.gfactions.data.ChunkWrapper;
import gg.goatedcraft.gfactions.data.Faction;
import gg.goatedcraft.gfactions.data.FactionRank;
import gg.goatedcraft.gfactions.data.Outpost;
import gg.goatedcraft.gfactions.data.TeleportRequest;
import gg.goatedcraft.gfactions.integration.DynmapManager;
import gg.goatedcraft.gfactions.listeners.PlayerChatListener;
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
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation") // For OfflinePlayer methods
public class GFactionsPlugin extends JavaPlugin {

    // --- Data Maps ---
    private final Map<String, Faction> factionsByNameKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerFactionMembership = new ConcurrentHashMap<>();
    private final Map<ChunkWrapper, String> claimedChunks = new ConcurrentHashMap<>(); // Key: ChunkWrapper, Value: FactionNameKey
    private final Map<String, Map<UUID, Long>> pendingMemberInvites = new ConcurrentHashMap<>(); // Key: FactionNameKey, Value: Map<PlayerUUID, ExpiryTimestamp>
    private final Map<String, Map<String, Long>> pendingAllyRequests = new ConcurrentHashMap<>(); // Key: TargetFactionNameKey, Value: Map<RequesterFactionNameKey, ExpiryTimestamp>
    private final Map<UUID, TeleportRequest> activeTeleportRequests = new ConcurrentHashMap<>();
    private final Set<UUID> playersInFactionChat = ConcurrentHashMap.newKeySet();

    private BukkitTask powerRegenTask;
    private DynmapManager dynmapManager;
    private File factionsDataFile;


    // Configurable Settings (with defaults)
    public int POWER_INITIAL = 100;
    public int POWER_PER_MEMBER_BONUS = 100;
    public int POWER_REGENERATION_AMOUNT = 1;
    public long POWER_REGENERATION_INTERVAL_TICKS = 72000L; // 1 hour
    public int POWER_LOSS_ON_DEATH_BY_ENEMY = 50;
    public int COST_CLAIM_CHUNK = 5;
    public int COST_OVERCLAIM_CHUNK = 20;
    public int COST_DECLARE_ENEMY = 10;
    public int COST_DECLARE_NEUTRAL = 10;
    public int COST_SEND_ALLY_REQUEST = 10;
    public int COST_PROMOTE_MEMBER = 2; // Example, usually free
    public int COST_TRUST_PLAYER = 1;   // Example, usually free
    public int COST_CREATE_OUTPOST = 50;
    public long COOLDOWN_ENEMY_NEUTRAL_HOURS = 24;
    public long EXPIRATION_MEMBER_INVITE_MINUTES = 5;
    public long EXPIRATION_ALLY_REQUEST_MINUTES = 5;
    public long TELEPORT_WARMUP_SECONDS = 5;
    public int TITLE_FADE_IN_TICKS = 5;
    public int TITLE_STAY_TICKS = 30;
    public int TITLE_FADE_OUT_TICKS = 5;
    public long TITLE_DISPLAY_COOLDOWN_SECONDS = 1;
    public boolean DYNMAP_ENABLED = false;
    public String DYNMAP_MARKERSET_LABEL = "Faction Claims";
    public int DYNMAP_STROKE_WEIGHT = 2;
    public double DYNMAP_STROKE_OPACITY = 0.80;
    public String DYNMAP_STROKE_COLOR = "0x000000";
    public double DYNMAP_FILL_OPACITY = 0.35;
    public int DYNMAP_COLOR_DEFAULT_CLAIM = 0x00FF00; // Green
    public int DYNMAP_COLOR_ENEMY_CLAIM = 0xFF0000;   // Red
    public int DYNMAP_COLOR_ALLY_CLAIM = 0x00FFFF;    // Cyan
    public int DYNMAP_COLOR_NEUTRAL_CLAIM = 0xFFFF00; // Yellow
    public int FACTION_NAME_MIN_LENGTH = 3;
    public int FACTION_NAME_MAX_LENGTH = 16;
    public Pattern FACTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    public String FACTION_CHAT_FORMAT = "&8[&a{FACTION_NAME}&8] &f{PLAYER_NAME}: &e{MESSAGE}";
    public int MAX_CLAIMS_PER_FACTION = 0; // 0 for unlimited
    public int MAX_MEMBERS_PER_FACTION = 0; // 0 for unlimited
    public int MAX_OUTPOSTS_PER_FACTION = 1;
    public boolean PREVENT_CLAIM_NEAR_SPAWN = true;
    public int SPAWN_PROTECTION_RADIUS = 50; // Blocks
    public Location SERVER_SPAWN_LOCATION;

    public String MESSAGE_ENTERING_WILDERNESS = ChatColor.translateAlternateColorCodes('&', "&7Now entering Wilderness");
    public String MESSAGE_ENTERING_FACTION_TERRITORY = ChatColor.translateAlternateColorCodes('&', "&7Now entering {FACTION_RELATION_COLOR}{FACTION_NAME}");


    @Override
    public void onEnable() {
        boolean enabledSuccessfully = false;
        getLogger().info(getName() + " is starting the enabling process...");
        try {
            // Ensure data folder exists
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            factionsDataFile = new File(getDataFolder(), "factions.yml");


            saveDefaultConfig(); // Ensures config.yml exists if not present

            getLogger().info("Loading plugin configuration...");
            loadPluginConfig();
            getLogger().info("Plugin configuration loaded. Dynmap config: " + (DYNMAP_ENABLED ? "Enabled" : "Disabled"));

            getLogger().info("Determining server spawn location...");
            if (Bukkit.getWorlds().isEmpty()) {
                getLogger().severe("No worlds loaded at onEnable! Cannot determine server spawn. Spawn protection might not work.");
                PREVENT_CLAIM_NEAR_SPAWN = false;
            } else {
                World defaultWorld = Bukkit.getWorlds().get(0); // Primary world
                if (defaultWorld != null) {
                    SERVER_SPAWN_LOCATION = defaultWorld.getSpawnLocation();
                    getLogger().info("Server spawn location identified at: " + SERVER_SPAWN_LOCATION.toString());
                } else {
                    getLogger().severe("Could not determine server spawn location (default world was null)! Spawn protection might not work correctly.");
                    PREVENT_CLAIM_NEAR_SPAWN = false;
                }
            }

            getLogger().info("Loading factions data...");
            loadFactionsData();
            getLogger().info("Factions data loaded. " + factionsByNameKey.size() + " factions processed.");

            getLogger().info("Registering commands...");
            FactionCommand factionCommand = new FactionCommand(this);
            PluginCommand fCmd = getCommand("faction");
            if (fCmd != null) {
                fCmd.setExecutor(factionCommand);
                fCmd.setTabCompleter(factionCommand);
            } else {
                getLogger().severe("FATAL: Could not find 'faction' command in plugin.yml! Commands will not work.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            AdminFactionCommand adminFactionCommand = new AdminFactionCommand(this);
            PluginCommand faCmd = getCommand("factionadmin");
            if (faCmd != null) {
                faCmd.setExecutor(adminFactionCommand);
                faCmd.setTabCompleter(adminFactionCommand);
            } else {
                getLogger().severe("Could not find 'factionadmin' command in plugin.yml! Admin commands will not work.");
            }
            getLogger().info("Commands registered.");

            getLogger().info("Registering event listeners...");
            getServer().getPluginManager().registerEvents(new ZoneProtectionListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerClaimBoundaryListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerTeleportListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);
            getLogger().info("Event listeners registered.");

            getLogger().info("Starting power regeneration task...");
            startPowerRegeneration();
            getLogger().info("Power regeneration task started/checked.");

            if (DYNMAP_ENABLED) {
                getLogger().info("Initializing Dynmap integration (delayed)...");
                this.dynmapManager = new DynmapManager(this);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        Plugin dynmapPluginHook = getServer().getPluginManager().getPlugin("dynmap");
                        if (dynmapPluginHook != null && dynmapPluginHook.isEnabled()) {
                            getLogger().info("Attempting to activate Dynmap integration for " + getName() + "...");
                            dynmapManager.activate(); // This will call updateAllFactionClaimsVisuals
                        } else {
                            getLogger().info("Dynmap plugin not found or not enabled for delayed activation. Integration will remain disabled.");
                        }
                    } catch (Exception e_dynmap) {
                        getLogger().log(Level.SEVERE, "Error during delayed Dynmap activation for " + getName(), e_dynmap);
                    }
                }, 20L); // 1 second delay
            } else {
                getLogger().info("Dynmap integration is disabled in GFactions configuration.");
            }

            enabledSuccessfully = true;

        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "A critical error occurred during GFactions onEnable phase, plugin will be disabled:", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (enabledSuccessfully) {
            getLogger().info(getName() + " has been successfully enabled and initialized!");
        } else {
            getLogger().severe(getName() + " completed onEnable but was not marked as successfully enabled. Check for earlier critical errors.");
        }
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

        getLogger().info("Attempting to save factions data during disable...");
        saveFactionsData();
        getLogger().info(getName() + " has been disabled.");
    }

    public void loadPluginConfig() {
        FileConfiguration config = getConfig();

        POWER_INITIAL = config.getInt("power.initial", 100);
        POWER_PER_MEMBER_BONUS = config.getInt("power.per_member_bonus", 100);
        POWER_REGENERATION_AMOUNT = config.getInt("power.regeneration_amount", 1);
        POWER_REGENERATION_INTERVAL_TICKS = config.getLong("power.regeneration_interval_hours", 1) * 60 * 60 * 20; // hours to ticks
        POWER_LOSS_ON_DEATH_BY_ENEMY = config.getInt("power.loss_on_death_by_enemy", 50);

        COST_CLAIM_CHUNK = config.getInt("power.cost.claim_chunk", 5);
        COST_OVERCLAIM_CHUNK = config.getInt("power.cost.overclaim_chunk", 20);
        COST_DECLARE_ENEMY = config.getInt("power.cost.declare_enemy", 10);
        COST_DECLARE_NEUTRAL = config.getInt("power.cost.declare_neutral", 10);
        COST_SEND_ALLY_REQUEST = config.getInt("power.cost.send_ally_request", 10);
        COST_PROMOTE_MEMBER = config.getInt("power.cost.promote_member", 0); // Typically free
        COST_TRUST_PLAYER = config.getInt("power.cost.trust_player", 0);     // Typically free
        COST_CREATE_OUTPOST = config.getInt("power.cost.create_outpost", 50);


        COOLDOWN_ENEMY_NEUTRAL_HOURS = config.getLong("cooldowns.enemy_neutral_declaration_hours", 24);
        EXPIRATION_MEMBER_INVITE_MINUTES = config.getLong("cooldowns.member_invite_expiration_minutes", 5);
        EXPIRATION_ALLY_REQUEST_MINUTES = config.getLong("cooldowns.ally_request_expiration_minutes", 5);

        TELEPORT_WARMUP_SECONDS = config.getLong("teleport.warmup_seconds", 5);

        TITLE_FADE_IN_TICKS = config.getInt("titles.fade_in_ticks", 10); // Adjusted for visibility
        TITLE_STAY_TICKS = config.getInt("titles.stay_ticks", 40);    // Adjusted
        TITLE_FADE_OUT_TICKS = config.getInt("titles.fade_out_ticks", 10); // Adjusted
        TITLE_DISPLAY_COOLDOWN_SECONDS = config.getLong("titles.display_cooldown_seconds", 1); // Short for testing, can be increased

        DYNMAP_ENABLED = config.getBoolean("dynmap.enabled", false);
        DYNMAP_MARKERSET_LABEL = config.getString("dynmap.markerset_label", "Faction Claims");
        DYNMAP_STROKE_WEIGHT = config.getInt("dynmap.style.stroke_weight", 2);
        DYNMAP_STROKE_OPACITY = config.getDouble("dynmap.style.stroke_opacity", 0.80);
        DYNMAP_STROKE_COLOR = config.getString("dynmap.style.stroke_color", "0x000000"); // Black
        DYNMAP_FILL_OPACITY = config.getDouble("dynmap.style.fill_opacity", 0.35);

        try {
            DYNMAP_COLOR_DEFAULT_CLAIM = Integer.decode(config.getString("dynmap.style.default_claim_color", "0x00FF00")); // Green
            DYNMAP_COLOR_ENEMY_CLAIM = Integer.decode(config.getString("dynmap.style.enemy_claim_color", "0xFF0000"));   // Red
            DYNMAP_COLOR_ALLY_CLAIM = Integer.decode(config.getString("dynmap.style.ally_claim_color", "0x00FFFF"));    // Cyan
            DYNMAP_COLOR_NEUTRAL_CLAIM = Integer.decode(config.getString("dynmap.style.neutral_claim_color", "0xFFFF00")); // Yellow
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid hex color format in Dynmap style settings. Using internal defaults.");
            // Defaults are already set if decode fails, but can be re-asserted here if needed.
        }

        FACTION_NAME_MIN_LENGTH = config.getInt("faction_details.name_min_length", 3);
        FACTION_NAME_MAX_LENGTH = config.getInt("faction_details.name_max_length", 16);
        String regex = config.getString("faction_details.name_regex", "^[a-zA-Z0-9_]+$");
        try {
            FACTION_NAME_PATTERN = Pattern.compile(regex);
        } catch (Exception e) {
            getLogger().warning("Invalid faction_name_regex: " + regex + ". Using default: ^[a-zA-Z0-9_]+$");
            FACTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
        }
        MAX_OUTPOSTS_PER_FACTION = config.getInt("faction_details.max_outposts", 1);

        String chatFormatRaw = config.getString("faction_details.faction_chat_format", "&8[&a{FACTION_NAME}&8] &f{PLAYER_NAME}: &e{MESSAGE}");
        FACTION_CHAT_FORMAT = ChatColor.translateAlternateColorCodes('&', chatFormatRaw);

        MAX_CLAIMS_PER_FACTION = config.getInt("claiming.max_claims_per_faction", 0); // 0 for unlimited
        MAX_MEMBERS_PER_FACTION = config.getInt("claiming.max_members_per_faction", 0); // 0 for unlimited
        PREVENT_CLAIM_NEAR_SPAWN = config.getBoolean("claiming.prevent_claim_near_spawn", true);
        SPAWN_PROTECTION_RADIUS = config.getInt("claiming.spawn_protection_radius", 50);

        MESSAGE_ENTERING_WILDERNESS = ChatColor.translateAlternateColorCodes('&', config.getString("messages.entering_wilderness", "&7Now entering Wilderness"));
        MESSAGE_ENTERING_FACTION_TERRITORY = ChatColor.translateAlternateColorCodes('&', config.getString("messages.entering_faction_territory", "&7Now entering {FACTION_RELATION_COLOR}{FACTION_NAME}"));

        getLogger().info("GFactions configuration values have been processed.");
    }


    private void startPowerRegeneration() {
        if (powerRegenTask != null && !powerRegenTask.isCancelled()) {
            powerRegenTask.cancel();
        }
        if (POWER_REGENERATION_AMOUNT <= 0 || POWER_REGENERATION_INTERVAL_TICKS <= 0) {
            getLogger().info("Power regeneration is disabled due to amount or interval being zero or less.");
            return;
        }
        powerRegenTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Faction faction : factionsByNameKey.values()) {
                if (faction.getCurrentPower() < faction.getMaxPower()) {
                    faction.addPower(POWER_REGENERATION_AMOUNT);
                    // Optionally notify faction or log, but can be spammy
                }
            }
        }, POWER_REGENERATION_INTERVAL_TICKS, POWER_REGENERATION_INTERVAL_TICKS);
        getLogger().info("Power regeneration task scheduled to run every " + POWER_REGENERATION_INTERVAL_TICKS + " ticks (" + (POWER_REGENERATION_INTERVAL_TICKS/20.0/3600.0) + " hours).");
    }

    @Nullable
    public Faction createFactionAndReturn(Player owner, String name) {
        String nameKey = name.toLowerCase();
        if (factionsByNameKey.containsKey(nameKey)) {
            getLogger().warning("Attempt to create faction with existing nameKey: " + nameKey);
            return null; // Faction already exists
        }
        if (playerFactionMembership.containsKey(owner.getUniqueId())) {
            getLogger().warning("Player " + owner.getName() + " tried to create faction while already in one.");
            return null; // Player already in a faction
        }

        Chunk initialChunk = owner.getLocation().getChunk();
        ChunkWrapper initialChunkWrapper = new ChunkWrapper(initialChunk.getWorld().getName(), initialChunk.getX(), initialChunk.getZ());

        // Check if this specific chunk is already claimed by someone else (should be rare if creating)
        if (claimedChunks.containsKey(initialChunkWrapper)) {
            owner.sendMessage(ChatColor.RED + "This exact chunk is already claimed by another faction. Try moving slightly.");
            return null;
        }


        Faction faction = new Faction(name, owner.getUniqueId(), owner.getLocation(), this); // Pass 'this' (plugin instance)
        // Faction constructor and setHomeLocation now handle adding the home chunk to allClaimedChunks.

        claimedChunks.put(initialChunkWrapper, nameKey); // Add to global map
        factionsByNameKey.put(nameKey, faction);
        playerFactionMembership.put(owner.getUniqueId(), nameKey);

        getLogger().info("Faction '" + name + "' created by " + owner.getName() + ". Home chunk: " + initialChunkWrapper.toStringShort());

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionClaimsVisual(faction); // Update map for the new faction and its first claim
        }
        saveFactionsData(); // Save after creation
        return faction;
    }
    public boolean createFaction(Player owner, String name) {
        return createFactionAndReturn(owner, name) != null;
    }


    @Nullable
    public Faction getFaction(String nameOrKey) {
        if (nameOrKey == null) return null;
        return factionsByNameKey.get(nameOrKey.toLowerCase());
    }

    @Nullable
    public Faction getFactionByPlayer(UUID playerUUID) {
        String factionNameKey = playerFactionMembership.get(playerUUID);
        if (factionNameKey != null) {
            return factionsByNameKey.get(factionNameKey);
        }
        return null;
    }

    public boolean transferOwnership(Faction faction, Player currentOwner, OfflinePlayer newOwnerOfflinePlayer) {
        if (!faction.isOwner(currentOwner.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + "You are not the owner of this faction.");
            return false;
        }
        if (!faction.getMembers().containsKey(newOwnerOfflinePlayer.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + newOwnerOfflinePlayer.getName() + " is not a member of your faction.");
            return false;
        }
        if (currentOwner.getUniqueId().equals(newOwnerOfflinePlayer.getUniqueId())) {
            currentOwner.sendMessage(ChatColor.RED + "You are already the owner.");
            return false;
        }

        faction.setOwnerUUID(newOwnerOfflinePlayer.getUniqueId()); // This also handles rank changes internally
        saveFactionsData();
        return true;
    }

    public boolean sendMemberInvite(Faction invitingFaction, UUID invitedPlayerUUID) {
        if (getFactionByPlayer(invitedPlayerUUID) != null) return false; // Already in a faction
        if (invitingFaction.getMembers().containsKey(invitedPlayerUUID)) return false; // Already a member

        // Check for existing invite from this faction
        Map<UUID, Long> factionInvites = pendingMemberInvites.getOrDefault(invitingFaction.getNameKey(), new HashMap<>());
        if (factionInvites.containsKey(invitedPlayerUUID) && factionInvites.get(invitedPlayerUUID) > System.currentTimeMillis()) {
            return false; // Active invite already exists
        }

        if (MAX_MEMBERS_PER_FACTION > 0 && invitingFaction.getMembers().size() >= MAX_MEMBERS_PER_FACTION) {
            // Faction is full
            Player owner = Bukkit.getPlayer(invitingFaction.getOwnerUUID());
            if(owner != null && owner.isOnline()){ // Notify owner or admins if online
                owner.sendMessage(ChatColor.RED + "Could not invite " + Bukkit.getOfflinePlayer(invitedPlayerUUID).getName() + ". Faction is full.");
            }
            return false;
        }


        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(EXPIRATION_MEMBER_INVITE_MINUTES);
        pendingMemberInvites.computeIfAbsent(invitingFaction.getNameKey(), k -> new ConcurrentHashMap<>()).put(invitedPlayerUUID, expiryTime);
        saveFactionsData(); // Save invites
        return true;
    }
    public void revokeMemberInvite(Faction invitingFaction, OfflinePlayer invitedOfflinePlayer) {
        Map<UUID, Long> factionInvites = pendingMemberInvites.get(invitingFaction.getNameKey());
        if (factionInvites != null) {
            if (factionInvites.remove(invitedOfflinePlayer.getUniqueId()) != null) {
                saveFactionsData(); // Save if an invite was actually removed
            }
        }
    }


    public boolean acceptMemberInvite(UUID playerUUID, String factionNameKeyToJoin) {
        Faction targetFaction = factionsByNameKey.get(factionNameKeyToJoin.toLowerCase());
        if (targetFaction == null) return false; // Faction doesn't exist

        Map<UUID, Long> invitesForFaction = pendingMemberInvites.get(targetFaction.getNameKey());
        if (invitesForFaction == null || !invitesForFaction.containsKey(playerUUID) || invitesForFaction.get(playerUUID) < System.currentTimeMillis()) {
            return false; // No valid invite or expired
        }
        if (getFactionByPlayer(playerUUID) != null) return false; // Already in a faction

        if (MAX_MEMBERS_PER_FACTION > 0 && targetFaction.getMembers().size() >= MAX_MEMBERS_PER_FACTION) {
            // Faction became full after invite was sent
            Player player = Bukkit.getPlayer(playerUUID);
            if(player != null) player.sendMessage(ChatColor.RED + "Cannot join " + targetFaction.getName() + ", the faction is now full.");
            invitesForFaction.remove(playerUUID); // Clean up invite
            saveFactionsData();
            return false;
        }


        if (targetFaction.addMember(playerUUID, FactionRank.MEMBER)) {
            playerFactionMembership.put(playerUUID, targetFaction.getNameKey());
            invitesForFaction.remove(playerUUID); // Remove invite after successful join
            saveFactionsData();
            return true;
        }
        return false; // Should not happen if previous checks are fine
    }

    public Map<UUID, Long> getPendingMemberInvitesForFaction(String inviterFactionKey) {
        // Clean up expired invites before returning
        Map<UUID, Long> invites = pendingMemberInvites.get(inviterFactionKey.toLowerCase());
        if (invites != null) {
            long currentTime = System.currentTimeMillis();
            invites.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            if(invites.isEmpty()) pendingMemberInvites.remove(inviterFactionKey.toLowerCase()); // remove map if empty
            return Collections.unmodifiableMap(new HashMap<>(invites)); // Return a copy
        }
        return Collections.emptyMap();
    }


    public boolean removePlayerFromFaction(Faction faction, UUID playerUUID, boolean isKick) {
        if (!faction.getMembers().containsKey(playerUUID)) return false; // Not a member

        if (faction.isOwner(playerUUID)) {
            if (faction.getMembers().size() == 1) { // Owner is the last member
                // Disband faction if owner leaves and is last member
                disbandFactionInternal(faction, false); // Not an admin action
                return true; // Player is removed as faction is gone
            } else if (!isKick) { // Owner trying to leave but not last member
                Player ownerPlayer = Bukkit.getPlayer(playerUUID);
                if (ownerPlayer != null) {
                    ownerPlayer.sendMessage(ChatColor.RED + "You must transfer ownership or disband the faction.");
                }
                return false;
            }
            // If it's a kick, owner cannot be kicked (handled in command)
        }

        if (faction.removeMember(playerUUID)) {
            playerFactionMembership.remove(playerUUID);
            // Remove from trusted list if they were there (though members shouldn't be explicitly trusted)
            faction.removeTrusted(playerUUID);
            saveFactionsData();
            return true;
        }
        return false;
    }

    public boolean claimChunk(Faction faction, Chunk chunk, boolean isOverclaim, boolean isOutpostCreation, @Nullable Outpost attachingToOutpost) {
        Player player = Bukkit.getPlayer(faction.getOwnerUUID()); // Get a player from faction for messages, preferably an admin/owner performing action
        if (faction.getMembers().keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().isPresent()){
            player = faction.getMembers().keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).findFirst().get();
        }


        ChunkWrapper cw = new ChunkWrapper(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        String currentOwnerKey = claimedChunks.get(cw);
        Faction currentOwnerFaction = (currentOwnerKey != null) ? getFaction(currentOwnerKey) : null;

        // Spawn Protection
        if (PREVENT_CLAIM_NEAR_SPAWN && SERVER_SPAWN_LOCATION != null) {
            Location chunkCenter = new Location(chunk.getWorld(), chunk.getX() * 16 + 8, (player != null ? player.getLocation().getY() : 64), chunk.getZ() * 16 + 8);
            if (chunkCenter.getWorld().equals(SERVER_SPAWN_LOCATION.getWorld()) &&
                    chunkCenter.distanceSquared(SERVER_SPAWN_LOCATION) < SPAWN_PROTECTION_RADIUS * SPAWN_PROTECTION_RADIUS) {
                if (player != null && !player.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                    player.sendMessage(ChatColor.RED + "You cannot claim land this close to server spawn.");
                    return false;
                }
            }
        }

        // Max claims check (only if not outpost creation, as outposts might have separate limits or always be 1 chunk)
        if (!isOutpostCreation && MAX_CLAIMS_PER_FACTION > 0 && faction.getClaimedChunks().size() >= MAX_CLAIMS_PER_FACTION) {
            if (player != null) player.sendMessage(ChatColor.RED + "Your faction has reached the maximum claim limit (" + MAX_CLAIMS_PER_FACTION + ").");
            return false;
        }
        // Adjacency check for normal claims (not for first claim or outpost creation)
        if (!isOutpostCreation && !faction.getClaimedChunks().isEmpty() && !faction.isChunkAdjacentToExistingClaim(cw)) {
            if (attachingToOutpost == null || !faction.isChunkAdjacentTo(cw, attachingToOutpost.getOutpostSpecificClaims())) {
                if (player != null) player.sendMessage(ChatColor.RED + "You must claim land adjacent to your existing territory or an outpost's territory.");
                return false;
            }
        }


        if (currentOwnerFaction != null) { // Land is already claimed
            if (currentOwnerFaction.equals(faction)) {
                if (player != null) player.sendMessage(ChatColor.YELLOW + "Your faction already owns this land.");
                return false; // Already owns it
            }
            // Overclaiming logic
            if (!isOverclaim) { // This flag should be set if it's an intentional overclaim attempt
                if (player != null) player.sendMessage(ChatColor.RED + "This land is claimed by " + currentOwnerFaction.getName() + ". Overclaiming is not yet fully implemented or requires specific command.");
                return false; // For now, basic claim doesn't overclaim
            }
            if (currentOwnerFaction.getCurrentPower() > 0) { // Cannot overclaim if target has power
                if (player != null) player.sendMessage(ChatColor.RED + currentOwnerFaction.getName() + " still has power ("+currentOwnerFaction.getCurrentPower()+") and cannot be overclaimed.");
                return false;
            }
            if (faction.getCurrentPower() < COST_OVERCLAIM_CHUNK) {
                if (player != null) player.sendMessage(ChatColor.RED + "Not enough power to overclaim. Cost: " + COST_OVERCLAIM_CHUNK);
                return false;
            }
            faction.removePower(COST_OVERCLAIM_CHUNK);
            currentOwnerFaction.removeClaim(cw); // Remove from old owner
            getLogger().info("Faction " + faction.getName() + " overclaimed chunk " + cw.toStringShort() + " from " + currentOwnerFaction.getName());
            notifyFaction(currentOwnerFaction, ChatColor.DARK_RED + "Your land at (" + cw.getX() + "," + cw.getZ() + ") has been overclaimed by " + faction.getName() + "!", null);

        } else { // Wilderness claim
            if (faction.getCurrentPower() < COST_CLAIM_CHUNK && !isOutpostCreation) { // Outpost creation cost is separate
                if (player != null) player.sendMessage(ChatColor.RED + "Not enough power to claim. Cost: " + COST_CLAIM_CHUNK);
                return false;
            }
            if(!isOutpostCreation) faction.removePower(COST_CLAIM_CHUNK);
        }

        // Perform the claim
        if (isOutpostCreation && attachingToOutpost != null) {
            faction.addClaim(cw, true, attachingToOutpost); // Associates with specific outpost
        } else {
            faction.addClaim(cw); // Adds to general claims, potentially merging outposts if adjacent
        }
        claimedChunks.put(cw, faction.getNameKey());


        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionClaimsVisual(faction);
            if (currentOwnerFaction != null) {
                dynmapManager.updateFactionClaimsVisual(currentOwnerFaction); // Update previous owner too
            }
        }
        saveFactionsData();
        return true;
    }

    public void unclaimChunkPlayer(Faction faction, ChunkWrapper chunkWrapper) {
        if (!faction.getClaimedChunks().contains(chunkWrapper)) return; // Not their claim

        // Prevent unclaiming home chunk if it's the last main territory chunk
        if (chunkWrapper.equals(faction.getHomeChunk())) {
            Set<ChunkWrapper> mainTerritory = new HashSet<>(faction.getClaimedChunks());
            faction.getOutposts().forEach(op -> mainTerritory.removeAll(op.getOutpostSpecificClaims()));
            if (mainTerritory.size() <= 1) {
                Player owner = Bukkit.getPlayer(faction.getOwnerUUID());
                if (owner != null && owner.isOnline()) { // Or any admin online
                    owner.sendMessage(ChatColor.RED + "You cannot unclaim your faction's only main territory chunk that also contains your home. Set home elsewhere or unclaim other chunks first.");
                }
                return; // Prevent unclaiming last main territory chunk if it's home
            }
        }

        // Prevent unclaiming outpost spawn chunk if it's the last claim of that outpost
        Outpost outpostOwningChunk = faction.getOutpostBySpawnChunk(chunkWrapper);
        if (outpostOwningChunk != null && outpostOwningChunk.getOutpostSpecificClaims().size() <=1 && outpostOwningChunk.getOutpostSpecificClaims().contains(chunkWrapper)) {
            Player owner = Bukkit.getPlayer(faction.getOwnerUUID());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(ChatColor.RED + "You cannot unclaim the last chunk of Outpost #" + outpostOwningChunk.getOutpostID() + ". Delete the outpost instead using /f outpost delete " + outpostOwningChunk.getOutpostID());
            }
            return;
        }


        faction.removeClaim(chunkWrapper); // This handles home relocation if needed
        claimedChunks.remove(chunkWrapper);
        // Power is not typically refunded on unclaim by player.

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionClaimsVisual(faction);
        }
        saveFactionsData();
    }
    public void unclaimChunkAdmin(ChunkWrapper chunkWrapper) {
        String ownerKey = claimedChunks.remove(chunkWrapper);
        if (ownerKey != null) {
            Faction faction = getFaction(ownerKey);
            if (faction != null) {
                faction.removeClaim(chunkWrapper); // This handles home relocation if needed
                if (dynmapManager != null && dynmapManager.isEnabled()) {
                    dynmapManager.updateFactionClaimsVisual(faction);
                }
                getLogger().info("Admin unclaimed chunk " + chunkWrapper.toStringShort() + " from " + faction.getName());
            }
        }
        saveFactionsData();
    }


    public boolean sendAllyRequest(Faction requesterFaction, Faction targetFaction) {
        if (requesterFaction.getCurrentPower() < COST_SEND_ALLY_REQUEST) {
            Player p = Bukkit.getPlayer(requesterFaction.getOwnerUUID());
            if (p!=null) p.sendMessage(ChatColor.RED + "Not enough power to send ally request. Cost: " + COST_SEND_ALLY_REQUEST);
            return false;
        }
        // Check for existing pending request (either way)
        Map<String, Long> targetRequests = pendingAllyRequests.getOrDefault(targetFaction.getNameKey(), new HashMap<>());
        if (targetRequests.containsKey(requesterFaction.getNameKey()) && targetRequests.get(requesterFaction.getNameKey()) > System.currentTimeMillis()) {
            return false; // Request already sent to them
        }
        Map<String, Long> requesterRequests = pendingAllyRequests.getOrDefault(requesterFaction.getNameKey(), new HashMap<>());
        if (requesterRequests.containsKey(targetFaction.getNameKey()) && requesterRequests.get(targetFaction.getNameKey()) > System.currentTimeMillis()) {
            return false; // They already sent one to us
        }


        requesterFaction.removePower(COST_SEND_ALLY_REQUEST);
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(EXPIRATION_ALLY_REQUEST_MINUTES);
        pendingAllyRequests.computeIfAbsent(targetFaction.getNameKey(), k -> new ConcurrentHashMap<>()).put(requesterFaction.getNameKey(), expiryTime);
        saveFactionsData();
        return true;
    }

    public boolean acceptAllyRequest(Faction acceptingFaction, Faction requestingFaction) {
        Map<String, Long> requestsForAccepting = pendingAllyRequests.get(acceptingFaction.getNameKey());
        if (requestsForAccepting == null || !requestsForAccepting.containsKey(requestingFaction.getNameKey()) || requestsForAccepting.get(requestingFaction.getNameKey()) < System.currentTimeMillis()) {
            return false; // No valid request
        }

        acceptingFaction.addAlly(requestingFaction.getNameKey());
        requestingFaction.addAlly(acceptingFaction.getNameKey());
        requestsForAccepting.remove(requestingFaction.getNameKey()); // Remove accepted request
        // Also remove any counter-request from accepting to requesting, if it existed
        Map<String, Long> requestsForRequesting = pendingAllyRequests.get(requestingFaction.getNameKey());
        if (requestsForRequesting != null) {
            requestsForRequesting.remove(acceptingFaction.getNameKey());
        }

        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionRelations(acceptingFaction, requestingFaction);
        }
        saveFactionsData();
        return true;
    }
    public Map<String, Long> getPendingAllyRequestsFor(String targetFactionNameKey) {
        Map<String, Long> requests = pendingAllyRequests.get(targetFactionNameKey.toLowerCase());
        if (requests != null) {
            long currentTime = System.currentTimeMillis();
            requests.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            if(requests.isEmpty()) pendingAllyRequests.remove(targetFactionNameKey.toLowerCase());
            return Collections.unmodifiableMap(new HashMap<>(requests));
        }
        return Collections.emptyMap();
    }


    public void revokeAlliance(Faction faction1, Faction faction2) {
        boolean changed1 = faction1.removeAlly(faction2.getNameKey());
        boolean changed2 = faction2.removeAlly(faction1.getNameKey());
        if (changed1 || changed2) {
            if (dynmapManager != null && dynmapManager.isEnabled()) {
                dynmapManager.updateFactionRelations(faction1, faction2);
            }
            saveFactionsData();
        }
    }


    @SuppressWarnings("unchecked")
    private void loadFactionsData() {
        if (!factionsDataFile.exists()) {
            getLogger().info("No factions.yml found, starting fresh.");
            return;
        }
        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(factionsDataFile);

        // Load Factions
        ConfigurationSection factionsSection = dataConfig.getConfigurationSection("factions");
        if (factionsSection != null) {
            for (String nameKey : factionsSection.getKeys(false)) {
                ConfigurationSection facSec = factionsSection.getConfigurationSection(nameKey);
                if (facSec == null) continue;

                String originalName = facSec.getString("originalName");
                UUID ownerUUID = UUID.fromString(facSec.getString("ownerUUID"));
                Location homeLoc = facSec.getLocation("homeLocation"); // Bukkit's getLocation handles world loading

                Faction faction = new Faction(originalName, ownerUUID, homeLoc, this); // Pass plugin instance
                faction.setCurrentPower(facSec.getInt("currentPower"));

                // Members
                ConfigurationSection membersSec = facSec.getConfigurationSection("members");
                if (membersSec != null) {
                    for (String uuidStr : membersSec.getKeys(false)) {
                        try {
                            UUID memberUUID = UUID.fromString(uuidStr);
                            FactionRank rank = FactionRank.fromString(membersSec.getString(uuidStr));
                            if (!memberUUID.equals(ownerUUID)) { // Owner is added by constructor
                                faction.addMember(memberUUID, rank);
                            } else { // Ensure owner's rank is correctly set if stored
                                Map<UUID, FactionRank> internalMembersMap = faction.getMembers(); // This is a bit hacky, need a setter or better constructor
                                if (internalMembersMap instanceof HashMap) { // Check if modifiable (it is in current Faction.java)
                                    ((HashMap<UUID, FactionRank>)internalMembersMap).put(ownerUUID, rank);
                                }
                            }
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Invalid UUID or Rank for member in faction " + nameKey + ": " + uuidStr);
                        }
                    }
                }
                faction.updatePowerOnMemberChangeAndLoad(); // Recalculate max power and adjust current if needed

                // Claims
                List<String> claimStrings = facSec.getStringList("claimedChunks");
                for (String s : claimStrings) {
                    ChunkWrapper cw = ChunkWrapper.fromString(s);
                    if (cw != null) {
                        faction.getAllClaimedChunks_Modifiable().add(cw); // Use modifiable set for loading
                        claimedChunks.put(cw, nameKey);
                    }
                }
                // Ensure home chunk is in allClaimedChunks if homeLoc is set
                if (faction.getHomeChunk() != null && !faction.getClaimedChunks().contains(faction.getHomeChunk())) {
                    faction.getAllClaimedChunks_Modifiable().add(faction.getHomeChunk());
                    claimedChunks.put(faction.getHomeChunk(), nameKey);
                }


                // Outposts
                List<Map<?, ?>> outpostMaps = facSec.getMapList("outposts");
                for (Map<?, ?> outpostMap : outpostMaps) {
                    try {
                        // Ensure the map is of the correct type for Outpost.deserialize
                        Map<String, Object> correctlyTypedMap = new HashMap<>();
                        for (Map.Entry<?, ?> entry : outpostMap.entrySet()) {
                            if (entry.getKey() instanceof String) {
                                correctlyTypedMap.put((String) entry.getKey(), entry.getValue());
                            }
                        }
                        Outpost outpost = Outpost.deserialize(correctlyTypedMap);
                        if (outpost != null) {
                            faction.addOutpost(outpost); // This also adds outpost claims to faction's allClaimedChunks
                            // Ensure outpost claims are also in the global map
                            outpost.getOutpostSpecificClaims().forEach(cw -> claimedChunks.put(cw, nameKey));
                        }
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "Error deserializing an outpost for faction " + nameKey, e);
                    }
                }


                // Relations
                facSec.getStringList("enemies").forEach(enemyKey -> faction.addEnemy(enemyKey, System.currentTimeMillis())); // Timestamp will be recent, consider storing actual
                facSec.getStringList("allies").forEach(faction::addAlly);
                ConfigurationSection enemyTimeSec = facSec.getConfigurationSection("enemyDeclareTimestamps");
                if (enemyTimeSec != null) {
                    for (String enemyKey : enemyTimeSec.getKeys(false)) {
                        faction.getEnemyDeclareTimestamps().put(enemyKey, enemyTimeSec.getLong(enemyKey));
                    }
                }

                // Trusted
                facSec.getStringList("trustedPlayers").forEach(uuidStr -> {
                    try { faction.addTrusted(UUID.fromString(uuidStr)); }
                    catch (IllegalArgumentException ignored) {}
                });

                // Vault
                List<?> vaultItemsRaw = facSec.getList("vaultContents");
                if (vaultItemsRaw != null) {
                    ItemStack[] vaultContents = new ItemStack[Faction.VAULT_SIZE];
                    for (int i = 0; i < vaultItemsRaw.size() && i < Faction.VAULT_SIZE; i++) {
                        if (vaultItemsRaw.get(i) instanceof ItemStack) {
                            vaultContents[i] = (ItemStack) vaultItemsRaw.get(i);
                        }
                    }
                    faction.setVaultContents(vaultContents);
                }

                faction.lateInitPluginReference(this); // Critical for plugin dependent methods in Faction
                factionsByNameKey.put(nameKey, faction);
                faction.getMembers().keySet().forEach(uuid -> playerFactionMembership.put(uuid, nameKey));
            }
        }

        // Load Pending Invites (Member)
        ConfigurationSection memberInvitesSection = dataConfig.getConfigurationSection("pendingMemberInvites");
        if (memberInvitesSection != null) {
            for (String factionKey : memberInvitesSection.getKeys(false)) {
                ConfigurationSection specificInvites = memberInvitesSection.getConfigurationSection(factionKey);
                if (specificInvites != null) {
                    Map<UUID, Long> invites = new ConcurrentHashMap<>();
                    for (String playerUUIDStr : specificInvites.getKeys(false)) {
                        try {
                            invites.put(UUID.fromString(playerUUIDStr), specificInvites.getLong(playerUUIDStr));
                        } catch (IllegalArgumentException e) { getLogger().warning("Bad UUID in member invites: " + playerUUIDStr); }
                    }
                    if (!invites.isEmpty()) pendingMemberInvites.put(factionKey, invites);
                }
            }
        }

        // Load Pending Ally Requests
        ConfigurationSection allyRequestsSection = dataConfig.getConfigurationSection("pendingAllyRequests");
        if (allyRequestsSection != null) {
            for (String targetFacKey : allyRequestsSection.getKeys(false)) {
                ConfigurationSection specificRequests = allyRequestsSection.getConfigurationSection(targetFacKey);
                if (specificRequests != null) {
                    Map<String, Long> requests = new ConcurrentHashMap<>();
                    for (String requesterFacKey : specificRequests.getKeys(false)) {
                        requests.put(requesterFacKey, specificRequests.getLong(requesterFacKey));
                    }
                    if (!requests.isEmpty()) pendingAllyRequests.put(targetFacKey, requests);
                }
            }
        }
        getLogger().info("Loaded " + factionsByNameKey.size() + " factions, " + claimedChunks.size() + " claimed chunks.");
    }


    public void saveFactionsData() {
        YamlConfiguration dataConfig = new YamlConfiguration();
        ConfigurationSection factionsSection = dataConfig.createSection("factions");

        for (Map.Entry<String, Faction> entry : factionsByNameKey.entrySet()) {
            String nameKey = entry.getKey();
            Faction faction = entry.getValue();
            ConfigurationSection facSec = factionsSection.createSection(nameKey);

            facSec.set("originalName", faction.getName());
            facSec.set("ownerUUID", faction.getOwnerUUID().toString());
            if (faction.getHomeLocation() != null) {
                facSec.set("homeLocation", faction.getHomeLocation());
            }
            facSec.set("currentPower", faction.getCurrentPower());

            ConfigurationSection membersSec = facSec.createSection("members");
            faction.getMembers().forEach((uuid, rank) -> membersSec.set(uuid.toString(), rank.name()));

            // Save all unique claimed chunks (includes main territory and outpost claims)
            facSec.set("claimedChunks", faction.getClaimedChunks().stream().map(ChunkWrapper::toString).distinct().collect(Collectors.toList()));

            // Save outposts
            facSec.set("outposts", faction.getOutposts().stream().map(Outpost::serialize).collect(Collectors.toList()));


            facSec.set("enemies", new ArrayList<>(faction.getEnemyFactionKeys()));
            facSec.set("allies", new ArrayList<>(faction.getAllyFactionKeys()));

            ConfigurationSection enemyTimeSec = facSec.createSection("enemyDeclareTimestamps");
            faction.getEnemyDeclareTimestamps().forEach(enemyTimeSec::set);


            facSec.set("trustedPlayers", faction.getTrustedPlayers().stream().map(UUID::toString).collect(Collectors.toList()));
            facSec.set("vaultContents", Arrays.asList(faction.getVaultContentsForSave()));
        }

        // Save Pending Invites (Member)
        ConfigurationSection memberInvitesSection = dataConfig.createSection("pendingMemberInvites");
        pendingMemberInvites.forEach((factionKey, invitesMap) -> {
            ConfigurationSection specificInvites = memberInvitesSection.createSection(factionKey);
            invitesMap.forEach((playerUUID, expiry) -> specificInvites.set(playerUUID.toString(), expiry));
        });


        // Save Pending Ally Requests
        ConfigurationSection allyRequestsSection = dataConfig.createSection("pendingAllyRequests");
        pendingAllyRequests.forEach((targetFacKey, requestsMap) -> {
            ConfigurationSection specificRequests = allyRequestsSection.createSection(targetFacKey);
            requestsMap.forEach(specificRequests::set);
        });


        try {
            dataConfig.save(factionsDataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save factions.yml", e);
        }
    }


    public void disbandFactionInternal(Faction factionToDisband, boolean isAdminAction) {
        String nameKey = factionToDisband.getNameKey();

        // Remove all claims
        new HashSet<>(factionToDisband.getClaimedChunks()).forEach(cw -> {
            claimedChunks.remove(cw);
            // No need to call factionToDisband.removeClaim(cw) as we are dismantling it
        });

        // Remove from main map
        factionsByNameKey.remove(nameKey);

        // Remove all members from playerFactionMembership
        factionToDisband.getMembers().keySet().forEach(playerUUID -> {
            playerFactionMembership.remove(playerUUID);
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null && p.isOnline() && !isAdminAction) { // Don't message if admin action is silent or has its own message
                p.sendMessage(ChatColor.RED + "Your faction, " + factionToDisband.getName() + ", has been disbanded.");
            }
        });

        // Clear relations with other factions
        for (Faction otherFac : factionsByNameKey.values()) {
            otherFac.removeAlly(nameKey);
            otherFac.removeEnemy(nameKey);
            otherFac.getEnemyDeclareTimestamps().remove(nameKey);
        }

        // Clear pending invites and requests involving this faction
        pendingMemberInvites.remove(nameKey); // Invites sent by this faction
        pendingMemberInvites.values().forEach(map -> map.keySet().removeIf(uuid -> { // Invites sent TO members of this faction (less common to track this way but good cleanup)
            return factionToDisband.getMembers().containsKey(uuid);
        }));

        pendingAllyRequests.remove(nameKey); // Requests sent TO this faction
        pendingAllyRequests.values().forEach(map -> map.remove(nameKey)); // Requests sent BY this faction


        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.removeFactionClaimsFromMap(factionToDisband);
        }
        getLogger().info("Faction " + factionToDisband.getName() + " has been disbanded" + (isAdminAction ? " by an admin." : "."));
        saveFactionsData();
    }


    public Map<String, Faction> getFactionsByNameKey() {
        return Collections.unmodifiableMap(factionsByNameKey);
    }

    public Map<ChunkWrapper, String> getClaimedChunksMap() {
        return Collections.unmodifiableMap(claimedChunks);
    }

    @Nullable
    public String getFactionOwningChunk(Chunk chunk) {
        return claimedChunks.get(new ChunkWrapper(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public Map<UUID, TeleportRequest> getActiveTeleportRequests() {
        return activeTeleportRequests;
    }

    @Nullable
    public DynmapManager getDynmapManager() {
        return dynmapManager;
    }

    public boolean isPlayerInFactionChat(UUID playerUUID) {
        return playersInFactionChat.contains(playerUUID);
    }

    public boolean togglePlayerFactionChat(UUID playerUUID) {
        if (playersInFactionChat.contains(playerUUID)) {
            playersInFactionChat.remove(playerUUID);
            return false; // Now in public chat
        } else {
            playersInFactionChat.add(playerUUID);
            return true; // Now in faction chat
        }
    }
    public void setPlayerFactionChat(UUID playerUUID, boolean inFactionChat) {
        if (inFactionChat) {
            playersInFactionChat.add(playerUUID);
        } else {
            playersInFactionChat.remove(playerUUID);
        }
    }


    public void initiateTeleportWarmup(Player player, Location targetLocation, String successMessage) {
        UUID playerUUID = player.getUniqueId();
        if (activeTeleportRequests.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "You already have an active teleport request.");
            return;
        }
        if (TELEPORT_WARMUP_SECONDS <= 0) { // Instant teleport
            player.teleport(targetLocation);
            player.sendMessage(successMessage);
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Teleporting in " + TELEPORT_WARMUP_SECONDS + " seconds. Don't move or take damage!");
        int taskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            TeleportRequest completedRequest = activeTeleportRequests.remove(playerUUID);
            if (completedRequest != null) { // Check if it wasn't cancelled
                player.teleport(targetLocation);
                if (completedRequest.getTeleportMessage() != null && !completedRequest.getTeleportMessage().isEmpty()){
                    player.sendMessage(completedRequest.getTeleportMessage());
                } else {
                    player.sendMessage(ChatColor.GREEN + "Teleported!");
                }
            }
        }, TELEPORT_WARMUP_SECONDS * 20L).getTaskId();

        activeTeleportRequests.put(playerUUID, new TeleportRequest(player, targetLocation, taskId, successMessage));
    }

    public void cancelTeleport(UUID playerUUID, @Nullable String cancelMessage) {
        TeleportRequest request = activeTeleportRequests.remove(playerUUID);
        if (request != null) {
            Bukkit.getScheduler().cancelTask(request.getTaskId());
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline() && cancelMessage != null && !cancelMessage.isEmpty()) {
                player.sendMessage(cancelMessage);
            }
        }
    }

    public void notifyFaction(Faction faction, String message, @Nullable UUID excludePlayerUUID) {
        if (faction == null || message == null || message.isEmpty()) return;
        for (UUID memberUUID : faction.getMembers().keySet()) {
            if (excludePlayerUUID != null && memberUUID.equals(excludePlayerUUID)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }
}
