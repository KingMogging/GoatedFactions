package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import gg.goatedcraft.gfactions.data.FactionRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerChatListener implements Listener {

    private final GFactionsPlugin plugin;
    public PlayerChatListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String originalMessage = event.getMessage();

        Faction playerFaction = plugin.getFactionByPlayer(playerUUID);

        // --- Faction Chat Handling ---
        if (plugin.isPlayerInFactionChat(playerUUID)) {
            event.setCancelled(true); // Always cancel the event for private channels

            if (!plugin.FACTION_CHAT_ENABLED) {
                player.sendMessage(ChatColor.RED + "Faction chat is currently disabled.");
                plugin.setPlayerFactionChat(playerUUID, false);
                return;
            }

            if (playerFaction == null) {
                plugin.setPlayerFactionChat(playerUUID, false);
                player.sendMessage(ChatColor.RED + "You are no longer in faction chat as you are not in a faction.");
                return;
            }

            FactionRank rank = playerFaction.getRank(playerUUID);
            String rankDisplay = (rank != null) ? rank.getDisplayName() : FactionRank.ASSOCIATE.getDisplayName();

            String factionChatFormat = plugin.FACTION_CHAT_FORMAT
                    .replace("{RANK}", rankDisplay)
                    .replace("{PLAYER_NAME}", player.getDisplayName())
                    .replace("{MESSAGE}", originalMessage)
                    .replace("{FACTION_NAME}", playerFaction.getName());

            Set<Player> recipients = new HashSet<>();
            playerFaction.getMemberUUIDsOnly().stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .forEach(recipients::add);

            if (plugin.TRUSTED_PLAYERS_CAN_HEAR_FACTION_CHAT){
                playerFaction.getTrustedPlayers().stream()
                        .filter(uuid -> !playerFaction.getMembers().containsKey(uuid))
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline())
                        .forEach(recipients::add);
            }

            recipients.forEach(r -> r.sendMessage(factionChatFormat));

            // Spy for admins
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("goatedfactions.admin.spy") && !recipients.contains(p))
                    .forEach(spy -> spy.sendMessage(ChatColor.GRAY + "[F-SPY] " + ChatColor.stripColor(factionChatFormat)));

            plugin.getLogger().info("[FactionChat] " + ChatColor.stripColor(factionChatFormat));

            // --- Ally Chat Handling ---
        } else if (plugin.isPlayerInAllyChat(playerUUID)) {
            event.setCancelled(true);

            if (!plugin.ALLY_CHAT_ENABLED) {
                player.sendMessage(ChatColor.RED + "Ally chat is currently disabled.");
                plugin.setPlayerAllyChat(playerUUID, false);
                return;
            }

            if (playerFaction == null) {
                plugin.setPlayerAllyChat(playerUUID, false);
                player.sendMessage(ChatColor.RED + "You are no longer in ally chat as you are not in a faction.");
                return;
            }

            String allyChatFormat = plugin.ALLY_CHAT_FORMAT
                    .replace("{FACTION_NAME}", playerFaction.getName())
                    .replace("{PLAYER_NAME}", player.getDisplayName())
                    .replace("{MESSAGE}", originalMessage);

            Set<Player> recipients = new HashSet<>();
            playerFaction.getMemberUUIDsOnly().stream()
                    .map(Bukkit::getPlayer)
                    .filter(p -> p != null && p.isOnline())
                    .forEach(recipients::add);

            playerFaction.getAllyFactionKeys().stream()
                    .map(plugin::getFaction)
                    .filter(allyFac -> allyFac != null)
                    .flatMap(allyFac -> allyFac.getMemberUUIDsOnly().stream())
                    .map(Bukkit::getPlayer)
                    .filter(allyMember -> allyMember != null && allyMember.isOnline())
                    .forEach(recipients::add);

            recipients.forEach(r -> r.sendMessage(allyChatFormat));

            // Spy for admins
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("goatedfactions.admin.allyspy") && !recipients.contains(p))
                    .forEach(spy -> spy.sendMessage(ChatColor.GRAY + "[A-SPY] " + ChatColor.stripColor(allyChatFormat)));

            plugin.getLogger().info("[AllyChat] " + ChatColor.stripColor(allyChatFormat));

        }
        // --- Public Chat ---
        // No 'else' block needed. If the player is not in faction or ally chat,
        // we do nothing and let other plugins (like EssentialsXChat) handle the format.
        // They will automatically parse the %gfactions_...% placeholders.
    }
}
