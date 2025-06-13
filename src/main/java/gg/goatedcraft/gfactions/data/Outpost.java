package gg.goatedcraft.gfactions.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class Outpost {
    private String worldName;
    private int x;
    private int z;
    private Location outpostSpawnLocation;
    private Set<ChunkWrapper> outpostSpecificClaims;
    private transient int outpostID = -1;

    public Outpost(Location outpostSpawnLocation, ChunkWrapper initialClaim) {
        this.worldName = outpostSpawnLocation.getWorld().getName();
        this.x = outpostSpawnLocation.getChunk().getX();
        this.z = outpostSpawnLocation.getChunk().getZ();
        this.outpostSpawnLocation = outpostSpawnLocation;
        this.outpostSpecificClaims = new HashSet<>();
        if (initialClaim != null) {
            this.outpostSpecificClaims.add(initialClaim);
        }
    }

    public Outpost(String worldName, int x, int z, Location outpostSpawnLocation, Set<ChunkWrapper> specificClaims) {
        this.worldName = worldName;
        this.x = x;
        this.z = z;
        this.outpostSpawnLocation = outpostSpawnLocation;
        this.outpostSpecificClaims = new HashSet<>(specificClaims);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public ChunkWrapper getSpawnChunk() {
        return new ChunkWrapper(worldName, x, z);
    }

    public Location getOutpostSpawnLocation() {
        if (outpostSpawnLocation != null && outpostSpawnLocation.getWorld() == null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                this.outpostSpawnLocation = new Location(world, outpostSpawnLocation.getX(), outpostSpawnLocation.getY(), outpostSpawnLocation.getZ(), outpostSpawnLocation.getYaw(), outpostSpawnLocation.getPitch());
            } else {
                return null;
            }
        }
        return outpostSpawnLocation;
    }

    public void setOutpostSpawnLocation(Location outpostSpawnLocation) {
        this.outpostSpawnLocation = outpostSpawnLocation;
        if (outpostSpawnLocation != null && outpostSpawnLocation.getWorld() != null) {
            this.worldName = outpostSpawnLocation.getWorld().getName();
            this.x = outpostSpawnLocation.getChunk().getX();
            this.z = outpostSpawnLocation.getChunk().getZ();
        }
    }

    public Set<ChunkWrapper> getOutpostSpecificClaims() {
        return outpostSpecificClaims;
    }

    public void addClaim(ChunkWrapper claim) {
        this.outpostSpecificClaims.add(claim);
    }

    public void removeClaim(ChunkWrapper claim) {
        this.outpostSpecificClaims.remove(claim);
    }

    public boolean containsClaim(ChunkWrapper claim) {
        return this.outpostSpecificClaims.contains(claim);
    }

    public void setOutpostID(int id) { this.outpostID = id; }
    public int getOutpostID() { return this.outpostID; }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("worldName", worldName);
        map.put("x", x);
        map.put("z", z);
        if (outpostSpawnLocation != null && outpostSpawnLocation.getWorld() != null) {
            map.put("spawnLocation.world", outpostSpawnLocation.getWorld().getName());
            map.put("spawnLocation.x", outpostSpawnLocation.getX());
            map.put("spawnLocation.y", outpostSpawnLocation.getY());
            map.put("spawnLocation.z", outpostSpawnLocation.getZ());
            map.put("spawnLocation.yaw", outpostSpawnLocation.getYaw());
            map.put("spawnLocation.pitch", outpostSpawnLocation.getPitch());
        }
        map.put("specificClaims", outpostSpecificClaims.stream().map(ChunkWrapper::toString).collect(Collectors.toList()));
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Outpost deserialize(Map<String, Object> map) {
        if (map == null) return null;
        try {
            String world = (String) map.get("worldName");
            int chunkX = (int) map.get("x");
            int chunkZ = (int) map.get("z");

            Location spawnLoc = null;
            if (map.containsKey("spawnLocation.world")) {
                World spawnWorld = Bukkit.getWorld((String) map.get("spawnLocation.world"));
                if (spawnWorld != null) {
                    spawnLoc = new Location(
                            spawnWorld,
                            (double) map.get("spawnLocation.x"),
                            (double) map.get("spawnLocation.y"),
                            (double) map.get("spawnLocation.z"),
                            ((Number) map.get("spawnLocation.yaw")).floatValue(),
                            ((Number) map.get("spawnLocation.pitch")).floatValue()
                    );
                }
            }

            Set<ChunkWrapper> specificClaims = new HashSet<>();
            List<String> claimStrings = (List<String>) map.get("specificClaims");
            if (claimStrings != null) {
                for (String s : claimStrings) {
                    ChunkWrapper cw = ChunkWrapper.fromString(s);
                    if (cw != null) specificClaims.add(cw);
                }
            }
            if(spawnLoc != null) {
                specificClaims.add(new ChunkWrapper(spawnLoc.getWorld().getName(), spawnLoc.getChunk().getX(), spawnLoc.getChunk().getZ()));
            }

            return new Outpost(world, chunkX, chunkZ, spawnLoc, specificClaims);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[GFactions] Error deserializing outpost: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Outpost outpost = (Outpost) o;
        return x == outpost.x && z == outpost.z && Objects.equals(worldName, outpost.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, z);
    }
}
