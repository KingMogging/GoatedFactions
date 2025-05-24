package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerTabDisplayListener implements Listener {

    private final GFactionsPlugin plugin;

    public PlayerTabDisplayListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    // Update on join
    @EventHandler(priority = EventPriority.MONITOR) // Monitor to run after other plugins might have set display name
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.updatePlayerTabListName(event.getPlayer());
    }

    // Could also update on faction join/leave/rename events if those are implemented more directly
    // For now, join event is the primary trigger. FactionCommand changes will also need to trigger updates.

    // Reset on quit to prevent issues if player had a prefix and plugin is reloaded/removed
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Reset to original name if it was prefixed.
        // This is tricky without storing original, but often other plugins handle this.
        // For simplicity, we might not explicitly reset, or reset to player.getName().
        // If another plugin manages tab, it will likely overwrite this anyway.
        // Let's assume for now that other plugins will handle the base name or it's not critical to reset here.
        // If issues arise, a more robust reset mechanism would be needed.
    }

    // We will add a public method in GFactionsPlugin to call to update a player's tab name
    // This can be called when a player joins a faction, leaves, or a faction renames.
}
