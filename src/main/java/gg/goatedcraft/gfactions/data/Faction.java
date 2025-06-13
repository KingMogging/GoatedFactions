package gg.goatedcraft.gfactions.data;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation") // For OfflinePlayer methods
public class Faction {
    private final String originalName;
    private final String nameKey;
    private UUID ownerUUID;
    private String description;
    private final Map<UUID, FactionRank> members;
    private final Set<ChunkWrapper> allClaimedChunks;
    private Location homeLocation;
    private ChunkWrapper homeChunk;

    private final List<Outpost> outposts;

    private int currentPower;
    private double accumulatedFractionalPower = 0.0;
    private final Set<String> enemyFactionKeys;
    private final Set<String> allyFactionKeys;
    private final Map<String, Long> enemyDeclareTimestamps;
    private final Set<UUID> trustedPlayers;
    private Inventory vault;
    public static final int VAULT_SIZE = 27;

    private long lastOnlineTime;
    private int claimLimitOverride = -1;
    private boolean pvpProtected;
    private String dynmapColorHex; // NEW: Field for custom Dynmap color

    private transient GFactionsPlugin plugin;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{6}$");


    public Faction(String name, UUID ownerUUID, @Nullable Location initialHomeLocation, GFactionsPlugin pluginInstance) {
        this.originalName = name;
        this.nameKey = name.toLowerCase();
        this.ownerUUID = ownerUUID;
        this.plugin = pluginInstance;
        this.description = "A new faction.";
        this.dynmapColorHex = null; // Default to null (no custom color)

        this.members = new ConcurrentHashMap<>();
        this.members.put(ownerUUID, FactionRank.OWNER);

        this.allClaimedChunks = ConcurrentHashMap.newKeySet();
        this.outposts = new ArrayList<>();

        if (initialHomeLocation != null && initialHomeLocation.getWorld() != null) {
            setHomeLocation(initialHomeLocation);
        } else if (pluginInstance != null) {
            pluginInstance.getLogger().warning("Faction " + name + " created with null initialHomeLocation or world. Home needs to be set.");
        }

        this.enemyFactionKeys = ConcurrentHashMap.newKeySet();
        this.allyFactionKeys = ConcurrentHashMap.newKeySet();
        this.enemyDeclareTimestamps = new ConcurrentHashMap<>();
        this.trustedPlayers = ConcurrentHashMap.newKeySet();

        this.currentPower = (this.plugin != null) ? this.plugin.POWER_INITIAL : 100;
        if (this.plugin != null && this.plugin.VAULT_SYSTEM_ENABLED) {
            this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + this.originalName + " Vault");
        }
        this.lastOnlineTime = System.currentTimeMillis();
        this.pvpProtected = (this.plugin != null) ? this.plugin.PVP_IN_TERRITORY_PROTECTION_ENABLED_BY_DEFAULT : false;
        updatePowerOnMemberChangeAndLoad();
    }

    public void lateInitPluginReference(GFactionsPlugin pluginInstance) {
        if (this.plugin == null) {
            this.plugin = pluginInstance;
            if (this.currentPower == 100 && pluginInstance.POWER_INITIAL != 100 && members.size() == 1) {
                this.currentPower = pluginInstance.POWER_INITIAL;
            }
            if (this.homeLocation != null && this.homeLocation.getWorld() == null && this.homeChunk != null) {
                World world = Bukkit.getWorld(this.homeChunk.getWorldName());
                if (world != null) {
                    this.homeLocation.setWorld(world);
                } else {
                    plugin.getLogger().warning("Could not reload world for home location of faction " + getName() + ": " + this.homeChunk.getWorldName());
                }
            }
            for(int i = 0; i < outposts.size(); i++){
                Outpost outpost = outposts.get(i);
                outpost.setOutpostID(i + 1);
                Location outpostSpawn = outpost.getOutpostSpawnLocation();
                if (outpostSpawn == null) {
                    plugin.getLogger().warning("Outpost " + outpost.getOutpostID() + " for faction " + getName() + " has an invalid spawn location (world not loaded or location data missing).");
                }
            }
            if (this.vault == null && this.plugin.VAULT_SYSTEM_ENABLED) {
                this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + this.originalName + " Vault");
            }
            this.pvpProtected = this.plugin.PVP_IN_TERRITORY_PROTECTION_ENABLED_BY_DEFAULT;
            updatePowerOnMemberChangeAndLoad();
        }
    }

    // --- Getters ---
    public String getName() { return originalName; }
    public String getNameKey() { return nameKey; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getDescription() { return description; }
    @Nullable public String getDynmapColorHex() { return dynmapColorHex; } // NEW: Getter
    public Map<UUID, FactionRank> getMembers() { return Collections.unmodifiableMap(members); }
    public Map<UUID, FactionRank> getMembersRaw() { return members; }
    public Set<UUID> getMemberUUIDsOnly() { return Collections.unmodifiableSet(members.keySet()); }
    @Nullable public FactionRank getRank(UUID playerUUID) { return members.get(playerUUID); }

    public Set<ChunkWrapper> getClaimedChunks() {
        return Collections.unmodifiableSet(allClaimedChunks);
    }
    public Set<ChunkWrapper> getAllClaimedChunks_Modifiable() {
        return allClaimedChunks;
    }

    @Nullable public Location getHomeLocation() {
        if (homeLocation != null && homeLocation.getWorld() == null && homeChunk != null) {
            World world = Bukkit.getWorld(homeChunk.getWorldName());
            if (world != null) {
                homeLocation.setWorld(world);
            } else {
                if(plugin != null) plugin.getLogger().severe("CRITICAL: World " + homeChunk.getWorldName() + " for faction " + getName() + "'s home is not loaded!");
                return null;
            }
        }
        return homeLocation;
    }
    @Nullable public ChunkWrapper getHomeChunk() { return homeChunk; }

    public int getCurrentPower() { return currentPower; }
    public int getClaimLimitOverride() { return claimLimitOverride; }
    public boolean isPvpProtected() { return pvpProtected; }

    public int getMaxPowerCalculated(GFactionsPlugin pluginInstance) {
        GFactionsPlugin currentPlugin = (this.plugin != null) ? this.plugin : pluginInstance;
        if (currentPlugin == null) return Math.max(1, members.size()) * 100;

        int calculatedPower = Math.max(1, members.size()) * currentPlugin.POWER_PER_MEMBER_BONUS;

        if (currentPlugin.MAX_POWER_ABSOLUTE > 0) {
            return Math.min(calculatedPower, currentPlugin.MAX_POWER_ABSOLUTE);
        }
        return calculatedPower;
    }

    public double getAccumulatedFractionalPower() { return accumulatedFractionalPower; }
    public void addFractionalPower(double amount) {
        this.accumulatedFractionalPower += amount;
    }
    public void resetFractionalPower(double remainder) {
        this.accumulatedFractionalPower = remainder;
    }
    public void setAccumulatedFractionalPower(double amount) {
        this.accumulatedFractionalPower = amount;
    }

    public Set<String> getEnemyFactionKeys() { return Collections.unmodifiableSet(enemyFactionKeys); }
    public Set<String> getAllyFactionKeys() { return Collections.unmodifiableSet(allyFactionKeys); }
    public Map<String, Long> getEnemyDeclareTimestamps() { return Collections.unmodifiableMap(enemyDeclareTimestamps); }
    public Map<String, Long> getEnemyDeclareTimestampsInternal() { return enemyDeclareTimestamps; }

    public Set<UUID> getTrustedPlayers() { return Collections.unmodifiableSet(trustedPlayers); }
    public Inventory getVault() {
        if (vault == null && plugin != null && plugin.VAULT_SYSTEM_ENABLED) {
            this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + this.originalName + " Vault");
        }
        return vault;
    }
    public List<Outpost> getOutposts() { return Collections.unmodifiableList(outposts); }
    public long getLastOnlineTime() { return lastOnlineTime; }

    // --- Setters & Modifiers ---
    public void setOwnerUUID(UUID newOwnerUUID) {
        if (members.containsKey(this.ownerUUID) && !this.ownerUUID.equals(newOwnerUUID)) {
            members.put(this.ownerUUID, FactionRank.ADMIN);
        }
        this.ownerUUID = newOwnerUUID;
        members.put(newOwnerUUID, FactionRank.OWNER);
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }

    public void setDescription(String description) {
        if (plugin != null && description.length() > plugin.DESCRIPTION_MAX_LENGTH) {
            this.description = description.substring(0, plugin.DESCRIPTION_MAX_LENGTH);
        } else {
            this.description = description;
        }
        if (plugin != null) {
            plugin.updateFactionActivity(this.nameKey);
            if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
                plugin.getDynmapManager().updateFactionAppearance(this);
            }
        }
    }

    // NEW: Setter for custom Dynmap color
    public boolean setDynmapColorHex(@Nullable String hexColor) {
        if (hexColor == null || hexColor.equalsIgnoreCase("reset") || hexColor.equalsIgnoreCase("clear")) {
            this.dynmapColorHex = null;
            if (plugin != null) plugin.updateFactionActivity(this.nameKey);
            return true;
        }
        if (HEX_COLOR_PATTERN.matcher(hexColor).matches()) {
            this.dynmapColorHex = hexColor;
            if (plugin != null) plugin.updateFactionActivity(this.nameKey);
            return true;
        }
        return false; // Invalid format
    }

    public void setClaimLimitOverride(int limit) {
        this.claimLimitOverride = Math.max(-1, limit);
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }
    public void setPvpProtected(boolean pvpProtected) {
        this.pvpProtected = pvpProtected;
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }

    public void setHomeLocation(@Nullable Location newHomeLocation) {
        this.homeLocation = newHomeLocation;
        if (newHomeLocation != null && newHomeLocation.getWorld() != null) {
            this.homeChunk = new ChunkWrapper(newHomeLocation.getWorld().getName(), newHomeLocation.getChunk().getX(), newHomeLocation.getChunk().getZ());
            this.allClaimedChunks.add(this.homeChunk);
        } else {
            this.homeChunk = null;
            if (plugin != null && newHomeLocation != null) {
                plugin.getLogger().warning("Attempted to set home for " + getName() + " with a null world. Home chunk invalidated.");
            }
        }
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }

    public void relocateHomeToRandomClaim() {
        if (plugin == null) return;
        Set<ChunkWrapper> mainTerritoryClaims = new HashSet<>(allClaimedChunks);
        if (homeChunk != null) {
            mainTerritoryClaims.remove(homeChunk);
        }
        for (Outpost outpost : outposts) {
            mainTerritoryClaims.removeAll(outpost.getOutpostSpecificClaims());
        }

        if (mainTerritoryClaims.isEmpty()) {
            if (plugin.OUTPOST_SYSTEM_ENABLED && !outposts.isEmpty()) {
                Outpost firstOutpost = outposts.get(0);
                Location outpostSpawn = firstOutpost.getOutpostSpawnLocation();
                if (outpostSpawn != null && outpostSpawn.getWorld() != null) {
                    setHomeLocation(outpostSpawn);
                    allClaimedChunks.addAll(firstOutpost.getOutpostSpecificClaims());
                    outposts.remove(firstOutpost);
                    for(int i=0; i<outposts.size(); i++){ outposts.get(i).setOutpostID(i+1); }
                    plugin.getLogger().info("Faction " + getName() + " home relocated to former outpost at " + firstOutpost.getSpawnChunk().toStringShort());
                    broadcastMessage(ChatColor.YELLOW + "Your faction's home was lost. Outpost #" + firstOutpost.getOutpostID() + " has become your new faction home.", null);
                    if (plugin != null) plugin.updateFactionActivity(this.nameKey);
                    return;
                }
            }
            plugin.getLogger().warning("Faction " + getName() + " lost its home and has no other claims or valid outposts to relocate to.");
            this.homeLocation = null;
            this.homeChunk = null;
            broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Your faction's home was lost and no land was found to relocate it! You MUST set a new home with /f sethome.", null);
            if (plugin != null) plugin.updateFactionActivity(this.nameKey);
            return;
        }

        List<ChunkWrapper> possibleRelocationChunks = new ArrayList<>(mainTerritoryClaims);
        Collections.shuffle(possibleRelocationChunks);
        ChunkWrapper newHomeChunkWrapper = possibleRelocationChunks.get(0);
        World world = Bukkit.getWorld(newHomeChunkWrapper.getWorldName());
        if (world == null) {
            if(plugin != null) plugin.getLogger().severe("Failed to relocate home for " + getName() + ": World " + newHomeChunkWrapper.getWorldName() + " not loaded for new home chunk.");
            broadcastMessage(ChatColor.RED + "Critical error: Could not relocate faction home due to an unloaded world. Contact an admin.", null);
            return;
        }
        Chunk newHomeSpigotChunk = world.getChunkAt(newHomeChunkWrapper.getX(), newHomeChunkWrapper.getZ());
        Location newHomeLoc = findSafeSurfaceLocation(newHomeSpigotChunk);

        setHomeLocation(newHomeLoc);
        plugin.getLogger().info("Faction " + getName() + "'s home relocated to chunk: " + newHomeChunkWrapper.toStringShort());
        broadcastMessage(ChatColor.RED + "Your faction's home chunk was lost! Your home has been randomly relocated within your territory to " + newHomeChunkWrapper.toStringShort() + ".", null);
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }

    @NotNull
    private Location findSafeSurfaceLocation(Chunk chunk) {
        World world = chunk.getWorld();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            int x = chunk.getX() * 16 + random.nextInt(16);
            int z = chunk.getZ() * 16 + random.nextInt(16);
            Block highestBlock = world.getHighestBlockAt(x,z);
            int y = highestBlock.getY() + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            Block blockAtFeet = world.getBlockAt(loc);
            Block blockAtHead = world.getBlockAt(loc.clone().add(0,1,0));
            Block blockBelowFeet = world.getBlockAt(loc.clone().add(0,-1,0));
            if (blockBelowFeet.getType().isSolid() && !blockAtFeet.getType().isSolid() && !blockAtHead.getType().isSolid()) {
                return loc;
            }
        }
        int cx = chunk.getX() * 16 + 8;
        int cz = chunk.getZ() * 16 + 8;
        return new Location(world, cx + 0.5, world.getHighestBlockYAt(cx, cz) + 1.0, cz + 0.5);
    }

    public void setCurrentPower(int power) {
        this.currentPower = Math.max(0, Math.min(power, getMaxPowerCalculated(this.plugin)));
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }
    public void addPower(int amount) {
        setCurrentPower(this.currentPower + amount);
    }
    public void removePower(int amount) {
        setCurrentPower(this.currentPower - amount);
    }

    public void updatePowerOnMemberChangeAndLoad() {
        if (plugin == null) return;
        int newMaxPower = getMaxPowerCalculated(plugin);
        if (members.size() == 1 && members.containsKey(ownerUUID) && members.get(ownerUUID) == FactionRank.OWNER) {
            this.currentPower = Math.min(plugin.POWER_INITIAL, newMaxPower);
        } else {
            this.currentPower = Math.min(this.currentPower, newMaxPower);
        }
        this.currentPower = Math.max(0, this.currentPower);
    }

    public boolean addMember(UUID memberUUID, FactionRank rank) {
        if (members.containsKey(memberUUID)) return false;
        members.put(memberUUID, rank);
        updatePowerOnMemberChangeAndLoad();
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
        return true;
    }

    public boolean removeMember(UUID memberUUID) {
        if (memberUUID.equals(ownerUUID)) return false;
        if (members.remove(memberUUID) != null) {
            updatePowerOnMemberChangeAndLoad();
            if (plugin != null) plugin.updateFactionActivity(this.nameKey);
            return true;
        }
        return false;
    }

    public boolean promotePlayer(UUID memberUUID, FactionRank newRank) {
        if (!members.containsKey(memberUUID) || members.get(memberUUID) == FactionRank.OWNER) {
            return false;
        }
        FactionRank currentRank = members.get(memberUUID);
        if (newRank.ordinal() <= currentRank.ordinal() || newRank == FactionRank.OWNER) {
            if (!((currentRank == FactionRank.ASSOCIATE && (newRank == FactionRank.MEMBER || newRank == FactionRank.ADMIN)) ||
                    (currentRank == FactionRank.MEMBER && newRank == FactionRank.ADMIN))) {
                return false;
            }
        }
        members.put(memberUUID, newRank);
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
        return true;
    }

    public boolean demotePlayer(UUID memberUUID, FactionRank newRank) {
        if (!members.containsKey(memberUUID) || members.get(memberUUID) == FactionRank.OWNER) {
            return false;
        }
        FactionRank currentRank = members.get(memberUUID);
        if (newRank.ordinal() >= currentRank.ordinal() || newRank == FactionRank.OWNER) {
            return false;
        }
        members.put(memberUUID, newRank);
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
        return true;
    }

    public void addClaim(ChunkWrapper chunkWrapper, boolean isOutpostClaimHint, @Nullable Outpost specificOutpost) {
        this.allClaimedChunks.add(chunkWrapper);
        if (plugin != null && plugin.OUTPOST_SYSTEM_ENABLED && isOutpostClaimHint && specificOutpost != null && outposts.contains(specificOutpost)) {
            specificOutpost.addClaim(chunkWrapper);
        }
        if (!isOutpostClaimHint || specificOutpost == null) {
            checkForAndHandleTerritoryMerge();
        }
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }
    public void addClaim(ChunkWrapper chunkWrapper) {
        addClaim(chunkWrapper, false, null);
    }

    public void removeClaim(ChunkWrapper chunkWrapper) {
        boolean removed = this.allClaimedChunks.remove(chunkWrapper);
        if (!removed) return;

        if (plugin != null && plugin.OUTPOST_SYSTEM_ENABLED) {
            Outpost associatedOutpost = null;
            for (Outpost outpost : outposts) {
                if (outpost.containsClaim(chunkWrapper)) {
                    outpost.removeClaim(chunkWrapper);
                    associatedOutpost = outpost;
                    break;
                }
            }
            if (associatedOutpost != null && chunkWrapper.equals(associatedOutpost.getSpawnChunk())) {
                if (associatedOutpost.getOutpostSpecificClaims().isEmpty()) {
                    outposts.remove(associatedOutpost);
                    for(int i=0; i<outposts.size(); i++){ outposts.get(i).setOutpostID(i+1); }
                    if (plugin != null) {
                        plugin.getLogger().info("Outpost #" + associatedOutpost.getOutpostID() + " for " + getName() + " disbanded (last claim lost).");
                        broadcastMessage(ChatColor.YELLOW + "Your outpost #" + associatedOutpost.getOutpostID() + " at " + chunkWrapper.toStringShort() + " was disbanded as its last claim was lost.", null);
                    }
                } else {
                    if (plugin != null) {
                        plugin.getLogger().warning("Outpost #" + associatedOutpost.getOutpostID() + " for " + getName() + " lost its spawn chunk but still has claims.");
                        broadcastMessage(ChatColor.RED + "Your outpost #" + associatedOutpost.getOutpostID() + " at " + chunkWrapper.toStringShort() + " lost its spawn chunk! It may be inaccessible.", null);
                    }
                }
            }
        }

        if (plugin != null && chunkWrapper.equals(this.homeChunk)) {
            plugin.getLogger().info("Home chunk " + chunkWrapper.toStringShort() + " for faction " + getName() + " is being lost. Relocating home...");
            relocateHomeToRandomClaim();
        }
        checkForAndHandleTerritoryMerge();
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }

    public boolean isConnectedToAnySpawnPoint(ChunkWrapper targetChunk) {
        if (allClaimedChunks.isEmpty()) return false;
        if (targetChunk.equals(homeChunk)) return true;
        if (plugin != null && plugin.OUTPOST_SYSTEM_ENABLED) {
            for (Outpost outpost : outposts) {
                if (targetChunk.equals(outpost.getSpawnChunk())) return true;
            }
        }

        Queue<ChunkWrapper> queue = new LinkedList<>();
        Set<ChunkWrapper> visited = new HashSet<>();

        if (homeChunk != null && allClaimedChunks.contains(homeChunk)) {
            queue.offer(homeChunk);
            visited.add(homeChunk);
        }
        if (plugin != null && plugin.OUTPOST_SYSTEM_ENABLED) {
            for (Outpost outpost : outposts) {
                ChunkWrapper outpostSpawn = outpost.getSpawnChunk();
                if (allClaimedChunks.contains(outpostSpawn) && !visited.contains(outpostSpawn)) {
                    queue.offer(outpostSpawn);
                    visited.add(outpostSpawn);
                }
            }
        }

        if (queue.isEmpty() && !allClaimedChunks.isEmpty()) {
            return false;
        }

        while (!queue.isEmpty()) {
            ChunkWrapper current = queue.poll();
            if (current.equals(targetChunk)) return true;

            int[] dX = {0, 0, 1, -1};
            int[] dZ = {1, -1, 0, 0};

            for (int i = 0; i < 4; i++) {
                ChunkWrapper neighbor = new ChunkWrapper(current.getWorldName(), current.getX() + dX[i], current.getZ() + dZ[i]);
                if (allClaimedChunks.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        return false;
    }

    public boolean isChunkAdjacentToExistingClaim(ChunkWrapper targetChunk) {
        if (allClaimedChunks.isEmpty()) return true;

        int[] dX = {0, 0, 1, -1};
        int[] dZ = {1, -1, 0, 0};

        for (ChunkWrapper claimed : allClaimedChunks) {
            if (!claimed.getWorldName().equals(targetChunk.getWorldName())) continue;
            for (int i = 0; i < 4; i++) {
                if (claimed.getX() + dX[i] == targetChunk.getX() && claimed.getZ() + dZ[i] == targetChunk.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    public Outpost getAttachmentTerritory(ChunkWrapper newClaim) {
        if (plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED) return null;
        if (homeChunk != null && isChunkAdjacentTo(newClaim, getClaimsConnectedTo(homeChunk))) {
            return null;
        }
        for (Outpost outpost : outposts) {
            if (isChunkAdjacentTo(newClaim, outpost.getOutpostSpecificClaims())) {
                return outpost;
            }
        }
        return null;
    }

    public boolean isChunkAdjacentTo(ChunkWrapper target, Set<ChunkWrapper> territoryClaims) {
        if (territoryClaims.isEmpty()) return false;
        int[] dX = {0, 0, 1, -1};
        int[] dZ = {1, -1, 0, 0};
        for (ChunkWrapper claimed : territoryClaims) {
            if (!claimed.getWorldName().equals(target.getWorldName())) continue;
            for (int i = 0; i < 4; i++) {
                if (claimed.getX() + dX[i] == target.getX() && claimed.getZ() + dZ[i] == target.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<ChunkWrapper> getClaimsConnectedTo(ChunkWrapper startChunk) {
        Set<ChunkWrapper> connected = new HashSet<>();
        if (startChunk == null || !allClaimedChunks.contains(startChunk)) return connected;

        Queue<ChunkWrapper> queue = new LinkedList<>();
        Set<ChunkWrapper> visited = new HashSet<>();

        queue.offer(startChunk);
        visited.add(startChunk);
        connected.add(startChunk);

        while(!queue.isEmpty()){
            ChunkWrapper current = queue.poll();
            int[] dX = {0, 0, 1, -1};
            int[] dZ = {1, -1, 0, 0};
            for(int i=0; i < 4; i++){
                ChunkWrapper neighbor = new ChunkWrapper(current.getWorldName(), current.getX() + dX[i], current.getZ() + dZ[i]);
                if(allClaimedChunks.contains(neighbor) && !visited.contains(neighbor)){
                    visited.add(neighbor);
                    connected.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        return connected;
    }

    public void checkForAndHandleTerritoryMerge() {
        if (homeChunk == null || plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED || outposts.isEmpty()) return;

        Set<ChunkWrapper> mainHomeTerritory = getClaimsConnectedTo(homeChunk);

        List<Outpost> outpostsToRemove = new ArrayList<>();
        for (Outpost outpost : new ArrayList<>(outposts)) {
            boolean merged = false;
            for (ChunkWrapper outpostClaim : new HashSet<>(outpost.getOutpostSpecificClaims())) {
                if (isChunkAdjacentTo(outpostClaim, mainHomeTerritory)) {
                    merged = true;
                    break;
                }
            }

            if (merged) {
                plugin.getLogger().info("Outpost " + (outpost.getOutpostID() > 0 ? "#"+outpost.getOutpostID() : outpost.getSpawnChunk().toStringShort()) + " for faction " + getName() + " has merged with main territory.");
                broadcastMessage(ChatColor.GREEN + "Your outpost at " + outpost.getSpawnChunk().toStringShort() + " has connected to your main faction territory and has been merged!", null);
                outpostsToRemove.add(outpost);
            }
        }
        if (!outpostsToRemove.isEmpty()) {
            outposts.removeAll(outpostsToRemove);
            for(int i=0; i<outposts.size(); i++){
                outposts.get(i).setOutpostID(i+1);
            }
            if (plugin != null) plugin.updateFactionActivity(this.nameKey);
        }
    }

    // --- Membership & Roles ---
    public boolean isOwner(UUID playerUUID) { return playerUUID.equals(this.ownerUUID); }
    public boolean isAdmin(UUID playerUUID) {
        FactionRank rank = members.get(playerUUID);
        return rank != null && rank.isAdminOrHigher();
    }
    public boolean isAssociate(UUID playerUUID) {
        FactionRank rank = members.get(playerUUID);
        return rank != null && rank == FactionRank.ASSOCIATE;
    }
    public boolean isMemberOrHigher(UUID playerUUID) {
        FactionRank rank = members.get(playerUUID);
        return rank != null && rank.isMemberOrHigher();
    }
    public boolean isAssociateOrHigher(UUID playerUUID) {
        FactionRank rank = members.get(playerUUID);
        return rank != null; // Since ASSOCIATE is the lowest rank, any valid rank is AssociateOrHigher
    }
    public int getTotalSize() { return members.size(); }

    // --- Relations ---
    public boolean isEnemy(String otherFactionKey) {
        if (plugin == null || !plugin.ENEMY_SYSTEM_ENABLED) return false;
        return enemyFactionKeys.contains(otherFactionKey.toLowerCase());
    }
    public void addEnemy(String otherFactionKey, long timestamp) {
        if (plugin == null || !plugin.ENEMY_SYSTEM_ENABLED) return;
        String keyLC = otherFactionKey.toLowerCase();
        removeAlly(keyLC);
        enemyFactionKeys.add(keyLC);
        enemyDeclareTimestamps.put(keyLC, timestamp);
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }
    public boolean removeEnemy(String otherFactionKey) {
        if (plugin == null || !plugin.ENEMY_SYSTEM_ENABLED) return false;
        String keyLC = otherFactionKey.toLowerCase();
        boolean changed = enemyFactionKeys.remove(keyLC);
        if (enemyDeclareTimestamps.remove(keyLC) != null) {
            changed = true;
        }
        if (changed && plugin != null) plugin.updateFactionActivity(this.nameKey);
        return changed;
    }

    public boolean isAlly(String otherFactionKey) {
        if (plugin == null || !plugin.ALLY_CHAT_ENABLED || !plugin.ENEMY_SYSTEM_ENABLED) return false;
        return allyFactionKeys.contains(otherFactionKey.toLowerCase());
    }
    public void addAlly(String otherFactionKey) {
        if (plugin == null || !plugin.ALLY_CHAT_ENABLED || !plugin.ENEMY_SYSTEM_ENABLED) return;
        String keyLC = otherFactionKey.toLowerCase();
        if (isEnemy(keyLC)) return;
        allyFactionKeys.add(keyLC);
        if (plugin != null) plugin.updateFactionActivity(this.nameKey);
    }
    public boolean removeAlly(String otherFactionKey) {
        if (plugin == null || !plugin.ALLY_CHAT_ENABLED || !plugin.ENEMY_SYSTEM_ENABLED) return false;
        boolean removed = allyFactionKeys.remove(otherFactionKey.toLowerCase());
        if (removed && plugin != null) plugin.updateFactionActivity(this.nameKey);
        return removed;
    }

    // --- Trusted Players ---
    public boolean isTrusted(UUID playerUUID) { return trustedPlayers.contains(playerUUID); }
    public boolean addTrusted(UUID playerUUID) {
        if (isMemberOrHigher(playerUUID)) return false;
        boolean added = trustedPlayers.add(playerUUID);
        if (added && plugin != null) plugin.updateFactionActivity(this.nameKey);
        return added;
    }
    public boolean removeTrusted(UUID playerUUID) {
        boolean removed = trustedPlayers.remove(playerUUID);
        if (removed && plugin != null) plugin.updateFactionActivity(this.nameKey);
        return removed;
    }

    // --- Vault ---
    public void setVaultContents(ItemStack[] items) {
        if (plugin == null || !plugin.VAULT_SYSTEM_ENABLED) return;
        if (this.vault == null) {
            this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + originalName + " Vault");
        }
        ItemStack[] contentsToSet = new ItemStack[Math.min(items.length, VAULT_SIZE)];
        System.arraycopy(items, 0, contentsToSet, 0, contentsToSet.length);
        this.vault.setContents(contentsToSet);
    }

    public ItemStack[] getVaultContentsForSave() {
        if (plugin == null || !plugin.VAULT_SYSTEM_ENABLED || this.vault == null) return new ItemStack[VAULT_SIZE];
        ItemStack[] contents = new ItemStack[VAULT_SIZE];
        for (int i = 0; i < VAULT_SIZE && i < this.vault.getSize(); i++) {
            contents[i] = this.vault.getItem(i);
        }
        return contents;
    }

    // --- Outposts ---
    public boolean addOutpost(Outpost outpost) {
        if (plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED) return false;
        if (plugin.MAX_OUTPOSTS_PER_FACTION > 0 && outposts.size() >= plugin.MAX_OUTPOSTS_PER_FACTION) {
            return false;
        }
        if (!outposts.contains(outpost)) {
            outposts.add(outpost);
            outpost.setOutpostID(outposts.size());
            allClaimedChunks.addAll(outpost.getOutpostSpecificClaims());
            if (plugin != null) plugin.updateFactionActivity(this.nameKey);
            return true;
        }
        return false;
    }

    public boolean removeOutpostAndItsClaims(Outpost outpostToRemove) {
        if (plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED || outpostToRemove == null || !outposts.contains(outpostToRemove)) return false;

        if (plugin != null) {
            for (ChunkWrapper cw : new HashSet<>(outpostToRemove.getOutpostSpecificClaims())) {
                allClaimedChunks.remove(cw);
                plugin.getClaimedChunksMapInternal().remove(cw);
            }
        } else {
            allClaimedChunks.removeAll(outpostToRemove.getOutpostSpecificClaims());
        }

        boolean removed = outposts.remove(outpostToRemove);
        if (removed) {
            for(int i=0; i<outposts.size(); i++){
                outposts.get(i).setOutpostID(i+1);
            }
            if (plugin != null) plugin.updateFactionActivity(this.nameKey);
        }
        return removed;
    }

    @Nullable
    public Outpost getOutpostById(int id) {
        if (plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED || id <= 0 || id > outposts.size()) return null;
        return outposts.get(id - 1);
    }

    @Nullable
    public Outpost getOutpostBySpawnChunk(ChunkWrapper chunk) {
        if (plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED) return null;
        for (Outpost outpost : outposts) {
            if (outpost.getSpawnChunk().equals(chunk)) {
                return outpost;
            }
        }
        return null;
    }

    public boolean isOutpostChunk(ChunkWrapper chunk) {
        if (plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED) return false;
        for (Outpost outpost : outposts) {
            if (outpost.getSpawnChunk().equals(chunk)) {
                return true;
            }
        }
        return false;
    }

    public boolean isChunkInOutpostTerritory(ChunkWrapper chunk) {
        if (plugin == null || !plugin.OUTPOST_SYSTEM_ENABLED) return false;
        for (Outpost outpost : outposts) {
            if (outpost.containsClaim(chunk)) {
                return true;
            }
        }
        return false;
    }

    public void setLastOnlineTime(long lastOnlineTime) { this.lastOnlineTime = lastOnlineTime; }

    public void broadcastMessage(String message, @Nullable UUID excludePlayerUUID) {
        if (message == null || message.isEmpty()) return;
        for (UUID memberUUID : members.keySet()) {
            if (excludePlayerUUID != null && memberUUID.equals(excludePlayerUUID)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Faction faction = (Faction) o;
        return nameKey.equals(faction.nameKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nameKey);
    }

    public int getMaxClaimsCalculated(GFactionsPlugin pluginInstance) {
        GFactionsPlugin currentPlugin = (this.plugin != null) ? this.plugin : pluginInstance;
        if (currentPlugin == null) return Integer.MAX_VALUE;

        if (this.claimLimitOverride != -1) {
            return this.claimLimitOverride == 0 ? Integer.MAX_VALUE : this.claimLimitOverride;
        }

        if (currentPlugin.BASE_CLAIM_LIMIT <= 0) {
            return Integer.MAX_VALUE;
        }

        int calculatedClaims = currentPlugin.BASE_CLAIM_LIMIT + (Math.max(0, members.size() - 1) * currentPlugin.CLAIMS_PER_MEMBER_BONUS);

        if (currentPlugin.MAX_CLAIM_LIMIT_ABSOLUTE > 0) {
            return Math.min(calculatedClaims, currentPlugin.MAX_CLAIM_LIMIT_ABSOLUTE);
        }

        return calculatedClaims;
    }
}
