package gg.goatedcraft.gfactions.data;

import java.util.Objects;

/**
 * A simple wrapper class for uniquely identifying a chunk by its world name and X, Z coordinates.
 * Used as a key in maps for storing claimed chunks.
 */
public class ChunkWrapper {
    private final String worldName;
    private final int x;
    private final int z;

    public ChunkWrapper(String worldName, int x, int z) {
        this.worldName = worldName;
        this.x = x;
        this.z = z;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkWrapper that = (ChunkWrapper) o;
        return x == that.x && z == that.z && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, z);
    }

    @Override
    public String toString() {
        // Used for saving to config.yml, ensure fromString can parse this format.
        return worldName + ";" + x + ";" + z;
    }

    /**
     * Provides a short string representation, e.g., "(10,-5)".
     * @return A short string for display.
     */
    public String toStringShort() {
        return "(" + x + "," + z + ")";
    }

    /**
     * Creates a ChunkWrapper from its string representation (e.g., "world;10;-5").
     * @param s The string to parse.
     * @return A ChunkWrapper object, or null if parsing fails.
     */
    public static ChunkWrapper fromString(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split(";");
        if (parts.length == 3) {
            try {
                String worldName = parts[0];
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                return new ChunkWrapper(worldName, x, z);
            } catch (NumberFormatException e) {
                // In a real plugin, you might log this error
                return null;
            }
        }
        return null; // Invalid format
    }
}