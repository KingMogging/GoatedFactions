package gg.goatedcraft.gfactions.data;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class Faction {
    private final String originalName;
    private final String nameKey;
    private UUID ownerUUID;
    private final Map<UUID, FactionRank> members;
    private final Set<ChunkWrapper> claimedChunks;
    private Location homeLocation; // This is the main spawnblock location
    private ChunkWrapper spawnBlockChunk; // Chunk containing the main homeLocation (spawnblock)

    private final List<Outpost> outposts;

    private int currentPower;
    private final Set<String> enemyFactionKeys;
    private final Set<String> allyFactionKeys;
    private final Map<String, Long> enemyDeclareTimestamps;
    private final Set<UUID> trustedPlayers;
    private Inventory vault;
    public static final int VAULT_SIZE = 27;

    private transient GFactionsPlugin plugin;

    public Faction(String name, UUID ownerUUID, Location initialHomeLocation, GFactionsPlugin pluginInstance) {
        this.originalName = name;
        this.nameKey = name.toLowerCase();
        this.ownerUUID = ownerUUID;
        this.plugin = pluginInstance;

        this.members = new ConcurrentHashMap<>();
        this.members.put(ownerUUID, FactionRank.OWNER);

        this.claimedChunks = new HashSet<>();
        this.outposts = new ArrayList<>(); // Initialize outposts list

        if (initialHomeLocation != null && initialHomeLocation.getWorld() != null) {
            setHomeLocation(initialHomeLocation); // This will also set spawnBlockChunk
        } else if (pluginInstance != null) {
            pluginInstance.getLogger().warning("Faction " + name + " created with null initialHomeLocation or world.");
        }


        this.enemyFactionKeys = ConcurrentHashMap.newKeySet();
        this.allyFactionKeys = ConcurrentHashMap.newKeySet();
        this.enemyDeclareTimestamps = new ConcurrentHashMap<>();
        this.trustedPlayers = ConcurrentHashMap.newKeySet();
        this.currentPower = (this.plugin != null) ? this.plugin.POWER_INITIAL : 100;

        this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + this.originalName + " Vault");
        updatePowerOnMemberChangeAndLoad();
    }

    public void lateInitPluginReference(GFactionsPlugin pluginInstance) {
        if (this.plugin == null) {
            this.plugin = pluginInstance;
            if (this.currentPower == 100 && pluginInstance != null && pluginInstance.POWER_INITIAL != 100) {
                this.currentPower = pluginInstance.POWER_INITIAL;
            }
            // If homeLocation has a null world, try to re-fetch it
            if (this.homeLocation != null && this.homeLocation.getWorld() == null && this.spawnBlockChunk != null) {
                World world = Bukkit.getWorld(this.spawnBlockChunk.getWorldName());
                if (world != null) {
                    this.homeLocation = new Location(world, this.homeLocation.getX(), this.homeLocation.getY(), this.homeLocation.getZ(), this.homeLocation.getYaw(), this.homeLocation.getPitch());
                } else {
                    plugin.getLogger().warning("Could not reload world for home location of faction " + getName());
                }
            }
            for(Outpost outpost : outposts){ // Ensure outpost locations also get their worlds reloaded if needed
                if(outpost.getOutpostSpawnLocation() != null && outpost.getOutpostSpawnLocation().getWorld() == null){
                    World world = Bukkit.getWorld(outpost.getWorldName());
                    if(world != null){
                        outpost.setOutpostSpawnLocation(new Location(world, outpost.getOutpostSpawnLocation().getX(), outpost.getOutpostSpawnLocation().getY(), outpost.getOutpostSpawnLocation().getZ(), outpost.getOutpostSpawnLocation().getYaw(), outpost.getOutpostSpawnLocation().getPitch()));
                    }
                }
            }
            updatePowerOnMemberChangeAndLoad();
        }
    }

    // --- Getters ---
    public String getName() { return originalName; }
    public String getNameKey() { return nameKey; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public Map<UUID, FactionRank> getMembers() { return Collections.unmodifiableMap(members); }
    public Set<UUID> getMemberUUIDsOnly() { return Collections.unmodifiableSet(members.keySet()); }
    public FactionRank getRank(UUID playerUUID) { return members.getOrDefault(playerUUID, null); }
    public Set<ChunkWrapper> getClaimedChunks() { return Collections.unmodifiableSet(claimedChunks); }

    public Location getHomeLocation() {
        if (homeLocation != null && homeLocation.getWorld() == null && spawnBlockChunk != null) {
            World world = Bukkit.getWorld(spawnBlockChunk.getWorldName());
            if (world != null) {
                homeLocation.setWorld(world);
            } else {
                if(plugin != null) plugin.getLogger().severe("CRITICAL: World " + spawnBlockChunk.getWorldName() + " for faction " + getName() + "'s home is not loaded!");
                return null; // Or handle this more gracefully
            }
        }
        return homeLocation;
    }
    public ChunkWrapper getSpawnBlockChunk() { return spawnBlockChunk; }

    public int getCurrentPower() { return currentPower; }

    public int getMaxPower() {
        if (plugin == null) {
            return Math.max(1, members.size()) * 100;
        }
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
            members.put(this.ownerUUID, FactionRank.ADMIN);
        }
        this.ownerUUID = newOwnerUUID;
        members.put(newOwnerUUID, FactionRank.OWNER);
    }

    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
        if (homeLocation != null && homeLocation.getWorld() != null) {
            this.spawnBlockChunk = new ChunkWrapper(homeLocation.getWorld().getName(), homeLocation.getChunk().getX(), homeLocation.getChunk().getZ());
            // Ensure the spawnblock chunk itself is considered claimed
            if (!claimedChunks.contains(this.spawnBlockChunk)) {
                addClaim(this.spawnBlockChunk); // Silently add if not already, though create/sethome commands should handle explicit claiming
            }
        } else {
            this.spawnBlockChunk = null;
            if(plugin != null) plugin.getLogger().warning("Attempted to set home location with null world or location for faction: " + getName());
        }
    }

    public void relocateHomeToRandomClaim() {
        if (plugin == null) return;
        Set<ChunkWrapper> otherClaims = new HashSet<>(claimedChunks);
        if (spawnBlockChunk != null) {
            otherClaims.remove(spawnBlockChunk); // Don't relocate to the chunk that was just lost (if it was spawn)
        }

        if (otherClaims.isEmpty()) {
            plugin.getLogger().warning("Faction " + getName() + " lost its spawnblock chunk and has no other claims. Home cannot be relocated.");
            this.homeLocation = null;
            this.spawnBlockChunk = null;
            // Potentially disband faction or mark as "homeless"
            return;
        }

        List<ChunkWrapper> possibleRelocationChunks = new ArrayList<>(otherClaims);
        Collections.shuffle(possibleRelocationChunks);
        ChunkWrapper newHomeChunkWrapper = possibleRelocationChunks.get(0);

        World world = Bukkit.getWorld(newHomeChunkWrapper.getWorldName());
        if (world == null) {
            plugin.getLogger().severe("Failed to relocate home for " + getName() + ": World " + newHomeChunkWrapper.getWorldName() + " not loaded.");
            this.homeLocation = null; // Or try another chunk
            this.spawnBlockChunk = null;
            return;
        }

        Chunk newHomeChunk = world.getChunkAt(newHomeChunkWrapper.getX(), newHomeChunkWrapper.getZ());
        Location newHomeLoc = findSafeSurfaceLocation(newHomeChunk);

        if (newHomeLoc == null) {
            // Fallback if safe spot not found, try center of chunk at a fixed Y or another chunk
            newHomeLoc = new Location(world, newHomeChunkWrapper.getX() * 16 + 8, world.getHighestBlockYAt(newHomeChunkWrapper.getX() * 16 + 8, newHomeChunkWrapper.getZ() * 16 + 8) + 1, newHomeChunkWrapper.getZ() * 16 + 8);
            if(world.getBlockAt(newHomeLoc).getType().isSolid() || world.getBlockAt(newHomeLoc.clone().add(0,-1,0)).getType() == Material.AIR){ // basic safety check
                newHomeLoc = new Location(world, newHomeChunkWrapper.getX() * 16 + 8, 64, newHomeChunkWrapper.getZ() * 16 + 8); // absolute fallback
            }
        }

        setHomeLocation(newHomeLoc);
        plugin.getLogger().info("Faction " + getName() + "'s home (spawnblock) was relocated to chunk: " + newHomeChunkWrapper.toString());
        plugin.notifyFaction(this, ChatColor.RED + "Your faction's spawnblock chunk was overclaimed! Your home has been randomly relocated within your territory.", null);
    }

    private Location findSafeSurfaceLocation(Chunk chunk) {
        World world = chunk.getWorld();
        // Try a few random spots in the chunk for a safe surface location
        Random random = new Random();
        for (int i = 0; i < 10; i++) { // Try 10 times
            int x = chunk.getX() * 16 + random.nextInt(16);
            int z = chunk.getZ() * 16 + random.nextInt(16);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            // Basic safety check: non-solid block at feet, solid block below feet
            if (!world.getBlockAt(loc).getType().isSolid() &&
                    !world.getBlockAt(loc.clone().add(0,1,0)).getType().isSolid() && // space for head
                    world.getBlockAt(loc.clone().add(0,-1,0)).getType().isSolid()) {
                return loc;
            }
        }
        // Fallback: center of chunk, highest block
        int cx = chunk.getX() * 16 + 8;
        int cz = chunk.getZ() * 16 + 8;
        return new Location(world, cx + 0.5, world.getHighestBlockYAt(cx, cz) + 1.0, cz + 0.5);
    }


    public void setCurrentPower(int power) {
        this.currentPower = Math.max(0, Math.min(power, getMaxPower()));
    }

    public void addPower(int amount) {
        this.currentPower = Math.min(this.currentPower + amount, getMaxPower());
        this.currentPower = Math.max(0, this.currentPower);
    }
    public void removePower(int amount) {
        this.currentPower = Math.max(0, this.currentPower - amount);
    }

    public void updatePowerOnMemberChangeAndLoad() {
        int newMaxPower = getMaxPower();
        if (plugin != null && members.size() == 1 && members.containsKey(ownerUUID) && members.get(ownerUUID) == FactionRank.OWNER) {
            this.currentPower = Math.min(plugin.POWER_INITIAL, newMaxPower);
        } else {
            this.currentPower = Math.min(this.currentPower, newMaxPower);
        }
        this.currentPower = Math.max(0, this.currentPower);
    }

    public boolean addMember(UUID memberUUID, FactionRank rank) {
        if (members.containsKey(memberUUID)) return false;
        if (plugin != null && plugin.MAX_MEMBERS_PER_FACTION > 0 && members.size() >= (plugin.MAX_MEMBERS_PER_FACTION + 1)) return false;
        members.put(memberUUID, rank);
        updatePowerOnMemberChangeAndLoad();
        return true;
    }

    public boolean removeMember(UUID memberUUID) {
        if (memberUUID.equals(ownerUUID)) return false;
        if (members.remove(memberUUID) != null) {
            updatePowerOnMemberChangeAndLoad();
            return true;
        }
        return false;
    }

    public boolean promotePlayer(UUID memberUUID) {
        if (!members.containsKey(memberUUID) || members.get(memberUUID) == FactionRank.OWNER || members.get(memberUUID) == FactionRank.ADMIN) return false;
        members.put(memberUUID, FactionRank.ADMIN);
        return true;
    }
    public boolean demotePlayer(UUID memberUUID) {
        if (!members.containsKey(memberUUID) || members.get(memberUUID) != FactionRank.ADMIN) return false;
        members.put(memberUUID, FactionRank.MEMBER);
        return true;
    }

    public void addClaim(ChunkWrapper chunkWrapper) { this.claimedChunks.add(chunkWrapper); }
    public void removeClaim(ChunkWrapper chunkWrapper) {
        this.claimedChunks.remove(chunkWrapper);
        // If the removed chunk was the spawnblock chunk, relocate home
        if (plugin != null && chunkWrapper.equals(this.spawnBlockChunk)) {
            plugin.getLogger().info("Spawnblock chunk " + chunkWrapper + " for faction " + getName() + " is being unclaimed/lost. Relocating home...");
            relocateHomeToRandomClaim();
        }
        // If it was an outpost chunk, remove the outpost
        Outpost removedOutpost = null;
        for(Outpost outpost : outposts){
            if(outpost.getChunkWrapper().equals(chunkWrapper)){
                removedOutpost = outpost;
                break;
            }
        }
        if(removedOutpost != null){
            outposts.remove(removedOutpost);
            if(plugin != null) plugin.getLogger().info("Outpost at chunk " + chunkWrapper + " for faction " + getName() + " was lost.");
            // Notify faction if desired
        }
    }

    public boolean isConnectedToSpawnBlock(ChunkWrapper targetChunk, GFactionsPlugin plugin) {
        if (spawnBlockChunk == null && outposts.isEmpty()) return false; // No spawn points to connect to

        Set<ChunkWrapper> allSpawnChunks = new HashSet<>();
        if (spawnBlockChunk != null) {
            allSpawnChunks.add(spawnBlockChunk);
        }
        for (Outpost outpost : outposts) {
            allSpawnChunks.add(outpost.getChunkWrapper());
        }

        if (allSpawnChunks.contains(targetChunk)) return true; // Target chunk is itself a spawn chunk

        // Check adjacency to any existing claimed chunk that IS connected
        // This can be done with a breadth-first or depth-first search from any spawn chunk
        // within the faction's claimed territory.

        Queue<ChunkWrapper> queue = new LinkedList<>();
        Set<ChunkWrapper> visited = new HashSet<>();

        // Start search from all spawn chunks
        for(ChunkWrapper sc : allSpawnChunks){
            if(claimedChunks.contains(sc)){ // only if the spawn chunk is actually claimed by this faction
                queue.offer(sc);
                visited.add(sc);
            }
        }
        if(queue.isEmpty() && !claimedChunks.isEmpty()){ // If no spawn chunk is currently claimed, but other claims exist, something is wrong.
            // This state means claims exist but none are designated spawn or outpost.
            // For the purpose of this check, if we are trying to claim *adjacent* to an existing disconnected claim,
            // it should still fail if that existing claim isn't itself connected to a valid spawn.
            // However, the initial claim of a spawn chunk itself should always be allowed.
            // This logic mostly applies when *expanding* claims.
            return false; // Cannot connect if no valid spawn point within claims.
        }


        while(!queue.isEmpty()){
            ChunkWrapper current = queue.poll();

            // Check neighbors of 'current'
            int[] dX = {0, 0, 1, -1};
            int[] dZ = {1, -1, 0, 0};

            for(int i=0; i < 4; i++){
                ChunkWrapper neighbor = new ChunkWrapper(current.getWorldName(), current.getX() + dX[i], current.getZ() + dZ[i]);
                if(neighbor.equals(targetChunk)) return true; // Found a path to the target chunk

                if(claimedChunks.contains(neighbor) && !visited.contains(neighbor)){
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        return false; // Target chunk is not connected to any spawn block chunk via claimed land
    }

    public boolean isChunkAdjacentToExistingClaim(ChunkWrapper targetChunk) {
        if (claimedChunks.isEmpty()) { // First claim (spawnblock) doesn't need adjacency to itself
            return true;
        }
        int[] dX = {0, 0, 1, -1, 1, 1, -1, -1}; // Include diagonals for simple check if needed, or restrict to cardinal
        int[] dZ = {1, -1, 0, 0, 1, -1, 1, -1}; // Cardinal: first 4 pairs

        for (ChunkWrapper claimed : claimedChunks) {
            if (!claimed.getWorldName().equals(targetChunk.getWorldName())) continue;
            for (int i = 0; i < 4; i++) { // Cardinal adjacency
                if (claimed.getX() + dX[i] == targetChunk.getX() && claimed.getZ() + dZ[i] == targetChunk.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }


    public boolean isOwner(UUID playerUUID) { return playerUUID.equals(this.ownerUUID); }
    public boolean isAdmin(UUID playerUUID) { return members.getOrDefault(playerUUID, FactionRank.MEMBER) == FactionRank.ADMIN; }
    public boolean isMemberOrHigher(UUID playerUUID) { return members.containsKey(playerUUID); }
    public int getTotalSize() { return members.size(); }

    public boolean isEnemy(String otherFactionKey) { return enemyFactionKeys.contains(otherFactionKey.toLowerCase()); }
    public void addEnemy(String otherFactionKey, long timestamp) {
        String keyLC = otherFactionKey.toLowerCase();
        removeAlly(keyLC);
        enemyFactionKeys.add(keyLC);
        enemyDeclareTimestamps.put(keyLC, timestamp);
    }
    public boolean removeEnemy(String otherFactionKey) {
        String keyLC = otherFactionKey.toLowerCase();
        boolean changed = enemyFactionKeys.remove(keyLC);
        if (enemyDeclareTimestamps.remove(keyLC) != null) {
            changed = true;
        }
        return changed;
    }
    public boolean isAlly(String otherFactionKey) { return allyFactionKeys.contains(otherFactionKey.toLowerCase()); }
    public void addAlly(String otherFactionKey) {
        String keyLC = otherFactionKey.toLowerCase();
        if (isEnemy(keyLC)) return;
        allyFactionKeys.add(keyLC);
    }
    public boolean removeAlly(String otherFactionKey) {
        return allyFactionKeys.remove(otherFactionKey.toLowerCase());
    }
    public boolean isTrusted(UUID playerUUID) { return trustedPlayers.contains(playerUUID); }
    public boolean addTrusted(UUID playerUUID) { if (isMemberOrHigher(playerUUID)) return false; return trustedPlayers.add(playerUUID); }
    public boolean removeTrusted(UUID playerUUID) { return trustedPlayers.remove(playerUUID); }

    public void setVaultContents(ItemStack[] items) {
        if (this.vault == null) this.vault = Bukkit.createInventory(null, VAULT_SIZE, ChatColor.DARK_AQUA + originalName + " Vault");
        ItemStack[] contentsToSet = new ItemStack[VAULT_SIZE];
        for (int i = 0; i < VAULT_SIZE; i++) contentsToSet[i] = (items != null && i < items.length) ? items[i] : null;
        this.vault.setContents(contentsToSet);
    }
    public ItemStack[] getVaultContentsForSave() {
        ItemStack[] contents = new ItemStack[VAULT_SIZE];
        if (this.vault != null) {
            for (int i = 0; i < VAULT_SIZE && i < this.vault.getSize(); i++) {
                contents[i] = this.vault.getItem(i);
            }
        }
        return contents;
    }

    // --- Outpost Management ---
    public boolean addOutpost(Outpost outpost) {
        if (plugin != null && outposts.size() >= plugin.MAX_OUTPOSTS_PER_FACTION && plugin.MAX_OUTPOSTS_PER_FACTION > 0) {
            return false; // Max outposts reached
        }
        if (!outposts.contains(outpost)) { // Prevent duplicate outposts by chunk
            outposts.add(outpost);
            claimedChunks.add(outpost.getChunkWrapper()); // The outpost chunk is also a claim
            return true;
        }
        return false;
    }

    public boolean removeOutpost(ChunkWrapper outpostChunk) {
        Outpost toRemove = null;
        for (Outpost op : outposts) {
            if (op.getChunkWrapper().equals(outpostChunk)) {
                toRemove = op;
                break;
            }
        }
        if (toRemove != null) {
            outposts.remove(toRemove);
            // Note: The chunk itself is removed from claimedChunks via the general removeClaim method.
            // This method is more for managing the 'Outpost' object list.
            return true;
        }
        return false;
    }

    public Outpost getOutpost(ChunkWrapper chunk) {
        for (Outpost outpost : outposts) {
            if (outpost.getChunkWrapper().equals(chunk)) {
                return outpost;
            }
        }
        return null;
    }

    public boolean isOutpostChunk(ChunkWrapper chunk) {
        for (Outpost outpost : outposts) {
            if (outpost.getChunkWrapper().equals(chunk)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Faction faction = (Faction) o; return nameKey.equals(faction.nameKey);
    }
    @Override
    public int hashCode() { return Objects.hash(nameKey); }
}