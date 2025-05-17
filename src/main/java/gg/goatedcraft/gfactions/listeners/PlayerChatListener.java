package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Set;
import java.util.UUID;

public class PlayerChatListener implements Listener {

    private final GFactionsPlugin plugin;

    public PlayerChatListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (plugin.isPlayerInFactionChat(playerUUID)) {
            event.setCancelled(true); // Cancel the original message

            Faction playerFaction = plugin.getFactionByPlayer(playerUUID);
            if (playerFaction == null) {
                // Should not happen if toggle is managed correctly, but as a fallback:
                plugin.setPlayerFactionChat(playerUUID, false);
                player.sendMessage(ChatColor.RED + "You are no longer in faction chat as you are not in a faction.");
                // Resend message to public chat? Or just swallow it. For now, swallow.
                // For resending (if desired, can be complex with async):
                // Bukkit.getScheduler().runTask(plugin, () -> player.chat(event.getMessage()));
                return;
            }

            String factionChatFormat = plugin.FACTION_CHAT_FORMAT
                    .replace("{FACTION_NAME}", playerFaction.getName())
                    .replace("{PLAYER_NAME}", player.getDisplayName()) // or player.getName()
                    .replace("{MESSAGE}", event.getMessage());

            // Send to faction members
            for (UUID memberUUID : playerFaction.getMemberUUIDsOnly()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    member.sendMessage(factionChatFormat);
                }
            }

            // Send to spying operators
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("goatedfactions.admin.spy") && // New permission for spying
                        !playerFaction.isMemberOrHigher(onlinePlayer.getUniqueId())) { // Don't send twice to op if they are in the same faction
                    onlinePlayer.sendMessage(ChatColor.GRAY + "[SPY] " + factionChatFormat);
                }
            }
            // Log to console
            plugin.getLogger().info("[FactionChat] " + ChatColor.stripColor(factionChatFormat));
        }
    }
}