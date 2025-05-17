package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.TeleportRequest;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.UUID;

public class PlayerTeleportListener implements Listener {

    private final GFactionsPlugin plugin;

    public PlayerTeleportListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (plugin.getActiveTeleportRequests().containsKey(playerUUID)) {
            TeleportRequest request = plugin.getActiveTeleportRequests().get(playerUUID);
            Location to = event.getTo();

            if (to.getBlockX() != request.getInitialBlockLocation().getBlockX() ||
                    to.getBlockY() != request.getInitialBlockLocation().getBlockY() ||
                    to.getBlockZ() != request.getInitialBlockLocation().getBlockZ() ||
                    !Objects.equals(to.getWorld(), request.getInitialBlockLocation().getWorld())) {
                plugin.cancelTeleport(playerUUID, ChatColor.RED + "Teleport cancelled: You moved!");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getActiveTeleportRequests().containsKey(player.getUniqueId())) {
                plugin.cancelTeleport(player.getUniqueId(), ChatColor.RED + "Teleport cancelled: Damage taken!");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOtherTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (plugin.getActiveTeleportRequests().containsKey(playerUUID)) {
            // If the cause isn't our plugin's specific teleport (which would remove the request *before* teleporting)
            // then cancel our pending teleport. This helps prevent exploit by /otherteleportcommand during warmup.
            // Also check if the destination is different from our intended target.
            TeleportRequest request = plugin.getActiveTeleportRequests().get(playerUUID);
            if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN && // Allow our plugin's final teleport
                    event.getCause() != PlayerTeleportEvent.TeleportCause.UNKNOWN && // Some internal server teleports might be UNKNOWN
                    !event.getTo().equals(request.getTargetLocation())) { // If it's not to our target
                plugin.cancelTeleport(playerUUID, ChatColor.RED + "Teleport cancelled: Another teleport occurred.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getActiveTeleportRequests().containsKey(event.getPlayer().getUniqueId())) {
            plugin.cancelTeleport(event.getPlayer().getUniqueId(), null);
        }
    }
}
