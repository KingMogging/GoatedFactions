package gg.goatedcraft.gfactions.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Represents an active teleport request for a player, including the target,
 * initial location for movement checks, and the Bukkit scheduler task ID.
 */
public class TeleportRequest {
    private final UUID playerUUID;
    private final Location targetLocation;
    private final Location initialBlockLocation; // For movement check (block coordinates)
    private final int taskId;
    private final String teleportMessage; // Message to send on successful teleport

    public TeleportRequest(Player player, Location targetLocation, int taskId, String teleportMessage) {
        this.playerUUID = player.getUniqueId();
        this.targetLocation = targetLocation;
        // Store the block coordinates the player was in when initiating teleport
        Location currentLoc = player.getLocation();
        this.initialBlockLocation = new Location(currentLoc.getWorld(), currentLoc.getBlockX(), currentLoc.getBlockY(), currentLoc.getBlockZ());
        this.taskId = taskId;
        this.teleportMessage = teleportMessage;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public Location getInitialBlockLocation() {
        return initialBlockLocation;
    }

    public int getTaskId() {
        return taskId;
    }

    public String getTeleportMessage() {
        return teleportMessage;
    }
}
