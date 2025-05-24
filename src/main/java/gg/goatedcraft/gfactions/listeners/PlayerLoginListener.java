package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerLoginListener implements Listener {

    private final GFactionsPlugin plugin;

    public PlayerLoginListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

        if (playerFaction != null) {
            // Update the faction's last online time
            plugin.updateFactionActivity(playerFaction.getNameKey());
            // Potentially save activity data periodically or on disable,
            // but for now, updateFactionActivity just updates the in-memory map.
            // GFactionsPlugin will handle saving this data.

            // Also, ensure the player's tab name is updated on join
            plugin.updatePlayerTabListName(player);
        }
    }
}
