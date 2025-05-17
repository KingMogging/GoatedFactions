package gg.goatedcraft.gfactions.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.Objects;

public class Outpost {
    private String worldName;
    private int x; // Chunk X
    private int z; // Chunk Z
    private Location outpostSpawnLocation; // Specific spawn point within the outpost chunk

    // Constructor for a new outpost
    public Outpost(Location outpostSpawnLocation) {
        this.worldName = outpostSpawnLocation.getWorld().getName();
        this.x = outpostSpawnLocation.getChunk().getX();
        this.z = outpostSpawnLocation.getChunk().getZ();
        this.outpostSpawnLocation = outpostSpawnLocation;
    }

    // Constructor for loading from config (adjust as needed for your serialization)
    public Outpost(String worldName, int x, int z, Location outpostSpawnLocation) {
        this.worldName = worldName;
        this.x = x;
        this.z = z;
        this.outpostSpawnLocation = outpostSpawnLocation;
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

    public ChunkWrapper getChunkWrapper() {
        return new ChunkWrapper(worldName, x, z);
    }

    public Location getOutpostSpawnLocation() {
        // Ensure world is loaded, if not, try to load it or handle appropriately
        if (outpostSpawnLocation != null && outpostSpawnLocation.getWorld() == null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                this.outpostSpawnLocation = new Location(world, outpostSpawnLocation.getX(), outpostSpawnLocation.getY(), outpostSpawnLocation.getZ(), outpostSpawnLocation.getYaw(), outpostSpawnLocation.getPitch());
            } else {
                // Consider logging a warning if world isn't loaded
                return null; // Or throw an exception
            }
        }
        return outpostSpawnLocation;
    }

    public void setOutpostSpawnLocation(Location outpostSpawnLocation) {
        this.outpostSpawnLocation = outpostSpawnLocation;
        // Update worldName, x, z if the new location is in a different chunk (though typically sethome is within the same claimed chunk)
        if (outpostSpawnLocation != null && outpostSpawnLocation.getWorld() != null) {
            this.worldName = outpostSpawnLocation.getWorld().getName();
            this.x = outpostSpawnLocation.getChunk().getX();
            this.z = outpostSpawnLocation.getChunk().getZ();
        }
    }

    // For saving to config - example string representation
    public String serialize() {
        if (outpostSpawnLocation == null || outpostSpawnLocation.getWorld() == null) return null;
        return outpostSpawnLocation.getWorld().getName() + ";" +
                outpostSpawnLocation.getX() + ";" +
                outpostSpawnLocation.getY() + ";" +
                outpostSpawnLocation.getZ() + ";" +
                outpostSpawnLocation.getYaw() + ";" +
                outpostSpawnLocation.getPitch() + ";" +
                x + ";" + // Chunk X
                z; // Chunk Z
    }

    // For loading from config - example
    public static Outpost deserialize(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(";");
        if (parts.length == 8) {
            try {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) return null; // World not loaded
                double locX = Double.parseDouble(parts[1]);
                double locY = Double.parseDouble(parts[2]);
                double locZ = Double.parseDouble(parts[3]);
                float yaw = Float.parseFloat(parts[4]);
                float pitch = Float.parseFloat(parts[5]);
                int chunkX = Integer.parseInt(parts[6]);
                int chunkZ = Integer.parseInt(parts[7]);
                Location spawnLoc = new Location(world, locX, locY, locZ, yaw, pitch);
                return new Outpost(parts[0], chunkX, chunkZ, spawnLoc);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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