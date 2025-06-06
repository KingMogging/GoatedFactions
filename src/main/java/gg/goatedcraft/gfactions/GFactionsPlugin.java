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
import gg.goatedcraft.gfactions.listeners.PlayerLoginListener;
import gg.goatedcraft.gfactions.listeners.PlayerTabDisplayListener;
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
    private final Map<ChunkWrapper, String> claimedChunks = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> pendingMemberInvites = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> pendingAllyRequests = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportRequest> activeTeleportRequests = new ConcurrentHashMap<>();
    private final Set<UUID> playersInFactionChat = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersInAllyChat = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersWithAutoclaim = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> factionLastOnlineTime = new ConcurrentHashMap<>();

    private BukkitTask powerRegenTask;
    private BukkitTask powerDecayTask;
    private DynmapManager dynmapManager;
    private File factionsDataFile;
    private File factionActivityDataFile;


    // Configurable Settings (with defaults) - these fields are public for easy access from other classes
    public int POWER_INITIAL = 100;
    public int POWER_PER_MEMBER_BONUS = 100;
    public int POWER_REGEN_OFFLINE_PER_HOUR = 2;
    public int POWER_REGEN_ONLINE_PER_HOUR = 5;
    public long powerRegenIntervalTicks;
    public int POWER_LOSS_ON_DEATH_MEMBER = 24;
    public int POWER_LOSS_ON_DEATH_ADMIN_OWNER = 50;
    public boolean POWER_DECAY_ENABLED = true;
    public int POWER_DECAY_DAYS_INACTIVE = 30;
    public int POWER_DECAY_AMOUNT_PER_DAY = 5;
    public long POWER_DECAY_CHECK_INTERVAL_HOURS = 24;
    public long powerDecayCheckIntervalTicks;
    public int COST_CLAIM_CHUNK = 5;
    public int COST_OVERCLAIM_CHUNK = 20;
    public int COST_DECLARE_ENEMY = 10;
    public int COST_DECLARE_NEUTRAL = 10;
    public int COST_SEND_ALLY_REQUEST = 10;
    public int COST_PROMOTE_MEMBER = 2;
    public int COST_TRUST_PLAYER = 1;
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
    public int DYNMAP_COLOR_DEFAULT_CLAIM = 0x00FF00;
    public int DYNMAP_COLOR_ENEMY_CLAIM = 0xFF0000;
    public int DYNMAP_COLOR_ALLY_CLAIM = 0x00FFFF;
    public int DYNMAP_COLOR_NEUTRAL_CLAIM = 0xFFFF00;
    public int FACTION_NAME_MIN_LENGTH = 3;
    public int FACTION_NAME_MAX_LENGTH = 16;
    public Pattern FACTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    public boolean FACTION_CHAT_ENABLED = true;
    public boolean ALLY_CHAT_ENABLED = true;
    // UPDATED DEFAULT for FACTION_CHAT_FORMAT
    public String FACTION_CHAT_FORMAT = "&8[&2{RANK}&8] &f{PLAYER_NAME}: &e{MESSAGE}";
    public String ALLY_CHAT_FORMAT = "&8[&6ALLY&8][&a{FACTION_NAME}&8] &f{PLAYER_NAME}: &e{MESSAGE}";
    public String PUBLIC_CHAT_PREFIX_FORMAT = "&7[{FACTION_NAME}&7] &r"; // Original, for full name prefix
    public String PUBLIC_CHAT_TAG_FORMAT = "&8[{FACTION_TAG_COLOR}{FACTION_TAG}&8] "; // New, for short tag prefix
    public int FACTION_TAG_LENGTH = 4; // New config for tag length
    public boolean TRUSTED_PLAYERS_CAN_HEAR_FACTION_CHAT = false; // New

    public boolean ENEMY_SYSTEM_ENABLED = true; // Now directly here, was in faction_details
    public boolean PVP_PROTECTION_SYSTEM_ENABLED = false; // NEW: Master PvP system toggle
    public boolean PVP_IN_TERRITORY_PROTECTION_ENABLED_BY_DEFAULT = false; // New
    public boolean VAULT_SYSTEM_ENABLED = true;
    public boolean OUTPOST_SYSTEM_ENABLED = true;
    public int MAX_OUTPOSTS_PER_FACTION = 1;
    public int BASE_CLAIM_LIMIT = 25;
    public int CLAIMS_PER_MEMBER_BONUS = 10;
    public boolean PREVENT_CLAIM_NEAR_SPAWN = true;
    public int SPAWN_PROTECTION_RADIUS_CHUNKS = 3;
    public boolean ALLOW_NETHER_CLAIMING = false;
    public boolean ALLOW_END_CLAIMING = false;
    public boolean AUTOCLAIM_ENABLED = true;
    public boolean CLAIMFILL_ENABLED = true;
    public List<String> FACTION_GUIDE_MESSAGES = new ArrayList<>();
    public String MESSAGE_ENTERING_WILDERNESS = ChatColor.translateAlternateColorCodes('&', "&7Now entering Wilderness");
    public String MESSAGE_ENTERING_FACTION_TERRITORY = ChatColor.translateAlternateColorCodes('&', "&7Now entering {FACTION_RELATION_COLOR}{FACTION_NAME}");
    public Location SERVER_SPAWN_LOCATION;
    private static final int CLAIM_FILL_MAX_POCKET_SIZE = 100;
    private static final int CLAIM_FILL_BFS_LIMIT = 500;

    // NEW: Toggles for the /f who screen
    public boolean SHOW_WHO_OWNER = true;
    public boolean SHOW_WHO_POWER = true;
    public boolean SHOW_WHO_CLAIMS = true;
    public boolean SHOW_WHO_PVP_STATUS = true;
    public boolean SHOW_WHO_HOME_LOCATION = false;
    public boolean SHOW_WHO_LEADERSHIP_LIST = true;
    public boolean SHOW_WHO_MEMBERS_LIST = true;
    public boolean SHOW_WHO_ASSOCIATES_LIST = true;
    public boolean SHOW_WHO_ALLIES_LIST = true;
    public boolean SHOW_WHO_ENEMIES_LIST = true;
    public boolean SHOW_WHO_OUTPOSTS = true;


    @Override
    public void onEnable() {
        boolean enabledSuccessfully = false;
        getLogger().info(getName() + " is starting the enabling process...");
        try {
            if (!getDataFolder().exists()) {
                if(!getDataFolder().mkdirs()){
                    getLogger().severe("Could not create plugin data folder!");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
            }
            factionsDataFile = new File(getDataFolder(), "factions.yml");
            factionActivityDataFile = new File(getDataFolder(), "faction_activity.yml");


            saveDefaultConfig();
            getLogger().info("Loading plugin configuration...");
            loadPluginConfig();
            getLogger().info("Plugin configuration loaded.");


            getLogger().info("Determining server spawn chunk location...");
            if (Bukkit.getWorlds().isEmpty()) {
                getLogger().severe("No worlds loaded at onEnable! Cannot determine server spawn. Spawn protection might not work.");
                PREVENT_CLAIM_NEAR_SPAWN = false;
            } else {
                World defaultWorld = Bukkit.getWorlds().get(0);
                if (defaultWorld != null) {
                    Chunk spawnChunk = defaultWorld.getSpawnLocation().getChunk();
                    SERVER_SPAWN_LOCATION = new Location(defaultWorld, spawnChunk.getX() * 16 + 8, defaultWorld.getSpawnLocation().getY(), spawnChunk.getZ() * 16 + 8);
                    getLogger().info("Server spawn chunk identified at: " + spawnChunk.getX() + ", " + spawnChunk.getZ() + " in world " + defaultWorld.getName());
                } else {
                    getLogger().severe("Could not determine server spawn location (default world was null)! Spawn protection might not work correctly.");
                    PREVENT_CLAIM_NEAR_SPAWN = false;
                }
            }

            getLogger().info("Loading factions data...");
            loadFactionsData();
            loadFactionActivityData();
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
            getServer().getPluginManager().registerEvents(new PlayerLoginListener(this), this);
            getServer().getPluginManager().registerEvents(new PlayerTabDisplayListener(this), this);
            getLogger().info("Event listeners registered.");

            getLogger().info("Starting power regeneration task...");
            startPowerRegeneration();
            getLogger().info("Power regeneration task started/checked.");
            if (POWER_DECAY_ENABLED) {
                getLogger().info("Starting power decay task...");
                startPowerDecayTask();
                getLogger().info("Power decay task started/checked.");
            } else {
                getLogger().info("Power decay is disabled in config.");
            }


            if (DYNMAP_ENABLED) {
                getLogger().info("Initializing Dynmap integration (delayed)...");
                this.dynmapManager = new DynmapManager(this);
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        Plugin dynmapPluginHook = getServer().getPluginManager().getPlugin("dynmap");
                        if (dynmapPluginHook != null && dynmapPluginHook.isEnabled()) {
                            getLogger().info("Attempting to activate Dynmap integration for " + getName() + "...");
                            dynmapManager.activate();
                        } else {
                            getLogger().info("Dynmap plugin not found or not enabled for delayed activation. Integration will remain disabled.");
                        }
                    } catch (Exception e_dynmap) {
                        getLogger().log(Level.SEVERE, "Error during delayed Dynmap activation for " + getName(),e_dynmap);
                    }
                }, 20L);
            } else {
                getLogger().info("Dynmap integration is disabled in GFactions configuration.");
            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                updatePlayerTabListName(onlinePlayer);
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
        if (powerDecayTask != null && !powerDecayTask.isCancelled()) {
            powerDecayTask.cancel();
            powerDecayTask = null;
        }
        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.deactivate();
        }

        activeTeleportRequests.values().forEach(request -> Bukkit.getScheduler().cancelTask(request.getTaskId()));
        activeTeleportRequests.clear();
        playersInFactionChat.clear();
        playersInAllyChat.clear();
        playersWithAutoclaim.clear();
        getLogger().info("Attempting to save factions data during disable...");
        saveFactionsData();
        saveFactionActivityData();
        getLogger().info(getName() + " has been disabled.");
    }

    public void loadPluginConfig() {
        FileConfiguration config = getConfig();
        POWER_INITIAL = config.getInt("power.initial", 100);
        POWER_PER_MEMBER_BONUS = config.getInt("power.per_member_bonus", 100);

        POWER_REGEN_OFFLINE_PER_HOUR = config.getInt("power.regeneration.offline_amount_per_hour", 2);
        POWER_REGEN_ONLINE_PER_HOUR = config.getInt("power.regeneration.online_amount_per_hour", 5);
        powerRegenIntervalTicks = 20L * 60 * 5; // 5 minutes in ticks

        POWER_LOSS_ON_DEATH_MEMBER = config.getInt("power.loss_on_death.member", 24);
        POWER_LOSS_ON_DEATH_ADMIN_OWNER = config.getInt("power.loss_on_death.admin_owner", 50);

        POWER_DECAY_ENABLED = config.getBoolean("power.decay.enabled", true);
        POWER_DECAY_DAYS_INACTIVE = config.getInt("power.decay.days_inactive_before_decay_starts", 30);
        POWER_DECAY_AMOUNT_PER_DAY = config.getInt("power.decay.decay_amount_per_day", 5);
        POWER_DECAY_CHECK_INTERVAL_HOURS = config.getLong("power.decay.check_interval_hours", 24);
        powerDecayCheckIntervalTicks = POWER_DECAY_CHECK_INTERVAL_HOURS * 60 * 60 * 20;


        COST_CLAIM_CHUNK = config.getInt("power.cost.claim_chunk", 5);
        COST_OVERCLAIM_CHUNK = config.getInt("power.cost.overclaim_chunk", 20);
        COST_DECLARE_ENEMY = config.getInt("power.cost.declare_enemy", 10);
        COST_DECLARE_NEUTRAL = config.getInt("power.cost.declare_neutral", 10);
        COST_SEND_ALLY_REQUEST = config.getInt("power.cost.send_ally_request", 10);
        COST_PROMOTE_MEMBER = config.getInt("power.cost.promote_member", 0);
        COST_TRUST_PLAYER = config.getInt("power.cost.trust_player", 0);
        COST_CREATE_OUTPOST = config.getInt("power.cost.create_outpost", 50);


        COOLDOWN_ENEMY_NEUTRAL_HOURS = config.getLong("cooldowns.enemy_neutral_declaration_hours", 24);
        EXPIRATION_MEMBER_INVITE_MINUTES = config.getLong("cooldowns.member_invite_expiration_minutes", 5);
        EXPIRATION_ALLY_REQUEST_MINUTES = config.getLong("cooldowns.ally_request_expiration_minutes", 5);
        TELEPORT_WARMUP_SECONDS = config.getLong("teleport.warmup_seconds", 5);

        TITLE_FADE_IN_TICKS = config.getInt("titles.fade_in_ticks", 10);
        TITLE_STAY_TICKS = config.getInt("titles.stay_ticks", 40);
        TITLE_FADE_OUT_TICKS = config.getInt("titles.fade_out_ticks", 10);
        TITLE_DISPLAY_COOLDOWN_SECONDS = config.getLong("titles.display_cooldown_seconds", 1);
        DYNMAP_ENABLED = config.getBoolean("dynmap.enabled", false);
        DYNMAP_MARKERSET_LABEL = config.getString("dynmap.markerset_label", "Faction Claims");
        DYNMAP_STROKE_WEIGHT = config.getInt("dynmap.style.stroke_weight", 2);
        DYNMAP_STROKE_OPACITY = config.getDouble("dynmap.style.stroke_opacity", 0.80);
        DYNMAP_STROKE_COLOR = config.getString("dynmap.style.stroke_color", "0x000000");
        DYNMAP_FILL_OPACITY = config.getDouble("dynmap.style.fill_opacity", 0.35);

        try {
            DYNMAP_COLOR_DEFAULT_CLAIM = Integer.decode(config.getString("dynmap.style.default_claim_color", "0x00FF00"));
            DYNMAP_COLOR_ENEMY_CLAIM = Integer.decode(config.getString("dynmap.style.enemy_claim_color", "0xFF0000"));
            DYNMAP_COLOR_ALLY_CLAIM = Integer.decode(config.getString("dynmap.style.ally_claim_color", "0x00FFFF"));
            DYNMAP_COLOR_NEUTRAL_CLAIM = Integer.decode(config.getString("dynmap.style.neutral_claim_color", "0xFFFF00"));
        } catch (NumberFormatException e) {
            getLogger().warning("Invalid hex color format in Dynmap style settings. Using internal defaults.");
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

        FACTION_CHAT_ENABLED = config.getBoolean("faction_details.chat.faction_chat_enabled", true);
        ALLY_CHAT_ENABLED = config.getBoolean("faction_details.chat.ally_chat_enabled", true);
        // UPDATED DEFAULT for FACTION_CHAT_FORMAT
        FACTION_CHAT_FORMAT = ChatColor.translateAlternateColorCodes('&', config.getString("faction_details.chat.faction_chat_format", "&8[&2{RANK}&8] &f{PLAYER_NAME}: &e{MESSAGE}"));
        ALLY_CHAT_FORMAT = ChatColor.translateAlternateColorCodes('&', config.getString("faction_details.chat.ally_chat_format", "&8[&6ALLY&8][&a{FACTION_NAME}&8] &f{PLAYER_NAME}: &e{MESSAGE}"));
        PUBLIC_CHAT_PREFIX_FORMAT = ChatColor.translateAlternateColorCodes('&', config.getString("faction_details.chat.public_chat_prefix_format", "&7[{FACTION_NAME}&7] &r"));
        // Kept for potential other uses
        PUBLIC_CHAT_TAG_FORMAT = ChatColor.translateAlternateColorCodes('&', config.getString("faction_details.chat.public_chat_tag_format", "&8[{FACTION_TAG_COLOR}{FACTION_TAG}&8] "));
        FACTION_TAG_LENGTH = config.getInt("faction_details.chat.faction_tag_length", 4);
        TRUSTED_PLAYERS_CAN_HEAR_FACTION_CHAT = config.getBoolean("faction_details.chat.trusted_hear_faction_chat", false);


        ENEMY_SYSTEM_ENABLED = config.getBoolean("faction_details.enemy_system_enabled", true);
        // If enemy system is disabled, ally system related features are also disabled.
        if (!ENEMY_SYSTEM_ENABLED) {
            ALLY_CHAT_ENABLED = false; // Disable ally chat if enemy system is off
        }

        PVP_PROTECTION_SYSTEM_ENABLED = config.getBoolean("faction_details.pvp_protection_system_enabled", false);
        PVP_IN_TERRITORY_PROTECTION_ENABLED_BY_DEFAULT = config.getBoolean("claiming.pvp_protection_enabled_by_default", false);
        VAULT_SYSTEM_ENABLED = config.getBoolean("faction_details.vault_system_enabled", true);
        OUTPOST_SYSTEM_ENABLED = config.getBoolean("faction_details.outpost_system_enabled", true);
        MAX_OUTPOSTS_PER_FACTION = config.getInt("faction_details.max_outposts", 1);


        BASE_CLAIM_LIMIT = config.getInt("claiming.base_claim_limit", 25);
        CLAIMS_PER_MEMBER_BONUS = config.getInt("claiming.claims_per_member_bonus", 10);
        PREVENT_CLAIM_NEAR_SPAWN = config.getBoolean("claiming.prevent_claim_near_spawn", true);
        SPAWN_PROTECTION_RADIUS_CHUNKS = config.getInt("claiming.spawn_protection_radius_chunks", 3);
        ALLOW_NETHER_CLAIMING = config.getBoolean("claiming.allow_nether_claiming", false);
        ALLOW_END_CLAIMING = config.getBoolean("claiming.allow_end_claiming", false);
        AUTOCLAIM_ENABLED = config.getBoolean("claiming.autoclaim_enabled", true);
        CLAIMFILL_ENABLED = config.getBoolean("claiming.claimfill_enabled", true);


        MESSAGE_ENTERING_WILDERNESS = ChatColor.translateAlternateColorCodes('&', config.getString("messages.entering_wilderness", "&7Now entering Wilderness"));
        MESSAGE_ENTERING_FACTION_TERRITORY = ChatColor.translateAlternateColorCodes('&', config.getString("messages.entering_faction_territory", "&7Now entering {FACTION_RELATION_COLOR}{FACTION_NAME}"));
        FACTION_GUIDE_MESSAGES = config.getStringList("messages.faction_guide").stream()
                .map(s -> ChatColor.translateAlternateColorCodes('&', s))
                .collect(Collectors.toList());
        if (FACTION_GUIDE_MESSAGES.isEmpty()) {
            FACTION_GUIDE_MESSAGES.add(ChatColor.GOLD + "--- Faction Guide ---");
            FACTION_GUIDE_MESSAGES.add(ChatColor.YELLOW + "Creating a Faction: " + ChatColor.WHITE + "Use /f create <name>");
            FACTION_GUIDE_MESSAGES.add(ChatColor.YELLOW + "Power: " + ChatColor.WHITE + "Power is crucial for claiming and maintaining land.");
            FACTION_GUIDE_MESSAGES.add(ChatColor.WHITE + " - Factions gain max power per member.");
            FACTION_GUIDE_MESSAGES.add(ChatColor.WHITE + " - Power regenerates over time, faster if members are online.");
            FACTION_GUIDE_MESSAGES.add(ChatColor.WHITE + " - Losing all power makes your claims vulnerable!");
            FACTION_GUIDE_MESSAGES.add(ChatColor.WHITE + " - Dying to enemies can cause power loss.");
            FACTION_GUIDE_MESSAGES.add(ChatColor.YELLOW + "Claims: " + ChatColor.WHITE + "Use /f claim to expand your territory.");
            FACTION_GUIDE_MESSAGES.add(ChatColor.YELLOW + "More help: " + ChatColor.WHITE + "Use /f help for a full command list.");
        }

        // Load /f who screen toggles
        SHOW_WHO_OWNER = config.getBoolean("faction_details.who_screen_display.show_owner", true);
        SHOW_WHO_POWER = config.getBoolean("faction_details.who_screen_display.show_power", true);
        SHOW_WHO_CLAIMS = config.getBoolean("faction_details.who_screen_display.show_claims", true);
        SHOW_WHO_PVP_STATUS = config.getBoolean("faction_details.who_screen_display.show_pvp_status", true);
        SHOW_WHO_HOME_LOCATION = config.getBoolean("faction_details.who_screen_display.show_home_location", false);
        SHOW_WHO_LEADERSHIP_LIST = config.getBoolean("faction_details.who_screen_display.show_leadership_list", true);
        SHOW_WHO_MEMBERS_LIST = config.getBoolean("faction_details.who_screen_display.show_members_list", true);
        SHOW_WHO_ASSOCIATES_LIST = config.getBoolean("faction_details.who_screen_display.show_associates_list", true);
        SHOW_WHO_ALLIES_LIST = config.getBoolean("faction_details.who_screen_display.show_allies_list", true);
        SHOW_WHO_ENEMIES_LIST = config.getBoolean("faction_details.who_screen_display.show_enemies_list", true);
        SHOW_WHO_OUTPOSTS = config.getBoolean("faction_details.who_screen_display.show_outposts", true);

        getLogger().info("GFactions configuration values have been processed.");
    }


    public void startPowerRegeneration() {
        if (powerRegenTask != null && !powerRegenTask.isCancelled()) {
            powerRegenTask.cancel();
        }
        final double onlineRegenPerInterval = (double) POWER_REGEN_ONLINE_PER_HOUR / 12.0;
        final double offlineRegenPerInterval = (double) POWER_REGEN_OFFLINE_PER_HOUR / 12.0;

        if (onlineRegenPerInterval <= 0 && offlineRegenPerInterval <= 0) {
            getLogger().info("Power regeneration is disabled as both online and offline rates are zero or less.");
            return;
        }

        powerRegenTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Faction faction : factionsByNameKey.values()) {
                if (faction.getCurrentPower() < faction.getMaxPowerCalculated(this)) {
                    double regenAmountThisTick;
                    if (isAnyMemberOnline(faction)) {
                        regenAmountThisTick = onlineRegenPerInterval;
                    } else {
                        regenAmountThisTick = offlineRegenPerInterval;
                    }
                    faction.addFractionalPower(regenAmountThisTick);
                    int wholePowerToAdd = (int) faction.getAccumulatedFractionalPower();
                    if (wholePowerToAdd > 0) {
                        faction.addPower(wholePowerToAdd);
                        faction.resetFractionalPower(faction.getAccumulatedFractionalPower() - wholePowerToAdd);
                    }
                }
            }
        }, powerRegenIntervalTicks, powerRegenIntervalTicks);
        getLogger().info("Power regeneration task scheduled to run every " + (powerRegenIntervalTicks / 20.0) + " seconds.");
    }

    public void startPowerDecayTask() {
        if (powerDecayTask != null && !powerDecayTask.isCancelled()) {
            powerDecayTask.cancel();
        }
        if (!POWER_DECAY_ENABLED || POWER_DECAY_AMOUNT_PER_DAY <= 0 || POWER_DECAY_DAYS_INACTIVE <= 0 || powerDecayCheckIntervalTicks <=0) {
            getLogger().info("Power decay is disabled or configured with invalid values.");
            return;
        }

        powerDecayTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            long currentTime = System.currentTimeMillis();
            long inactivityThresholdMillis = TimeUnit.DAYS.toMillis(POWER_DECAY_DAYS_INACTIVE);

            for (Faction faction : factionsByNameKey.values()) {
                long lastOnline = factionLastOnlineTime.getOrDefault(faction.getNameKey(), 0L);
                if (lastOnline == 0L) {
                    updateFactionActivity(faction.getNameKey());
                    continue;
                }

                if ((currentTime - lastOnline) > inactivityThresholdMillis) {
                    int oldPower = faction.getCurrentPower();
                    faction.removePower(POWER_DECAY_AMOUNT_PER_DAY);
                    if (faction.getCurrentPower() < oldPower) {
                        getLogger().info("Faction " + faction.getName() + " decayed " + POWER_DECAY_AMOUNT_PER_DAY + " power due to inactivity. New power: " + faction.getCurrentPower());
                        faction.broadcastMessage(ChatColor.RED + "Your faction has lost " + POWER_DECAY_AMOUNT_PER_DAY + " power due to inactivity!", null);
                        if (dynmapManager != null && dynmapManager.isEnabled()) {
                            dynmapManager.updateFactionAppearance(faction);
                        }
                    }
                }
            }
        }, powerDecayCheckIntervalTicks, powerDecayCheckIntervalTicks);
        getLogger().info("Power decay task scheduled to run every " + POWER_DECAY_CHECK_INTERVAL_HOURS + " hours.");
    }

    public void updateFactionActivity(String factionNameKey) {
        if (factionNameKey == null) return;
        factionLastOnlineTime.put(factionNameKey.toLowerCase(), System.currentTimeMillis());
    }


    @Nullable
    public Faction createFactionAndReturn(Player owner, String name) {
        String nameKey = name.toLowerCase();
        if (factionsByNameKey.containsKey(nameKey)) {
            owner.sendMessage(ChatColor.RED + "A faction with that name already exists.");
            return null;
        }
        if (playerFactionMembership.containsKey(owner.getUniqueId())) {
            owner.sendMessage(ChatColor.RED + "You are already in a faction.");
            return null;
        }

        Chunk initialChunk = owner.getLocation().getChunk();
        World.Environment environment = initialChunk.getWorld().getEnvironment();
        if (environment == World.Environment.NETHER && !ALLOW_NETHER_CLAIMING) {
            owner.sendMessage(ChatColor.RED + "Claiming land in The Nether is disabled on this server.");
            return null;
        }
        if (environment == World.Environment.THE_END && !ALLOW_END_CLAIMING) {
            owner.sendMessage(ChatColor.RED + "Claiming land in The End is disabled on this server.");
            return null;
        }

        ChunkWrapper initialChunkWrapper = new ChunkWrapper(initialChunk.getWorld().getName(), initialChunk.getX(), initialChunk.getZ());
        if (claimedChunks.containsKey(initialChunkWrapper)) {
            owner.sendMessage(ChatColor.RED + "This exact chunk is already claimed. Try moving slightly.");
            return null;
        }

        if (PREVENT_CLAIM_NEAR_SPAWN && SERVER_SPAWN_LOCATION != null) {
            Chunk spawnServerChunk = SERVER_SPAWN_LOCATION.getChunk();
            if (Objects.equals(initialChunk.getWorld().getName(), spawnServerChunk.getWorld().getName())) {
                int distChunksX = Math.abs(initialChunk.getX() - spawnServerChunk.getX());
                int distChunksZ = Math.abs(initialChunk.getZ() - spawnServerChunk.getZ());
                if (Math.max(distChunksX, distChunksZ) <= SPAWN_PROTECTION_RADIUS_CHUNKS) {
                    if (!owner.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                        owner.sendMessage(ChatColor.RED + "You cannot create a faction this close (within " + SPAWN_PROTECTION_RADIUS_CHUNKS + " chunks) to server spawn.");
                        return null;
                    }
                }
            }
        }

        Faction faction = new Faction(name, owner.getUniqueId(), owner.getLocation(), this);
        updateFactionActivity(faction.getNameKey());

        claimedChunks.put(initialChunkWrapper, nameKey);
        factionsByNameKey.put(nameKey, faction);
        playerFactionMembership.put(owner.getUniqueId(), nameKey);
        updatePlayerTabListName(owner);
        getLogger().info("Faction '" + name + "' created by " + owner.getName() + ". Home chunk: " + initialChunkWrapper.toStringShort());
        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionClaimsVisual(faction);
        }
        saveFactionsData();
        saveFactionActivityData();
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

        faction.setOwnerUUID(newOwnerOfflinePlayer.getUniqueId());
        updateFactionActivity(faction.getNameKey());
        updatePlayerTabListName(currentOwner);
        if (newOwnerOfflinePlayer.isOnline()) {
            updatePlayerTabListName((Player) newOwnerOfflinePlayer);
        }
        saveFactionsData();
        return true;
    }

    public boolean sendMemberInvite(Faction invitingFaction, UUID invitedPlayerUUID) {
        if (getFactionByPlayer(invitedPlayerUUID) != null) return false;
        if (invitingFaction.getMembers().containsKey(invitedPlayerUUID)) return false;

        Map<UUID, Long> factionInvites = pendingMemberInvites.getOrDefault(invitingFaction.getNameKey(), new HashMap<>());
        if (factionInvites.containsKey(invitedPlayerUUID) && factionInvites.get(invitedPlayerUUID) > System.currentTimeMillis()) {
            return false;
        }

        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(EXPIRATION_MEMBER_INVITE_MINUTES);
        pendingMemberInvites.computeIfAbsent(invitingFaction.getNameKey(), k -> new ConcurrentHashMap<>()).put(invitedPlayerUUID, expiryTime);
        saveFactionsData();
        return true;
    }
    public void revokeMemberInvite(Faction invitingFaction, OfflinePlayer invitedOfflinePlayer) {
        Map<UUID, Long> factionInvites = pendingMemberInvites.get(invitingFaction.getNameKey());
        if (factionInvites != null) {
            if (factionInvites.remove(invitedOfflinePlayer.getUniqueId()) != null) {
                saveFactionsData();
            }
        }
    }


    public boolean acceptMemberInvite(UUID playerUUID, String factionNameKeyToJoin) {
        Faction targetFaction = factionsByNameKey.get(factionNameKeyToJoin.toLowerCase());
        if (targetFaction == null) return false;

        Map<UUID, Long> invitesForFaction = pendingMemberInvites.get(targetFaction.getNameKey());
        if (invitesForFaction == null || !invitesForFaction.containsKey(playerUUID) || invitesForFaction.get(playerUUID) < System.currentTimeMillis()) {
            return false;
        }
        if (getFactionByPlayer(playerUUID) != null) return false;
        if (targetFaction.addMember(playerUUID, FactionRank.ASSOCIATE)) { // New members join as ASSOCIATE by default
            playerFactionMembership.put(playerUUID, targetFaction.getNameKey());
            invitesForFaction.remove(playerUUID);
            updateFactionActivity(targetFaction.getNameKey());
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) updatePlayerTabListName(player);
            saveFactionsData();
            return true;
        }
        return false;
    }

    public Map<UUID, Long> getPendingMemberInvitesForFaction(String inviterFactionKey) {
        Map<UUID, Long> invites = pendingMemberInvites.get(inviterFactionKey.toLowerCase());
        if (invites != null) {
            long currentTime = System.currentTimeMillis();
            invites.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            if(invites.isEmpty()) pendingMemberInvites.remove(inviterFactionKey.toLowerCase());
            return Collections.unmodifiableMap(new HashMap<>(invites));
        }
        return Collections.emptyMap();
    }


    public boolean removePlayerFromFaction(Faction faction, UUID playerUUID, boolean isKick) {
        if (!faction.getMembers().containsKey(playerUUID)) return false;
        if (faction.isOwner(playerUUID)) {
            if (faction.getMembers().size() == 1) {
                disbandFactionInternal(faction, false);
                return true;
            } else if (!isKick) {
                Player ownerPlayer = Bukkit.getPlayer(playerUUID);
                if (ownerPlayer != null) {
                    ownerPlayer.sendMessage(ChatColor.RED + "You must transfer ownership or disband the faction.");
                }
                return false;
            }
        }

        if (faction.removeMember(playerUUID)) {
            playerFactionMembership.remove(playerUUID);
            faction.removeTrusted(playerUUID);
            updateFactionActivity(faction.getNameKey());
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) updatePlayerTabListName(player);
            saveFactionsData();
            return true;
        }
        return false;
    }

    public boolean claimChunk(Faction faction, Chunk chunk, boolean isOverclaim, boolean isOutpostCreation, @Nullable Outpost attachingToOutpost, @Nullable Player actor) {
        Player player = actor;
        if (player == null) {
            Optional<Player> onlineMember = faction.getMemberUUIDsOnly().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .filter(Player::isOnline)
                    .findFirst();
            if (onlineMember.isPresent()) {
                player = onlineMember.get();
            }
        }

        ChunkWrapper cw = new ChunkWrapper(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        World.Environment environment = chunk.getWorld().getEnvironment();
        String currentOwnerKey = claimedChunks.get(cw);
        Faction currentOwnerFaction = (currentOwnerKey != null) ? getFaction(currentOwnerKey) : null;
        if (environment == World.Environment.NETHER && !ALLOW_NETHER_CLAIMING) {
            if (player != null) player.sendMessage(ChatColor.RED + "Claiming land in The Nether is disabled.");
            return false;
        }
        if (environment == World.Environment.THE_END && !ALLOW_END_CLAIMING) {
            if (player != null) player.sendMessage(ChatColor.RED + "Claiming land in The End is disabled.");
            return false;
        }

        if (PREVENT_CLAIM_NEAR_SPAWN && SERVER_SPAWN_LOCATION != null) {
            Chunk spawnServerChunk = SERVER_SPAWN_LOCATION.getChunk();
            if (Objects.equals(chunk.getWorld().getName(), spawnServerChunk.getWorld().getName())) {
                int distChunksX = Math.abs(chunk.getX() - spawnServerChunk.getX());
                int distChunksZ = Math.abs(chunk.getZ() - spawnServerChunk.getZ());
                if (Math.max(distChunksX, distChunksZ) <= SPAWN_PROTECTION_RADIUS_CHUNKS) {
                    if (player == null || !player.hasPermission("goatedfactions.admin.bypassspawnprotection")) {
                        if (player != null) player.sendMessage(ChatColor.RED + "You cannot claim land this close (within " + SPAWN_PROTECTION_RADIUS_CHUNKS + " chunks) to server spawn.");
                        return false;
                    }
                }
            }
        }

        int maxClaims = faction.getMaxClaimsCalculated(this);
        if (faction.getClaimLimitOverride() != -1) { // Check for override
            maxClaims = faction.getClaimLimitOverride();
        }

        if (!isOutpostCreation && maxClaims != Integer.MAX_VALUE && faction.getClaimedChunks().size() >= maxClaims) {
            if (player != null) player.sendMessage(ChatColor.RED + "Your faction has reached its claim limit (" + maxClaims + ").");
            return false;
        }

        if (!isOutpostCreation && !faction.getClaimedChunks().isEmpty() && !faction.isChunkAdjacentToExistingClaim(cw)) {
            if (attachingToOutpost == null || !faction.isChunkAdjacentTo(cw, attachingToOutpost.getOutpostSpecificClaims())) {
                if (player != null) player.sendMessage(ChatColor.RED + "You must claim land adjacent to your existing territory or an outpost's territory.");
                return false;
            }
        }


        if (currentOwnerFaction != null) {
            if (currentOwnerFaction.equals(faction)) {
                if (player != null) player.sendMessage(ChatColor.YELLOW + "Your faction already owns this land.");
                return false;
            }

            if (!isOverclaim) {
                if (player != null) player.sendMessage(ChatColor.RED + "This land is claimed by " + currentOwnerFaction.getName() + ".");
                return false;
            }
            // Overclaim attempt
            if (!ENEMY_SYSTEM_ENABLED) {
                if (player != null) player.sendMessage(ChatColor.RED + "Overclaiming is disabled because the enemy system is off.");
                return false;
            }
            if (currentOwnerFaction.getCurrentPower() > 0) {
                if (player != null) player.sendMessage(ChatColor.RED + currentOwnerFaction.getName() + " still has power ("+currentOwnerFaction.getCurrentPower()+") and cannot be overclaimed.");
                return false;
            }
            if (faction.getCurrentPower() < COST_OVERCLAIM_CHUNK) {
                if (player != null) player.sendMessage(ChatColor.RED + "Not enough power to overclaim. Cost: " + COST_OVERCLAIM_CHUNK);
                return false;
            }
            faction.removePower(COST_OVERCLAIM_CHUNK);
            currentOwnerFaction.removeClaim(cw);
            claimedChunks.remove(cw);
            getLogger().info("Faction " + faction.getName() + " overclaimed chunk " + cw.toStringShort() + " from " + currentOwnerFaction.getName());
            notifyFaction(currentOwnerFaction, ChatColor.DARK_RED + "Your land at (" + cw.getX() + "," + cw.getZ() + ") in " + cw.getWorldName() + " has been overclaimed by " + faction.getName() + "!", null);
        } else { // Land is wilderness
            if (faction.getCurrentPower() < COST_CLAIM_CHUNK && !isOutpostCreation) {
                if (player != null) player.sendMessage(ChatColor.RED + "Not enough power to claim. Cost: " + COST_CLAIM_CHUNK);
                return false;
            }
            if(!isOutpostCreation) faction.removePower(COST_CLAIM_CHUNK);
        }

        if (isOutpostCreation && attachingToOutpost != null) {
            faction.addClaim(cw, true, attachingToOutpost);
        } else {
            faction.addClaim(cw);
        }
        claimedChunks.put(cw, faction.getNameKey());
        if(player != null) player.sendMessage(ChatColor.GREEN + "Land claimed! " + ChatColor.GRAY + "(" + cw.getX() + ", " + cw.getZ() + " in " + cw.getWorldName() + ")");
        updateFactionActivity(faction.getNameKey());


        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.updateFactionClaimsVisual(faction);
            if (currentOwnerFaction != null) {
                dynmapManager.updateFactionClaimsVisual(currentOwnerFaction);
            }
        }
        saveFactionsData();
        return true;
    }

    public void unclaimChunkPlayer(Faction faction, ChunkWrapper chunkWrapper, @Nullable Player actor) {
        Player player = actor;
        if (player == null) {
            Optional<Player> onlineMember = faction.getMemberUUIDsOnly().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .filter(Player::isOnline)
                    .findFirst();
            if (onlineMember.isPresent()) {
                player = onlineMember.get();
            }
        }


        if (!faction.getClaimedChunks().contains(chunkWrapper)) {
            if (player != null) player.sendMessage(ChatColor.RED + "Your faction does not own this land.");
            return;
        }

        if (chunkWrapper.equals(faction.getHomeChunk())) {
            Set<ChunkWrapper> mainTerritory = new HashSet<>(faction.getClaimedChunks());
            faction.getOutposts().forEach(op -> mainTerritory.removeAll(op.getOutpostSpecificClaims()));
            if (mainTerritory.size() <= 1) {
                if (player != null) {
                    player.sendMessage(ChatColor.RED + "You cannot unclaim your faction's only main territory chunk that also contains your home. Set home elsewhere or unclaim other chunks first.");
                }
                return;
            }
        }

        Outpost outpostOwningChunk = null;
        for(Outpost op : faction.getOutposts()){
            if(op.getOutpostSpecificClaims().contains(chunkWrapper)){
                outpostOwningChunk = op;
                break;
            }
        }
        if (outpostOwningChunk != null && outpostOwningChunk.getOutpostSpecificClaims().size() <=1 && outpostOwningChunk.getOutpostSpecificClaims().contains(chunkWrapper)) {
            if (player != null) {
                player.sendMessage(ChatColor.RED + "You cannot unclaim the last chunk of Outpost #" + outpostOwningChunk.getOutpostID() + ". Delete the outpost instead using /f outpost delete " + outpostOwningChunk.getOutpostID());
            }
            return;
        }


        faction.removeClaim(chunkWrapper);
        claimedChunks.remove(chunkWrapper);
        if(player != null) player.sendMessage(ChatColor.YELLOW + "Land unclaimed: " + chunkWrapper.toStringShort() + " in " + chunkWrapper.getWorldName());
        updateFactionActivity(faction.getNameKey());
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
                faction.removeClaim(chunkWrapper);
                updateFactionActivity(faction.getNameKey());
                if (dynmapManager != null && dynmapManager.isEnabled()) {
                    dynmapManager.updateFactionClaimsVisual(faction);
                }
                getLogger().info("Admin unclaimed chunk " + chunkWrapper.toStringShort() + " from " + faction.getName());
            }
        }
        saveFactionsData();
    }


    public boolean sendAllyRequest(Faction requesterFaction, Faction targetFaction) {
        if (!ALLY_CHAT_ENABLED || !ENEMY_SYSTEM_ENABLED) { // Also check ENEMY_SYSTEM_ENABLED
            Player p = Bukkit.getPlayer(requesterFaction.getOwnerUUID());
            if(p != null) p.sendMessage(ChatColor.RED + "The alliance system is currently disabled.");
            return false;
        }

        Player p = Bukkit.getPlayer(requesterFaction.getOwnerUUID());
        if (requesterFaction.getCurrentPower() < COST_SEND_ALLY_REQUEST) {
            if (p!=null) p.sendMessage(ChatColor.RED + "Not enough power to send ally request. Cost: " + COST_SEND_ALLY_REQUEST);
            return false;
        }

        Map<String, Long> targetRequests = pendingAllyRequests.getOrDefault(targetFaction.getNameKey(), new HashMap<>());
        if (targetRequests.containsKey(requesterFaction.getNameKey()) && targetRequests.get(requesterFaction.getNameKey()) > System.currentTimeMillis()) {
            if (p!=null) p.sendMessage(ChatColor.YELLOW + "You already have a pending ally request to " + targetFaction.getName() + ".");
            return false;
        }
        Map<String, Long> requesterRequests = pendingAllyRequests.getOrDefault(requesterFaction.getNameKey(), new HashMap<>());
        if (requesterRequests.containsKey(targetFaction.getNameKey()) && requesterRequests.get(targetFaction.getNameKey()) > System.currentTimeMillis()) {
            if (p!=null) p.sendMessage(ChatColor.YELLOW + targetFaction.getName() + " has already sent you an ally request. Use /f allyaccept " + targetFaction.getName());
            return false;
        }


        requesterFaction.removePower(COST_SEND_ALLY_REQUEST);
        long expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(EXPIRATION_ALLY_REQUEST_MINUTES);
        pendingAllyRequests.computeIfAbsent(targetFaction.getNameKey(), k -> new ConcurrentHashMap<>()).put(requesterFaction.getNameKey(), expiryTime);
        updateFactionActivity(requesterFaction.getNameKey());
        saveFactionsData();
        return true;
    }

    public boolean acceptAllyRequest(Faction acceptingFaction, Faction requestingFaction) {
        if (!ALLY_CHAT_ENABLED || !ENEMY_SYSTEM_ENABLED) return false;
        // Also check ENEMY_SYSTEM_ENABLED
        Map<String, Long> requestsForAccepting = pendingAllyRequests.get(acceptingFaction.getNameKey());
        if (requestsForAccepting == null || !requestsForAccepting.containsKey(requestingFaction.getNameKey()) || requestsForAccepting.get(requestingFaction.getNameKey()) < System.currentTimeMillis()) {
            return false;
        }

        acceptingFaction.addAlly(requestingFaction.getNameKey());
        requestingFaction.addAlly(acceptingFaction.getNameKey());
        requestsForAccepting.remove(requestingFaction.getNameKey());

        Map<String, Long> requestsForRequesting = pendingAllyRequests.get(requestingFaction.getNameKey());
        if (requestsForRequesting != null) {
            requestsForRequesting.remove(acceptingFaction.getNameKey());
        }
        updateFactionActivity(acceptingFaction.getNameKey());
        updateFactionActivity(requestingFaction.getNameKey());
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
        if (!ALLY_CHAT_ENABLED || !ENEMY_SYSTEM_ENABLED) return;
        // Also check ENEMY_SYSTEM_ENABLED
        boolean changed1 = faction1.removeAlly(faction2.getNameKey());
        boolean changed2 = faction2.removeAlly(faction1.getNameKey());
        if (changed1 || changed2) {
            updateFactionActivity(faction1.getNameKey());
            updateFactionActivity(faction2.getNameKey());
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

        ConfigurationSection factionsSection = dataConfig.getConfigurationSection("factions");
        if (factionsSection != null) {
            for (String nameKey : factionsSection.getKeys(false)) {
                ConfigurationSection facSec = factionsSection.getConfigurationSection(nameKey);
                if (facSec == null) continue;

                String originalName = facSec.getString("originalName");
                UUID ownerUUID = null;
                String ownerUUIDString = facSec.getString("ownerUUID");
                if (ownerUUIDString == null) {
                    getLogger().severe("ownerUUID is null for faction " + nameKey + ". Skipping faction.");
                    continue;
                }
                try {
                    ownerUUID = UUID.fromString(ownerUUIDString);
                } catch (IllegalArgumentException e) {
                    getLogger().severe("Invalid ownerUUID for faction " + nameKey + ". Skipping faction.");
                    continue;
                }
                if (originalName == null) {
                    getLogger().severe("originalName is null for faction " + nameKey + " (Owner: " + ownerUUID + "). Attempting to use key as name, but this is not ideal.");
                    originalName = nameKey;
                }


                Location homeLoc = null;
                if (facSec.isSet("homeLocation")) {
                    try {
                        homeLoc = facSec.getLocation("homeLocation");
                        if (homeLoc != null && homeLoc.getWorld() == null && facSec.isSet("homeLocation.world")) {
                            String worldName = facSec.getString("homeLocation.world");
                            if (worldName != null) {
                                World world = Bukkit.getWorld(worldName);
                                if(world != null) homeLoc.setWorld(world);
                                else getLogger().warning("World '" + worldName + "' for home location of faction " + nameKey + " not loaded. Home might be invalid until set again.");
                            } else {
                                getLogger().warning("World name for home location of faction " + nameKey + " is null in config. Home might be invalid.");
                            }
                        }
                    } catch (Exception e) {
                        getLogger().warning("Error loading home location for faction " + nameKey + ": " + e.getMessage() + ". Home will be null.");
                        homeLoc = null;
                    }
                }


                Faction faction = new Faction(originalName, ownerUUID, homeLoc, this);
                faction.setCurrentPower(facSec.getInt("currentPower"));
                faction.setAccumulatedFractionalPower(facSec.getDouble("fractionalPower", 0.0));
                faction.setClaimLimitOverride(facSec.getInt("claimLimitOverride", -1)); // Load claim limit override
                faction.setPvpProtected(facSec.getBoolean("pvpProtected", this.PVP_IN_TERRITORY_PROTECTION_ENABLED_BY_DEFAULT));
                // Load PvP status


                ConfigurationSection membersSec = facSec.getConfigurationSection("members");
                if (membersSec != null) {
                    for (String uuidStr : membersSec.getKeys(false)) {
                        try {
                            UUID memberUUID = UUID.fromString(uuidStr);
                            FactionRank rank = FactionRank.fromString(membersSec.getString(uuidStr));
                            faction.getMembersRaw().put(memberUUID, rank);
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Invalid UUID or Rank for member in faction " + nameKey + ": " + uuidStr);
                        }
                    }
                }
                faction.updatePowerOnMemberChangeAndLoad();
                List<String> claimStrings = facSec.getStringList("claimedChunks");
                for (String s : claimStrings) {
                    ChunkWrapper cw = ChunkWrapper.fromString(s);
                    if (cw != null) {
                        faction.getAllClaimedChunks_Modifiable().add(cw);
                        claimedChunks.put(cw, nameKey);
                    } else {
                        getLogger().warning("Failed to parse claim string: '" + s + "' for faction " + nameKey);
                    }
                }
                if (faction.getHomeChunk() != null && !faction.getClaimedChunks().contains(faction.getHomeChunk())) {
                    faction.getAllClaimedChunks_Modifiable().add(faction.getHomeChunk());
                    claimedChunks.put(faction.getHomeChunk(), nameKey);
                }


                List<Map<?, ?>> outpostMaps = facSec.getMapList("outposts");
                for (Map<?, ?> outpostMap : outpostMaps) {
                    try {
                        Map<String, Object> correctlyTypedMap = new HashMap<>();
                        for(Map.Entry<?,?> entry : outpostMap.entrySet()){
                            if(entry.getKey() instanceof String){
                                correctlyTypedMap.put((String)entry.getKey(), entry.getValue());
                            }
                        }
                        Outpost outpost = Outpost.deserialize(correctlyTypedMap);
                        if (outpost != null) {
                            faction.addOutpost(outpost);
                            outpost.getOutpostSpecificClaims().forEach(cw -> {
                                faction.getAllClaimedChunks_Modifiable().add(cw);
                                claimedChunks.put(cw, nameKey);
                            });
                        }
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "Error deserializing an outpost for faction " + nameKey, e);
                    }
                }


                if (ENEMY_SYSTEM_ENABLED) {
                    facSec.getStringList("enemies").forEach(enemyKey -> faction.addEnemy(enemyKey, System.currentTimeMillis()));
                    ConfigurationSection enemyTimeSec = facSec.getConfigurationSection("enemyDeclareTimestamps");
                    if (enemyTimeSec != null) {
                        for (String enemyKey : enemyTimeSec.getKeys(false)) {
                            faction.getEnemyDeclareTimestamps().put(enemyKey.toLowerCase(), enemyTimeSec.getLong(enemyKey));
                        }
                    }
                }
                if (ALLY_CHAT_ENABLED && ENEMY_SYSTEM_ENABLED) { // Ally system depends on enemy system
                    facSec.getStringList("allies").forEach(faction::addAlly);
                }


                facSec.getStringList("trustedPlayers").forEach(uuidStr -> {
                    try { faction.addTrusted(UUID.fromString(uuidStr)); }
                    catch (IllegalArgumentException ignored) { getLogger().warning("Bad trusted player UUID for " + nameKey +": " + uuidStr);}
                });
                if (VAULT_SYSTEM_ENABLED) {
                    List<?> vaultItemsRaw = facSec.getList("vaultContents");
                    if (vaultItemsRaw != null) {
                        ItemStack[] vaultContents = new ItemStack[Faction.VAULT_SIZE];
                        for (int i = 0; i < vaultItemsRaw.size() && i < Faction.VAULT_SIZE; i++) {
                            if (vaultItemsRaw.get(i) instanceof ItemStack) {
                                vaultContents[i] = (ItemStack) vaultItemsRaw.get(i);
                            } else if (vaultItemsRaw.get(i) == null) {
                                vaultContents[i] = null;
                            } else {
                                getLogger().warning("Invalid itemstack in vault for faction " + nameKey + " at index " + i + ": " + vaultItemsRaw.get(i).getClass().getName());
                            }
                        }
                        faction.setVaultContents(vaultContents);
                    }
                }

                faction.lateInitPluginReference(this);
                factionsByNameKey.put(nameKey, faction);
                faction.getMembers().keySet().forEach(uuid -> playerFactionMembership.put(uuid, nameKey));
            }
        }

        ConfigurationSection memberInvitesSection = dataConfig.getConfigurationSection("pendingMemberInvites");
        if (memberInvitesSection != null) {
            for (String factionKey : memberInvitesSection.getKeys(false)) {
                ConfigurationSection specificInvites = memberInvitesSection.getConfigurationSection(factionKey);
                if (specificInvites != null) {
                    Map<UUID, Long> invites = new ConcurrentHashMap<>();
                    for (String playerUUIDStr : specificInvites.getKeys(false)) {
                        try {
                            invites.put(UUID.fromString(playerUUIDStr), specificInvites.getLong(playerUUIDStr));
                        } catch (IllegalArgumentException e) { getLogger().warning("Bad UUID in member invites: " + playerUUIDStr);
                        }
                    }
                    if (!invites.isEmpty()) pendingMemberInvites.put(factionKey, invites);
                }
            }
        }

        if (ALLY_CHAT_ENABLED && ENEMY_SYSTEM_ENABLED) { // Ally system depends on enemy system
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
            facSec.set("fractionalPower", faction.getAccumulatedFractionalPower());
            facSec.set("claimLimitOverride", faction.getClaimLimitOverride());
            // Save claim limit override
            facSec.set("pvpProtected", faction.isPvpProtected());
            // Save PvP status


            ConfigurationSection membersSec = facSec.createSection("members");
            faction.getMembers().forEach((uuid, rank) -> membersSec.set(uuid.toString(), rank.name()));

            facSec.set("claimedChunks", faction.getClaimedChunks().stream().map(ChunkWrapper::toString).distinct().collect(Collectors.toList()));

            if (OUTPOST_SYSTEM_ENABLED) {
                facSec.set("outposts", faction.getOutposts().stream().map(Outpost::serialize).collect(Collectors.toList()));
            }


            if (ENEMY_SYSTEM_ENABLED) {
                facSec.set("enemies", new ArrayList<>(faction.getEnemyFactionKeys()));
                ConfigurationSection enemyTimeSec = facSec.createSection("enemyDeclareTimestamps");
                faction.getEnemyDeclareTimestamps().forEach((key, time) -> enemyTimeSec.set(key.toLowerCase(), time));
            }
            if (ALLY_CHAT_ENABLED && ENEMY_SYSTEM_ENABLED) { // Ally system depends on enemy system
                facSec.set("allies", new ArrayList<>(faction.getAllyFactionKeys()));
            }


            facSec.set("trustedPlayers", faction.getTrustedPlayers().stream().map(UUID::toString).collect(Collectors.toList()));
            if (VAULT_SYSTEM_ENABLED) {
                facSec.set("vaultContents", Arrays.asList(faction.getVaultContentsForSave()));
            }
        }

        ConfigurationSection memberInvitesSection = dataConfig.createSection("pendingMemberInvites");
        pendingMemberInvites.forEach((factionKey, invitesMap) -> {
            if (!invitesMap.isEmpty()) {
                ConfigurationSection specificInvites = memberInvitesSection.createSection(factionKey);
                invitesMap.forEach((playerUUID, expiry) -> specificInvites.set(playerUUID.toString(), expiry));
            }
        });
        if (ALLY_CHAT_ENABLED && ENEMY_SYSTEM_ENABLED) { // Ally system depends on enemy system
            ConfigurationSection allyRequestsSection = dataConfig.createSection("pendingAllyRequests");
            pendingAllyRequests.forEach((targetFacKey, requestsMap) -> {
                if (!requestsMap.isEmpty()) {
                    ConfigurationSection specificRequests = allyRequestsSection.createSection(targetFacKey);
                    requestsMap.forEach(specificRequests::set);
                }
            });
        }


        try {
            dataConfig.save(factionsDataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save factions.yml", e);
        }
    }

    private void loadFactionActivityData() {
        if (!factionActivityDataFile.exists()) {
            getLogger().info("No faction_activity.yml found, will be created on first activity/save.");
            return;
        }
        YamlConfiguration activityConfig = YamlConfiguration.loadConfiguration(factionActivityDataFile);
        ConfigurationSection activitySection = activityConfig.getConfigurationSection("last_online");
        if (activitySection != null) {
            for (String factionKey : activitySection.getKeys(false)) {
                factionLastOnlineTime.put(factionKey.toLowerCase(), activitySection.getLong(factionKey));
            }
        }
        getLogger().info("Loaded " + factionLastOnlineTime.size() + " faction activity records.");
    }

    private void saveFactionActivityData() {
        YamlConfiguration activityConfig = new YamlConfiguration();
        ConfigurationSection activitySection = activityConfig.createSection("last_online");
        factionLastOnlineTime.forEach((key, time) -> activitySection.set(key.toLowerCase(), time));
        try {
            activityConfig.save(factionActivityDataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save faction_activity.yml", e);
        }
    }


    public void disbandFactionInternal(Faction factionToDisband, boolean isAdminAction) {
        String nameKey = factionToDisband.getNameKey();
        new HashSet<>(factionToDisband.getClaimedChunks()).forEach(cw -> {
            claimedChunks.remove(cw);
        });
        factionsByNameKey.remove(nameKey);

        Set<UUID> membersToUpdateTab = new HashSet<>(factionToDisband.getMemberUUIDsOnly());

        factionToDisband.getMembers().keySet().forEach(playerUUID -> {
            playerFactionMembership.remove(playerUUID);
            Player p = Bukkit.getPlayer(playerUUID);
            if (p != null && p.isOnline() && !isAdminAction) {
                p.sendMessage(ChatColor.RED + "Your faction, " + factionToDisband.getName() + ", has been disbanded.");
            }
        });

        for (Faction otherFac : factionsByNameKey.values()) {
            otherFac.removeAlly(nameKey);
            otherFac.removeEnemy(nameKey);
        }

        pendingMemberInvites.remove(nameKey);
        pendingAllyRequests.remove(nameKey);
        pendingAllyRequests.values().forEach(map -> map.remove(nameKey));

        factionLastOnlineTime.remove(nameKey);
        if (dynmapManager != null && dynmapManager.isEnabled()) {
            dynmapManager.removeFactionClaimsFromMap(factionToDisband);
        }
        getLogger().info("Faction " + factionToDisband.getName() + " has been disbanded" + (isAdminAction ? " by an admin." : "."));
        for (UUID memberUUID : membersToUpdateTab) {
            Player onlinePlayer = Bukkit.getPlayer(memberUUID);
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                updatePlayerTabListName(onlinePlayer);
            }
        }

        saveFactionsData();
        saveFactionActivityData();
    }


    public Map<String, Faction> getFactionsByNameKey() {
        return Collections.unmodifiableMap(factionsByNameKey);
    }

    public Map<ChunkWrapper, String> getClaimedChunksMap() {
        return Collections.unmodifiableMap(claimedChunks);
    }
    public Map<ChunkWrapper, String> getClaimedChunksMapInternal() {
        return claimedChunks;
    }


    @Nullable
    public String getFactionOwningChunk(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) return null;
        return claimedChunks.get(new ChunkWrapper(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    @Nullable
    public Faction getFactionOwningChunkAsFaction(Chunk chunk) {
        String ownerKey = getFactionOwningChunk(chunk);
        if (ownerKey != null) {
            return getFaction(ownerKey);
        }
        return null;
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
            return false;
        } else {
            playersInFactionChat.add(playerUUID);
            playersInAllyChat.remove(playerUUID);
            return true;
        }
    }
    public void setPlayerFactionChat(UUID playerUUID, boolean inFactionChat) {
        if (inFactionChat) {
            playersInFactionChat.add(playerUUID);
            playersInAllyChat.remove(playerUUID);
        } else {
            playersInFactionChat.remove(playerUUID);
        }
    }

    public boolean isPlayerInAllyChat(UUID playerUUID) {
        return playersInAllyChat.contains(playerUUID);
    }
    public boolean togglePlayerAllyChat(UUID playerUUID) {
        if (playersInAllyChat.contains(playerUUID)) {
            playersInAllyChat.remove(playerUUID);
            return false;
        } else {
            playersInAllyChat.add(playerUUID);
            playersInFactionChat.remove(playerUUID);
            return true;
        }
    }
    public void setPlayerAllyChat(UUID playerUUID, boolean inAllyChat) {
        if (inAllyChat) {
            playersInAllyChat.add(playerUUID);
            playersInFactionChat.remove(playerUUID);
        } else {
            playersInAllyChat.remove(playerUUID);
        }
    }


    public boolean isPlayerAutoclaiming(UUID playerUUID) {
        return playersWithAutoclaim.contains(playerUUID);
    }
    public boolean toggleAutoclaim(UUID playerUUID) {
        if (playersWithAutoclaim.contains(playerUUID)) {
            playersWithAutoclaim.remove(playerUUID);
            return false;
        } else {
            playersWithAutoclaim.add(playerUUID);
            return true;
        }
    }


    public void initiateTeleportWarmup(Player player, Location targetLocation, String successMessage) {
        UUID playerUUID = player.getUniqueId();
        if (activeTeleportRequests.containsKey(playerUUID)) {
            player.sendMessage(ChatColor.RED + "You already have an active teleport request.");
            return;
        }
        if (TELEPORT_WARMUP_SECONDS <= 0) {
            player.teleport(targetLocation);
            player.sendMessage(successMessage);
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Teleporting in " + TELEPORT_WARMUP_SECONDS + " seconds. Don't move or take damage!");
        int taskId = Bukkit.getScheduler().runTaskLater(this, () -> {
            TeleportRequest completedRequest = activeTeleportRequests.remove(playerUUID);
            if (completedRequest != null) {
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

    public boolean isAnyMemberOnline(Faction faction) {
        if (faction == null) return false;
        for (UUID memberId : faction.getMemberUUIDsOnly()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline()) {
                return true;
            }
        }
        return false;
    }

    private Set<ChunkWrapper> findSurroundedUnclaimedChunks(Chunk initialChunk, Faction faction) {
        Queue<ChunkWrapper> queue = new LinkedList<>();
        Set<ChunkWrapper> visitedInBFS = new HashSet<>();
        Set<ChunkWrapper> pocket = new HashSet<>();

        String factionNameKey = faction.getNameKey();
        World world = initialChunk.getWorld();
        String worldName = world.getName();

        int[] dX = {0, 0, 1, -1};
        int[] dZ = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            ChunkWrapper directNeighbor = new ChunkWrapper(worldName, initialChunk.getX() + dX[i], initialChunk.getZ() + dZ[i]);
            if (!claimedChunks.containsKey(directNeighbor)) {
                queue.offer(directNeighbor);
                visitedInBFS.add(directNeighbor);
            }
        }

        if (queue.isEmpty()) {
            return Collections.emptySet();
        }

        int bfsIterations = 0;
        while (!queue.isEmpty() && pocket.size() < CLAIM_FILL_MAX_POCKET_SIZE && bfsIterations < CLAIM_FILL_BFS_LIMIT) {
            ChunkWrapper current = queue.poll();
            bfsIterations++;

            if (claimedChunks.containsKey(current)) {
                continue;
            }
            pocket.add(current);
            for (int i = 0; i < 4; i++) {
                ChunkWrapper neighbor = new ChunkWrapper(worldName, current.getX() + dX[i], current.getZ() + dZ[i]);
                if (!visitedInBFS.contains(neighbor) && !claimedChunks.containsKey(neighbor)) {
                    visitedInBFS.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        if (pocket.isEmpty()) return Collections.emptySet();
        if (pocket.size() >= CLAIM_FILL_MAX_POCKET_SIZE) {
            getLogger().warning("[ClaimFill] Pocket size limit reached for " + faction.getName() + ". Size: " + pocket.size());
            return pocket;
        }
        if (bfsIterations >= CLAIM_FILL_BFS_LIMIT) {
            getLogger().warning("[ClaimFill] BFS iteration limit reached for " + faction.getName() + ". Pocket might be unbounded or too large to process safely.");
            return Collections.emptySet();
        }

        for (ChunkWrapper chunkInPocket : pocket) {
            for (int i = 0; i < 4; i++) {
                ChunkWrapper boundaryChunk = new ChunkWrapper(worldName, chunkInPocket.getX() + dX[i], chunkInPocket.getZ() + dZ[i]);
                if (pocket.contains(boundaryChunk)) continue;

                String ownerOfBoundary = claimedChunks.get(boundaryChunk);
                if (ownerOfBoundary == null || !ownerOfBoundary.equals(factionNameKey)) {
                    return Collections.emptySet();
                }
            }
        }
        return pocket;
    }

    public void handleClaimFill(Player player, Faction faction) {
        if (!CLAIMFILL_ENABLED) {
            player.sendMessage(ChatColor.RED + "Claimfill is currently disabled.");
            return;
        }
        Chunk playerChunk = player.getLocation().getChunk();
        ChunkWrapper playerCW = new ChunkWrapper(playerChunk.getWorld().getName(), playerChunk.getX(), playerChunk.getZ());
        if (!Objects.equals(getFactionOwningChunk(playerChunk), faction.getNameKey())) {
            player.sendMessage(ChatColor.RED + "You must be standing in your own faction's territory to use claimfill.");
            return;
        }

        Set<ChunkWrapper> toFill = findSurroundedUnclaimedChunks(playerChunk, faction);
        if (toFill.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No fillable pockets of land found connected to this chunk.");
            return;
        }
        if (toFill.size() >= CLAIM_FILL_MAX_POCKET_SIZE) {
            player.sendMessage(ChatColor.RED + "The fillable area is too large (max " + CLAIM_FILL_MAX_POCKET_SIZE + " chunks). Try a smaller area.");
            return;
        }


        int claimCostPerChunk = COST_CLAIM_CHUNK; // MODIFIED: Use the standard claim cost
        int totalCost = toFill.size() * claimCostPerChunk;
        int maxClaims = faction.getMaxClaimsCalculated(this);
        if (faction.getClaimLimitOverride() != -1) { // Check for override
            maxClaims = faction.getClaimLimitOverride();
        }


        if (faction.getClaimedChunks().size() + toFill.size() > maxClaims && maxClaims != Integer.MAX_VALUE) {
            player.sendMessage(ChatColor.RED + "Claiming this area (" + toFill.size() + " chunks) would exceed your faction's claim limit of " + maxClaims + ".");
            return;
        }

        if (faction.getCurrentPower() < totalCost) {
            player.sendMessage(ChatColor.RED + "Not enough power to claim this area. Cost: " + totalCost + " power (" + toFill.size() + " chunks). You have " + faction.getCurrentPower() + " power.");
            return;
        }

        int successfullyClaimed = 0;
        for (ChunkWrapper cw : toFill) {
            Chunk chunkToClaim = Bukkit.getWorld(cw.getWorldName()).getChunkAt(cw.getX(), cw.getZ());
            faction.addClaim(cw);
            claimedChunks.put(cw, faction.getNameKey());
            successfullyClaimed++;
        }

        if (successfullyClaimed > 0) {
            faction.removePower(successfullyClaimed * claimCostPerChunk);
            player.sendMessage(ChatColor.GREEN + "Successfully filled and claimed " + successfullyClaimed + " chunks for " + (successfullyClaimed * claimCostPerChunk) + " power!");
            updateFactionActivity(faction.getNameKey());
            if (dynmapManager != null && dynmapManager.isEnabled()) {
                dynmapManager.updateFactionClaimsVisual(faction);
            }
            saveFactionsData();
        } else {
            player.sendMessage(ChatColor.RED + "Could not claim any chunks in the fill operation.");
        }
    }

    public void updatePlayerTabListName(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Faction playerFaction = getFactionByPlayer(player.getUniqueId());
        String prefix = "";
        if (playerFaction != null) {
            FactionRank rank = playerFaction.getRank(player.getUniqueId());
            String rankPrefixString = ""; // For display in tab
            ChatColor factionColor = ChatColor.GRAY;
            if (rank != null) {
                switch (rank) {
                    case OWNER:
                        rankPrefixString = "**";
                        factionColor = ChatColor.GOLD;
                        break;
                    case ADMIN:
                        rankPrefixString = "*";
                        factionColor = ChatColor.RED;
                        break;
                    case MEMBER:
                        factionColor = ChatColor.GREEN;
                        break;
                    case ASSOCIATE:
                        factionColor = ChatColor.AQUA;
                        break;
                }
            }
            String factionTag = playerFaction.getName().substring(0, Math.min(playerFaction.getName().length(), FACTION_TAG_LENGTH)).toUpperCase();
            prefix = ChatColor.DARK_GRAY + "[" + factionColor + factionTag + ChatColor.DARK_GRAY + "] " + rankPrefixString + factionColor;
            // Apply faction color to name too

            String baseName = player.getName();
            player.setPlayerListName(prefix + baseName + ChatColor.RESET);

        } else {
            if (player.getPlayerListName() != null && !player.getPlayerListName().equals(player.getName())) {
                player.setPlayerListName(player.getName());
            }
        }
    }

    @Nullable
    public BukkitTask getPowerDecayTask() {
        return powerDecayTask;
    }
}