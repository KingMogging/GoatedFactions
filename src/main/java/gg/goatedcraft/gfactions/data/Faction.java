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
    private Location homeLocation; // This is the main faction home location
    private ChunkWrapper homeChunk; // Chunk containing the main homeLocation

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
        this.outposts = new ArrayList<>();

        if (initialHomeLocation != null && initialHomeLocation.getWorld() != null) {
            setHomeLocation(initialHomeLocation); // This will also set homeChunk
        } else if (pluginInstance != null) {
            pluginInstance.getLogger().warning("Faction " + name + " created with null initialHomeLocation or world. Home needs to be set.");
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
            if (this.homeLocation != null && this.homeLocation.getWorld() == null && this.homeChunk != null) {
                World world = Bukkit.getWorld(this.homeChunk.getWorldName());
                if (world != null) {
                    this.homeLocation = new Location(world, this.homeLocation.getX(), this.homeLocation.getY(), this.homeLocation.getZ(), this.homeLocation.getYaw(), this.homeLocation.getPitch());
                } else {
                    if(plugin != null) plugin.getLogger().warning("Could not reload world for home location of faction " + getName());
                }
            }
            for(Outpost outpost : outposts){
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

    public String getName() { return originalName; }
    public String getNameKey() { return nameKey; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public Map<UUID, FactionRank> getMembers() { return Collections.unmodifiableMap(members); }
    public Set<UUID> getMemberUUIDsOnly() { return Collections.unmodifiableSet(members.keySet()); }
    public FactionRank getRank(UUID playerUUID) { return members.getOrDefault(playerUUID, null); }
    public Set<ChunkWrapper> getClaimedChunks() { return Collections.unmodifiableSet(claimedChunks); }

    public Location getHomeLocation() {
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
    public ChunkWrapper getHomeChunk() { return homeChunk; } // Changed from getSpawnBlockChunk

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
            this.homeChunk = new ChunkWrapper(homeLocation.getWorld().getName(), homeLocation.getChunk().getX(), homeLocation.getChunk().getZ());
            if (!claimedChunks.contains(this.homeChunk)) {
                addClaim(this.homeChunk);
            }
        } else {
            this.homeChunk = null;
            if(plugin != null) plugin.getLogger().warning("Attempted to set home location with null world or location for faction: " + getName());
        }
    }

    public void relocateHomeToRandomClaim() {
        if (plugin == null) return;
        Set<ChunkWrapper> otherClaims = new HashSet<>(claimedChunks);
        if (homeChunk != null) {
            otherClaims.remove(homeChunk);
        }

        // Exclude outpost chunks from being chosen as a new main home automatically.
        // Player can manually set home in an outpost chunk via /f sethome, which then converts it.
        for (Outpost outpost : outposts) {
            otherClaims.remove(outpost.getChunkWrapper());
        }

        if (otherClaims.isEmpty()) {
            plugin.getLogger().warning("Faction " + getName() + " lost its home chunk and has no other suitable non-outpost claims. Home cannot be relocated automatically.");
            this.homeLocation = null;
            this.homeChunk = null;
            plugin.notifyFaction(this, ChatColor.RED + "" + ChatColor.BOLD + "Your faction's home chunk was lost and no other suitable land was found to automatically relocate your home! You must set a new home using /f sethome in a claimed chunk.", null);
            return;
        }

        List<ChunkWrapper> possibleRelocationChunks = new ArrayList<>(otherClaims);
        Collections.shuffle(possibleRelocationChunks);
        ChunkWrapper newHomeChunkWrapper = possibleRelocationChunks.get(0);

        World world = Bukkit.getWorld(newHomeChunkWrapper.getWorldName());
        if (world == null) {
            plugin.getLogger().severe("Failed to relocate home for " + getName() + ": World " + newHomeChunkWrapper.getWorldName() + " not loaded.");
            this.homeLocation = null;
            this.homeChunk = null;
            return;
        }

        Chunk newHomeChunkSpigot = world.getChunkAt(newHomeChunkWrapper.getX(), newHomeChunkWrapper.getZ());
        Location newHomeLoc = findSafeSurfaceLocation(newHomeChunkSpigot);

        if (newHomeLoc == null) {
            newHomeLoc = new Location(world, newHomeChunkWrapper.getX() * 16 + 8.5, world.getHighestBlockYAt(newHomeChunkWrapper.getX() * 16 + 8, newHomeChunkWrapper.getZ() * 16 + 8) + 1.0, newHomeChunkWrapper.getZ() * 16 + 8.5);
            if(world.getBlockAt(newHomeLoc).getType().isSolid() || world.getBlockAt(newHomeLoc.clone().add(0,-1,0)).getType() == Material.AIR){
                newHomeLoc = new Location(world, newHomeChunkWrapper.getX() * 16 + 8.5, 65, newHomeChunkWrapper.getZ() * 16 + 8.5);
            }
        }

        setHomeLocation(newHomeLoc);
        plugin.getLogger().info("Faction " + getName() + "'s home was relocated to chunk: " + newHomeChunkWrapper.toStringShort());
        plugin.notifyFaction(this, ChatColor.RED + "Your faction's home chunk was overclaimed! Your home has been randomly relocated within your territory to " + newHomeChunkWrapper.toStringShort() + ".", null);
    }

    private Location findSafeSurfaceLocation(Chunk chunk) {
        World world = chunk.getWorld();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            int x = chunk.getX() * 16 + random.nextInt(16);
            int z = chunk.getZ() * 16 + random.nextInt(16);
            // Get the highest solid block Y, then add 1 for feet, and 1 for head.
            Block highestBlock = world.getHighestBlockAt(x,z);
            int y = highestBlock.getY() + 1; // Feet position

            Location loc = new Location(world, x + 0.5, y, z + 0.5);

            Block blockAtFeet = world.getBlockAt(loc);
            Block blockAtHead = world.getBlockAt(loc.clone().add(0,1,0));
            Block blockBelowFeet = world.getBlockAt(loc.clone().add(0,-1,0));

            if (!blockAtFeet.getType().isSolid() && !blockAtHead.getType().isSolid() && blockBelowFeet.getType().isSolid()) {
                return loc;
            }
        }
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
        if (plugin != null && chunkWrapper.equals(this.homeChunk)) { // Changed from spawnBlockChunk
            plugin.getLogger().info("Home chunk " + chunkWrapper.toStringShort() + " for faction " + getName() + " is being unclaimed/lost. Relocating home...");
            relocateHomeToRandomClaim();
        }
        Outpost removedOutpost = null;
        for(Outpost outpost : outposts){
            if(outpost.getChunkWrapper().equals(chunkWrapper)){
                removedOutpost = outpost;
                break;
            }
        }
        if(removedOutpost != null){
            outposts.remove(removedOutpost);
            if(plugin != null) plugin.getLogger().info("Outpost at chunk " + chunkWrapper.toStringShort() + " for faction " + getName() + " was lost.");
        }
    }

    public boolean isConnectedToSpawnBlock(ChunkWrapper targetChunk, GFactionsPlugin plugin) {
        if (homeChunk == null && outposts.isEmpty()) return false;

        Set<ChunkWrapper> allSpawnChunks = new HashSet<>();
        if (homeChunk != null) { // Changed from spawnBlockChunk
            allSpawnChunks.add(homeChunk);
        }
        for (Outpost outpost : outposts) {
            allSpawnChunks.add(outpost.getChunkWrapper());
        }

        if (allSpawnChunks.contains(targetChunk)) return true;

        Queue<ChunkWrapper> queue = new LinkedList<>();
        Set<ChunkWrapper> visited = new HashSet<>();

        for(ChunkWrapper sc : allSpawnChunks){
            if(claimedChunks.contains(sc)){
                queue.offer(sc);
                visited.add(sc);
            }
        }
        if(queue.isEmpty() && !claimedChunks.isEmpty()){
            return false;
        }

        while(!queue.isEmpty()){
            ChunkWrapper current = queue.poll();
            int[] dX = {0, 0, 1, -1};
            int[] dZ = {1, -1, 0, 0};

            for(int i=0; i < 4; i++){
                ChunkWrapper neighbor = new ChunkWrapper(current.getWorldName(), current.getX() + dX[i], current.getZ() + dZ[i]);
                if(neighbor.equals(targetChunk)) return true;

                if(claimedChunks.contains(neighbor) && !visited.contains(neighbor)){
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }
        return false;
    }

    public boolean isChunkAdjacentToExistingClaim(ChunkWrapper targetChunk) {
        if (claimedChunks.isEmpty()) {
            return true;
        }
        int[] dX = {0, 0, 1, -1};
        int[] dZ = {1, -1, 0, 0};

        for (ChunkWrapper claimed : claimedChunks) {
            if (!claimed.getWorldName().equals(targetChunk.getWorldName())) continue;
            for (int i = 0; i < 4; i++) {
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

    public boolean addOutpost(Outpost outpost) {
        if (plugin != null && plugin.MAX_OUTPOSTS_PER_FACTION > 0 && outposts.size() >= plugin.MAX_OUTPOSTS_PER_FACTION) {
            return false;
        }
        if (!outposts.contains(outpost)) {
            outposts.add(outpost);
            claimedChunks.add(outpost.getChunkWrapper());
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
