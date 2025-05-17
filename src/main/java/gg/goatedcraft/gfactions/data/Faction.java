package gg.goatedcraft.gfactions.data;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player; // Added import for Player
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation") // For OfflinePlayer methods
public class Faction {
    private final String originalName;
    private final String nameKey;
    private UUID ownerUUID;
    private final Map<UUID, FactionRank> members; // Should be Concurrent for thread safety if modified by async tasks
    private final Set<ChunkWrapper> allClaimedChunks; // Should be Concurrent for thread safety
    private Location homeLocation;
    private ChunkWrapper homeChunk;

    private final List<Outpost> outposts; // If modified concurrently, consider CopyOnWriteArrayList or synchronized access

    private int currentPower;
    private final Set<String> enemyFactionKeys; // ConcurrentHashMap.newKeySet()
    private final Set<String> allyFactionKeys;  // ConcurrentHashMap.newKeySet()
    private final Map<String, Long> enemyDeclareTimestamps; // ConcurrentHashMap
    private final Set<UUID> trustedPlayers; // ConcurrentHashMap.newKeySet()
    private Inventory vault; // Bukkit inventories are generally not thread-safe for modification from async tasks
    public static final int VAULT_SIZE = 27; // Example size, 3 rows

    private transient GFactionsPlugin plugin; // Marked transient for GSON if it were used, good practice

    public Faction(String name, UUID ownerUUID, @Nullable Location initialHomeLocation, GFactionsPlugin pluginInstance) {
        this.originalName = name;
        this.nameKey = name.toLowerCase();
        this.ownerUUID = ownerUUID;
        this.plugin = pluginInstance; // Store plugin reference

        // Initialize collections for concurrency if necessary, though many Bukkit operations are main-thread only
        this.members = new ConcurrentHashMap<>();
        this.members.put(ownerUUID, FactionRank.OWNER);

        this.allClaimedChunks = ConcurrentHashMap.newKeySet(); // Use a concurrent set
        this.outposts = new ArrayList<>(); // If modified by multiple threads, use CopyOnWriteArrayList or synchronized blocks

        if (initialHomeLocation != null && initialHomeLocation.getWorld() != null) {
            setHomeLocation(initialHomeLocation); // This will also claim the home chunk
        } else if (pluginInstance != null) { // Check pluginInstance, not this.plugin yet
            pluginInstance.getLogger().warning("Faction " + name + " created with null initialHomeLocation or world. Home needs to be set.");
        }


        this.enemyFactionKeys = ConcurrentHashMap.newKeySet();
        this.allyFactionKeys = ConcurrentHashMap.newKeySet();
        this.enemyDeclareTimestamps = new ConcurrentHashMap<>();
        this.trustedPlayers = ConcurrentHashMap.newKeySet();

        this.currentPower = (this.plugin != null) ? this.plugin.POWER_INITIAL : 100; // Use plugin reference if available
        this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + this.originalName + " Vault");
        updatePowerOnMemberChangeAndLoad(); // Initial power calculation
    }

    // Call this after deserialization or if the plugin instance wasn't available at construction
    public void lateInitPluginReference(GFactionsPlugin pluginInstance) {
        if (this.plugin == null) {
            this.plugin = pluginInstance;
            // If currentPower was default and plugin has a different initial, update it
            if (this.currentPower == 100 && pluginInstance != null && pluginInstance.POWER_INITIAL != 100 && members.size() == 1) {
                this.currentPower = pluginInstance.POWER_INITIAL;
            }
            // Re-validate home location world if it was null
            if (this.homeLocation != null && this.homeLocation.getWorld() == null && this.homeChunk != null) {
                World world = Bukkit.getWorld(this.homeChunk.getWorldName());
                if (world != null) {
                    this.homeLocation.setWorld(world);
                } else if (plugin != null) {
                    plugin.getLogger().warning("Could not reload world for home location of faction " + getName() + ": " + this.homeChunk.getWorldName());
                }
            }
            // Re-validate outpost locations and assign IDs
            for(int i = 0; i < outposts.size(); i++){
                Outpost outpost = outposts.get(i);
                outpost.setOutpostID(i + 1); // Ensure IDs are sequential from 1
                Location outpostSpawn = outpost.getOutpostSpawnLocation(); // This re-validates world
                if (outpostSpawn == null && plugin != null) {
                    plugin.getLogger().warning("Outpost " + outpost.getOutpostID() + " for faction " + getName() + " has an invalid spawn location (world not loaded).");
                }
            }
            updatePowerOnMemberChangeAndLoad(); // Recalculate power based on loaded members
        }
    }

    // --- Getters ---
    public String getName() { return originalName; }
    public String getNameKey() { return nameKey; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public Map<UUID, FactionRank> getMembers() { return Collections.unmodifiableMap(members); }
    public Set<UUID> getMemberUUIDsOnly() { return Collections.unmodifiableSet(members.keySet()); }
    @Nullable public FactionRank getRank(UUID playerUUID) { return members.get(playerUUID); } // Can be null

    public Set<ChunkWrapper> getClaimedChunks() {
        return Collections.unmodifiableSet(allClaimedChunks);
    }
    // Used internally for modification during loading or direct faction operations
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
                return null; // World is not loaded, cannot provide valid location
            }
        }
        return homeLocation;
    }
    @Nullable public ChunkWrapper getHomeChunk() { return homeChunk; }

    public int getCurrentPower() { return currentPower; }
    public int getMaxPower() {
        if (plugin == null) return Math.max(1, members.size()) * 100; // Fallback if plugin not set
        return Math.max(1, members.size()) * plugin.POWER_PER_MEMBER_BONUS;
    }

    public Set<String> getEnemyFactionKeys() { return Collections.unmodifiableSet(enemyFactionKeys); }
    public Set<String> getAllyFactionKeys() { return Collections.unmodifiableSet(allyFactionKeys); }
    public Map<String, Long> getEnemyDeclareTimestamps() { return Collections.unmodifiableMap(enemyDeclareTimestamps); }
    public Set<UUID> getTrustedPlayers() { return Collections.unmodifiableSet(trustedPlayers); }
    public Inventory getVault() { return vault; }
    public List<Outpost> getOutposts() { return Collections.unmodifiableList(outposts); }


    // --- Setters & Modifiers ---
    public void setOwnerUUID(UUID newOwnerUUID) {
        if (members.containsKey(this.ownerUUID) && !this.ownerUUID.equals(newOwnerUUID)) {
            members.put(this.ownerUUID, FactionRank.ADMIN); // Demote old owner
        }
        this.ownerUUID = newOwnerUUID;
        members.put(newOwnerUUID, FactionRank.OWNER); // Set new owner
    }

    public void setHomeLocation(@Nullable Location newHomeLocation) {
        this.homeLocation = newHomeLocation;
        if (newHomeLocation != null && newHomeLocation.getWorld() != null) {
            this.homeChunk = new ChunkWrapper(newHomeLocation.getWorld().getName(), newHomeLocation.getChunk().getX(), newHomeLocation.getChunk().getZ());
            this.allClaimedChunks.add(this.homeChunk); // Ensure home chunk is always claimed
        } else {
            this.homeChunk = null; // Invalidate home chunk if location is null or world is null
            if (plugin != null && newHomeLocation != null) { // Log only if an attempt was made with an invalid location
                plugin.getLogger().warning("Attempted to set home for " + getName() + " with a null world. Home chunk invalidated.");
            }
        }
    }

    public void relocateHomeToRandomClaim() {
        if (plugin == null) return;
        Set<ChunkWrapper> mainTerritoryClaims = new HashSet<>(allClaimedChunks);
        if (homeChunk != null) { // Exclude current home if it exists
            mainTerritoryClaims.remove(homeChunk);
        }
        for (Outpost outpost : outposts) { // Exclude all outpost-specific claims
            mainTerritoryClaims.removeAll(outpost.getOutpostSpecificClaims());
        }

        if (mainTerritoryClaims.isEmpty()) { // No main territory left
            if (!outposts.isEmpty()) { // Try to convert an outpost
                Outpost firstOutpost = outposts.get(0); // Get the first one
                Location outpostSpawn = firstOutpost.getOutpostSpawnLocation();
                if (outpostSpawn != null && outpostSpawn.getWorld() != null) {
                    setHomeLocation(outpostSpawn); // New home is the outpost spawn
                    allClaimedChunks.removeAll(firstOutpost.getOutpostSpecificClaims()); // Remove old outpost claims from global list
                    outposts.remove(firstOutpost); // Remove the outpost object
                    // Re-ID remaining outposts
                    for(int i=0; i<outposts.size(); i++){ outposts.get(i).setOutpostID(i+1); }

                    plugin.getLogger().info("Faction " + getName() + " home relocated to former outpost at " + firstOutpost.getSpawnChunk().toStringShort());
                    broadcastMessage(ChatColor.YELLOW + "Your faction's home was lost. Outpost #" + firstOutpost.getOutpostID() + " has become your new faction home.", null);
                    return;
                }
            }
            // No suitable land found at all
            plugin.getLogger().warning("Faction " + getName() + " lost its home and has no other claims to relocate to.");
            this.homeLocation = null;
            this.homeChunk = null;
            broadcastMessage(ChatColor.RED + "" + ChatColor.BOLD + "Your faction's home was lost and no land was found to relocate it! You MUST set a new home with /f sethome.", null);
            return;
        }

        // Relocate to a random chunk in the main territory
        List<ChunkWrapper> possibleRelocationChunks = new ArrayList<>(mainTerritoryClaims);
        Collections.shuffle(possibleRelocationChunks);
        ChunkWrapper newHomeChunkWrapper = possibleRelocationChunks.get(0);
        World world = Bukkit.getWorld(newHomeChunkWrapper.getWorldName());
        if (world == null) {
            if(plugin != null) plugin.getLogger().severe("Failed to relocate home for " + getName() + ": World " + newHomeChunkWrapper.getWorldName() + " not loaded for new home chunk.");
            broadcastMessage(ChatColor.RED + "Critical error: Could not relocate faction home due to an unloaded world. Contact an admin.", null);
            return;
        }
        Chunk newHomeSpigotChunk = world.getChunkAt(newHomeChunkWrapper.getX(), newHomeChunkWrapper.getZ()); // Ensure chunk is loaded by Bukkit
        Location newHomeLoc = findSafeSurfaceLocation(newHomeSpigotChunk); // Find a safe spot in the chunk

        setHomeLocation(newHomeLoc); // This sets homeLocation and homeChunk, and adds to allClaimedChunks
        plugin.getLogger().info("Faction " + getName() + "'s home relocated to chunk: " + newHomeChunkWrapper.toStringShort());
        broadcastMessage(ChatColor.RED + "Your faction's home chunk was lost! Your home has been randomly relocated within your territory to " + newHomeChunkWrapper.toStringShort() + ".", null);
    }

    @Nullable
    private Location findSafeSurfaceLocation(Chunk chunk) {
        World world = chunk.getWorld();
        Random random = new Random();
        for (int i = 0; i < 10; i++) { // Try 10 random spots
            int x = chunk.getX() * 16 + random.nextInt(16);
            int z = chunk.getZ() * 16 + random.nextInt(16);
            // Get the highest solid block, then go one above.
            Block highestBlock = world.getHighestBlockAt(x,z);
            // Ensure highestBlock itself is not something like water or lava if possible, or that it's solid.
            // For simplicity, we'll just go one above the block returned by getHighestBlockAt.
            int y = highestBlock.getY() + 1;

            Location loc = new Location(world, x + 0.5, y, z + 0.5); // Center of block
            Block blockAtFeet = world.getBlockAt(loc);
            Block blockAtHead = world.getBlockAt(loc.clone().add(0,1,0));
            Block blockBelowFeet = world.getBlockAt(loc.clone().add(0,-1,0));

            if (blockBelowFeet.getType().isSolid() && !blockAtFeet.getType().isSolid() && !blockAtHead.getType().isSolid()) {
                return loc; // Safe spot found
            }
        }
        // Fallback to chunk center, highest Y
        int cx = chunk.getX() * 16 + 8;
        int cz = chunk.getZ() * 16 + 8;
        return new Location(world, cx + 0.5, world.getHighestBlockYAt(cx, cz) + 1.0, cz + 0.5);
    }


    public void setCurrentPower(int power) {
        this.currentPower = Math.max(0, Math.min(power, getMaxPower()));
    }
    public void addPower(int amount) {
        setCurrentPower(this.currentPower + amount);
    }
    public void removePower(int amount) {
        setCurrentPower(this.currentPower - amount);
    }

    public void updatePowerOnMemberChangeAndLoad() {
        int newMaxPower = getMaxPower();
        // If it's a brand new faction (only owner) and power was default, set to configured initial
        if (plugin != null && members.size() == 1 && members.containsKey(ownerUUID) && members.get(ownerUUID) == FactionRank.OWNER) {
            this.currentPower = Math.min(plugin.POWER_INITIAL, newMaxPower);
        } else { // Otherwise, just ensure current power doesn't exceed new max
            this.currentPower = Math.min(this.currentPower, newMaxPower);
        }
        this.currentPower = Math.max(0, this.currentPower); // Ensure power is not negative
    }

    public boolean addMember(UUID memberUUID, FactionRank rank) {
        if (members.containsKey(memberUUID)) return false; // Already a member
        if (plugin != null && plugin.MAX_MEMBERS_PER_FACTION > 0 && members.size() >= (plugin.MAX_MEMBERS_PER_FACTION)) {
            // Faction is full (owner counts as 1, so max_members is for additional members)
            // If MAX_MEMBERS_PER_FACTION is 10, total size can be 11 (1 owner + 10 members)
            // So check against MAX_MEMBERS_PER_FACTION + 1 for total size limit.
            // Or, if MAX_MEMBERS_PER_FACTION is total including owner, then check against MAX_MEMBERS_PER_FACTION.
            // Assuming MAX_MEMBERS_PER_FACTION is total size limit for now.
            if (members.size() >= plugin.MAX_MEMBERS_PER_FACTION) return false;
        }
        members.put(memberUUID, rank);
        updatePowerOnMemberChangeAndLoad();
        return true;
    }

    public boolean removeMember(UUID memberUUID) {
        if (memberUUID.equals(ownerUUID)) return false; // Cannot remove owner this way
        if (members.remove(memberUUID) != null) {
            updatePowerOnMemberChangeAndLoad();
            return true;
        }
        return false;
    }

    public boolean promotePlayer(UUID memberUUID) {
        if (!members.containsKey(memberUUID) || members.get(memberUUID) == FactionRank.OWNER || members.get(memberUUID) == FactionRank.ADMIN) {
            return false; // Not a member, or already owner/admin
        }
        members.put(memberUUID, FactionRank.ADMIN);
        return true;
    }

    public boolean demotePlayer(UUID memberUUID) {
        if (!members.containsKey(memberUUID) || members.get(memberUUID) != FactionRank.ADMIN) {
            return false; // Not a member or not an admin
        }
        members.put(memberUUID, FactionRank.MEMBER);
        return true;
    }

    public void addClaim(ChunkWrapper chunkWrapper, boolean isOutpostClaimHint, @Nullable Outpost specificOutpost) {
        this.allClaimedChunks.add(chunkWrapper);
        if (isOutpostClaimHint && specificOutpost != null && outposts.contains(specificOutpost)) {
            specificOutpost.addClaim(chunkWrapper); // Add to outpost's specific list
        }
        // Check for merge only if it's not an outpost-specific claim being added to an existing outpost.
        // If it's a new outpost being created, or a general claim, then check for merge.
        if (!isOutpostClaimHint || specificOutpost == null) {
            checkForAndHandleTerritoryMerge();
        }
    }
    public void addClaim(ChunkWrapper chunkWrapper) {
        addClaim(chunkWrapper, false, null);
    }

    public void removeClaim(ChunkWrapper chunkWrapper) {
        boolean removed = this.allClaimedChunks.remove(chunkWrapper);
        if (!removed) return; // Not claimed by this faction overall

        Outpost associatedOutpost = null;
        for (Outpost outpost : outposts) {
            if (outpost.containsClaim(chunkWrapper)) {
                outpost.removeClaim(chunkWrapper);
                associatedOutpost = outpost;
                break;
            }
        }

        // Check if home chunk was lost
        if (plugin != null && chunkWrapper.equals(this.homeChunk)) {
            plugin.getLogger().info("Home chunk " + chunkWrapper.toStringShort() + " for faction " + getName() + " is being lost. Relocating home...");
            relocateHomeToRandomClaim(); // This will find a new home or handle no land
        }
        // Check if an outpost's spawn chunk was lost and if it should be disbanded
        else if (associatedOutpost != null && chunkWrapper.equals(associatedOutpost.getSpawnChunk())) {
            if (associatedOutpost.getOutpostSpecificClaims().isEmpty()) { // Last claim of this outpost
                outposts.remove(associatedOutpost);
                // Re-ID remaining outposts
                for(int i=0; i<outposts.size(); i++){ outposts.get(i).setOutpostID(i+1); }
                if (plugin != null) {
                    plugin.getLogger().info("Outpost #" + associatedOutpost.getOutpostID() + " for " + getName() + " disbanded (last claim lost).");
                    broadcastMessage(ChatColor.YELLOW + "Your outpost #" + associatedOutpost.getOutpostID() + " at " + chunkWrapper.toStringShort() + " was disbanded as its last claim was lost.", null);
                }
            } else { // Outpost lost its spawn but has other claims
                if (plugin != null) {
                    plugin.getLogger().warning("Outpost #" + associatedOutpost.getOutpostID() + " for " + getName() + " lost its spawn chunk but still has claims. It may be inaccessible until a new home is set for it or it merges.");
                    broadcastMessage(ChatColor.RED + "Your outpost #" + associatedOutpost.getOutpostID() + " at " + chunkWrapper.toStringShort() + " lost its spawn chunk! It may be inaccessible. Consider deleting or re-establishing its home.", null);
                }
            }
        }
        checkForAndHandleTerritoryMerge(); // Check if unclaiming caused an outpost to become part of main land (less likely but possible if unclaiming a buffer)
    }


    public boolean isConnectedToAnySpawnPoint(ChunkWrapper targetChunk) {
        // This method checks if a target chunk is part of a contiguous territory
        // that includes either the main faction home or any outpost spawn.
        // Useful for commands like /f unclaim to prevent orphaning land.
        if (allClaimedChunks.isEmpty()) return false;
        if (targetChunk.equals(homeChunk)) return true; // Target is the home chunk itself
        for (Outpost outpost : outposts) {
            if (targetChunk.equals(outpost.getSpawnChunk())) return true; // Target is an outpost spawn itself
        }

        Queue<ChunkWrapper> queue = new LinkedList<>();
        Set<ChunkWrapper> visited = new HashSet<>();

        // Start BFS from all spawn points (home + outpost spawns)
        if (homeChunk != null && allClaimedChunks.contains(homeChunk)) {
            queue.offer(homeChunk);
            visited.add(homeChunk);
        }
        for (Outpost outpost : outposts) {
            ChunkWrapper outpostSpawn = outpost.getSpawnChunk();
            // Ensure outpost spawn is actually claimed by this faction (should always be true if data is consistent)
            if (allClaimedChunks.contains(outpostSpawn) && !visited.contains(outpostSpawn)) {
                queue.offer(outpostSpawn);
                visited.add(outpostSpawn);
            }
        }

        if (queue.isEmpty() && !allClaimedChunks.isEmpty()) {
            // This case means there are claims but no valid spawn points (home or outpost)
            // which indicates a data inconsistency or a state where all spawns were lost.
            // In such a scenario, any claim could be considered "connected" to itself if it's the only one.
            // However, for unclaiming, we usually want to ensure it's connected to a *functional* part.
            // For now, if no spawn points, assume not connected to a spawn.
            return false;
        }


        while (!queue.isEmpty()) {
            ChunkWrapper current = queue.poll();
            if (current.equals(targetChunk)) return true; // Found the target chunk

            // Check neighbors
            int[] dX = {0, 0, 1, -1}; // dx for N, S, E, W
            int[] dZ = {1, -1, 0, 0}; // dz for N, S, E, W

            for (int i = 0; i < 4; i++) {
                ChunkWrapper neighbor = new ChunkWrapper(current.getWorldName(), current.getX() + dX[i], current.getZ() + dZ[i]);
                if (allClaimedChunks.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        return false; // Target chunk not reached from any spawn point
    }


    public boolean isChunkAdjacentToExistingClaim(ChunkWrapper targetChunk) {
        if (allClaimedChunks.isEmpty()) return true; // First claim is always "adjacent" to nothing

        int[] dX = {0, 0, 1, -1};
        int[] dZ = {1, -1, 0, 0};

        for (ChunkWrapper claimed : allClaimedChunks) {
            if (!claimed.getWorldName().equals(targetChunk.getWorldName())) continue; // Must be in the same world
            for (int i = 0; i < 4; i++) {
                if (claimed.getX() + dX[i] == targetChunk.getX() && claimed.getZ() + dZ[i] == targetChunk.getZ()) {
                    return true; // Found an adjacent claimed chunk
                }
            }
        }
        return false; // No adjacent chunk found
    }

    @Nullable
    public Outpost getAttachmentTerritory(ChunkWrapper newClaim) {
        // If adjacent to main home territory, it's not attaching to an outpost (null)
        if (homeChunk != null && isChunkAdjacentTo(newClaim, getClaimsConnectedTo(homeChunk))) {
            return null;
        }
        // Check if adjacent to any existing outpost's territory
        for (Outpost outpost : outposts) {
            if (isChunkAdjacentTo(newClaim, outpost.getOutpostSpecificClaims())) {
                return outpost; // Attaches to this outpost
            }
        }
        return null; // Not adjacent to any existing territory (should be handled by isChunkAdjacentToExistingClaim for first claim)
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
        if (startChunk == null || !allClaimedChunks.contains(startChunk)) return connected; // Start chunk must be valid and claimed

        Queue<ChunkWrapper> queue = new LinkedList<>();
        Set<ChunkWrapper> visited = new HashSet<>(); // To avoid reprocessing and loops

        queue.offer(startChunk);
        visited.add(startChunk);
        connected.add(startChunk);

        while(!queue.isEmpty()){
            ChunkWrapper current = queue.poll();
            int[] dX = {0, 0, 1, -1};
            int[] dZ = {1, -1, 0, 0};
            for(int i=0; i < 4; i++){
                ChunkWrapper neighbor = new ChunkWrapper(current.getWorldName(), current.getX() + dX[i], current.getZ() + dZ[i]);
                if(allClaimedChunks.contains(neighbor) && !visited.contains(neighbor)){ // Must be claimed by this faction and not yet visited
                    visited.add(neighbor);
                    connected.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        return connected;
    }

    public void checkForAndHandleTerritoryMerge() {
        if (homeChunk == null || plugin == null) return; // Need a home and plugin instance

        Set<ChunkWrapper> mainHomeTerritory = getClaimsConnectedTo(homeChunk);
        // if (mainHomeTerritory.isEmpty() && homeChunk != null) { // Should not be empty if homeChunk is valid
        //     mainHomeTerritory.add(homeChunk); // Ensure home chunk itself is part of its territory
        // }

        List<Outpost> outpostsToRemove = new ArrayList<>();
        for (Outpost outpost : new ArrayList<>(outposts)) { // Iterate on a copy to allow modification
            boolean merged = false;
            // Check if any part of the outpost's specific claims is now adjacent to the main home territory
            for (ChunkWrapper outpostClaim : new HashSet<>(outpost.getOutpostSpecificClaims())) { // Iterate on copy of claims
                if (isChunkAdjacentTo(outpostClaim, mainHomeTerritory)) {
                    merged = true;
                    break;
                }
            }

            if (merged) {
                plugin.getLogger().info("Outpost " + (outpost.getOutpostID() > 0 ? "#"+outpost.getOutpostID() : outpost.getSpawnChunk().toStringShort()) + " for faction " + getName() + " has merged with main territory. Disbanding outpost and merging claims.");
                broadcastMessage(ChatColor.GREEN + "Your outpost at " + outpost.getSpawnChunk().toStringShort() + " has connected to your main faction territory and has been merged!", null);

                // The claims of the outpost are already in allClaimedChunks.
                // We just need to remove the Outpost object itself.
                outpostsToRemove.add(outpost);
            }
        }
        if (!outpostsToRemove.isEmpty()) {
            outposts.removeAll(outpostsToRemove);
            // Re-ID remaining outposts
            for(int i=0; i<outposts.size(); i++){
                outposts.get(i).setOutpostID(i+1);
            }
        }
    }


    // --- Membership & Roles ---
    public boolean isOwner(UUID playerUUID) { return playerUUID.equals(this.ownerUUID); }
    public boolean isAdmin(UUID playerUUID) { return members.getOrDefault(playerUUID, FactionRank.MEMBER).ordinal() >= FactionRank.ADMIN.ordinal(); }
    public boolean isMemberOrHigher(UUID playerUUID) { return members.containsKey(playerUUID); }
    public int getTotalSize() { return members.size(); }

    // --- Relations ---
    public boolean isEnemy(String otherFactionKey) { return enemyFactionKeys.contains(otherFactionKey.toLowerCase()); }
    public void addEnemy(String otherFactionKey, long timestamp) {
        String keyLC = otherFactionKey.toLowerCase();
        removeAlly(keyLC); // Cannot be ally and enemy
        enemyFactionKeys.add(keyLC);
        enemyDeclareTimestamps.put(keyLC, timestamp);
    }
    public boolean removeEnemy(String otherFactionKey) {
        String keyLC = otherFactionKey.toLowerCase();
        boolean changed = enemyFactionKeys.remove(keyLC);
        if (enemyDeclareTimestamps.remove(keyLC) != null) { // Remove timestamp too
            changed = true;
        }
        return changed;
    }

    public boolean isAlly(String otherFactionKey) { return allyFactionKeys.contains(otherFactionKey.toLowerCase()); }
    public void addAlly(String otherFactionKey) {
        String keyLC = otherFactionKey.toLowerCase();
        if (isEnemy(keyLC)) return; // Cannot be enemy and ally
        allyFactionKeys.add(keyLC);
    }
    public boolean removeAlly(String otherFactionKey) {
        return allyFactionKeys.remove(otherFactionKey.toLowerCase());
    }

    // --- Trusted Players ---
    public boolean isTrusted(UUID playerUUID) { return trustedPlayers.contains(playerUUID); }
    public boolean addTrusted(UUID playerUUID) {
        if (isMemberOrHigher(playerUUID)) return false; // Members are implicitly trusted
        return trustedPlayers.add(playerUUID);
    }
    public boolean removeTrusted(UUID playerUUID) { return trustedPlayers.remove(playerUUID); }

    // --- Vault ---
    public void setVaultContents(ItemStack[] items) {
        if (this.vault == null) { // Should be initialized in constructor
            this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + originalName + " Vault");
        }
        // Ensure items array is not too large for vault
        ItemStack[] contentsToSet = new ItemStack[Math.min(items.length, VAULT_SIZE)];
        System.arraycopy(items, 0, contentsToSet, 0, contentsToSet.length);
        this.vault.setContents(contentsToSet);
    }

    public ItemStack[] getVaultContentsForSave() {
        // Ensure vault is not null and return a copy of its contents up to VAULT_SIZE
        if (this.vault == null) return new ItemStack[VAULT_SIZE]; // Return empty array of correct size

        ItemStack[] contents = new ItemStack[VAULT_SIZE];
        for (int i = 0; i < VAULT_SIZE && i < this.vault.getSize(); i++) {
            contents[i] = this.vault.getItem(i); // getItem handles nulls correctly
        }
        return contents;
    }

    // --- Outposts ---
    public boolean addOutpost(Outpost outpost) {
        if (plugin != null && plugin.MAX_OUTPOSTS_PER_FACTION > 0 && outposts.size() >= plugin.MAX_OUTPOSTS_PER_FACTION) {
            return false; // Max outposts reached
        }
        if (!outposts.contains(outpost)) { // Prevent duplicates if equals/hashCode is based on location
            outposts.add(outpost);
            outpost.setOutpostID(outposts.size()); // Assign ID (1-based index)

            // Ensure all claims of this new outpost (especially its spawn chunk) are in the faction's main claim list
            allClaimedChunks.addAll(outpost.getOutpostSpecificClaims());
            return true;
        }
        return false; // Already exists or other issue
    }

    public boolean removeOutpostAndItsClaims(Outpost outpostToRemove) {
        if (outpostToRemove == null || !outposts.contains(outpostToRemove)) return false;

        // Remove all specific claims of this outpost from the faction's global claim list
        // and from the plugin's global claimedChunks map
        if (plugin != null) {
            for (ChunkWrapper cw : new HashSet<>(outpostToRemove.getOutpostSpecificClaims())) { // Iterate copy
                allClaimedChunks.remove(cw);
                plugin.getClaimedChunksMap().remove(cw); // Remove from GFactionsPlugin's map
            }
        } else { // Fallback if plugin ref is null (should not happen in normal operation)
            allClaimedChunks.removeAll(outpostToRemove.getOutpostSpecificClaims());
        }

        boolean removed = outposts.remove(outpostToRemove);
        if (removed) {
            // Re-ID remaining outposts
            for(int i=0; i<outposts.size(); i++){
                outposts.get(i).setOutpostID(i+1);
            }
        }
        return removed;
    }

    @Nullable
    public Outpost getOutpostById(int id) {
        if (id <= 0 || id > outposts.size()) return null;
        return outposts.get(id - 1); // List is 0-indexed, IDs are 1-indexed
    }

    @Nullable
    public Outpost getOutpostBySpawnChunk(ChunkWrapper chunk) {
        for (Outpost outpost : outposts) {
            if (outpost.getSpawnChunk().equals(chunk)) {
                return outpost;
            }
        }
        return null;
    }

    public boolean isOutpostChunk(ChunkWrapper chunk) { // Checks if the chunk is the *spawn* of any outpost
        for (Outpost outpost : outposts) {
            if (outpost.getSpawnChunk().equals(chunk)) {
                return true;
            }
        }
        return false;
    }

    public boolean isChunkInOutpostTerritory(ChunkWrapper chunk) { // Checks if chunk is part of *any* outpost's claimed land
        for (Outpost outpost : outposts) {
            if (outpost.containsClaim(chunk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Broadcasts a message to all online members of the faction.
     *
     * @param message The message to send.
     * @param excludePlayerUUID The UUID of a player to exclude from the broadcast, or null.
     */
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
        return nameKey.equals(faction.nameKey); // Factions are unique by their nameKey
    }

    @Override
    public int hashCode() {
        return Objects.hash(nameKey);
    }
}
