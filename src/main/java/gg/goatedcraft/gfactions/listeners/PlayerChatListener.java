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
import org.bukkit.event.player.AsyncPlayerChatEvent; // Keep this for chat formatting

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerChatListener implements Listener {

    private final GFactionsPlugin plugin;

    public PlayerChatListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true) // Changed from HIGHEST to allow other plugins to format if needed
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String originalMessage = event.getMessage();
        String originalFormat = event.getFormat(); // Get the original format

        Faction playerFaction = plugin.getFactionByPlayer(playerUUID);

        if (plugin.isPlayerInFactionChat(playerUUID)) {
            if (!plugin.FACTION_CHAT_ENABLED) {
                player.sendMessage(ChatColor.RED + "Faction chat is currently disabled by an administrator.");
                plugin.setPlayerFactionChat(playerUUID, false); // Switch them back to public
                // Resend as public chat
                Bukkit.getScheduler().runTask(plugin, () -> player.chat(originalMessage));
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true); // Cancel the original public message

            if (playerFaction == null) {
                plugin.setPlayerFactionChat(playerUUID, false);
                player.sendMessage(ChatColor.RED + "You are no longer in faction chat as you are not in a faction.");
                Bukkit.getScheduler().runTask(plugin, () -> player.chat(originalMessage)); // Resend as public
                return;
            }

            FactionRank rank = playerFaction.getRank(playerUUID);
            String rankDisplay = (rank != null) ? rank.getDisplayName() : FactionRank.ASSOCIATE.getDisplayName(); // Default to Associate if somehow null

            String factionChatFormat = plugin.FACTION_CHAT_FORMAT
                    .replace("{FACTION_NAME}", playerFaction.getName())
                    .replace("{PLAYER_NAME}", player.getDisplayName())
                    .replace("{RANK}", rankDisplay)
                    .replace("{MESSAGE}", originalMessage);

            Set<Player> recipients = new HashSet<>();
            // Send to faction members
            for (UUID memberUUID : playerFaction.getMemberUUIDsOnly()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    recipients.add(member);
                }
            }
            // Send to trusted players of this faction (if they are online and not already members)
            for (UUID trustedUUID : playerFaction.getTrustedPlayers()) {
                if (!playerFaction.getMembers().containsKey(trustedUUID)) { // Ensure not already a member
                    Player trustedPlayer = Bukkit.getPlayer(trustedUUID);
                    if (trustedPlayer != null && trustedPlayer.isOnline()) {
                        recipients.add(trustedPlayer);
                    }
                }
            }

            for (Player recipient : recipients) {
                recipient.sendMessage(factionChatFormat);
            }

            // Send to spying operators
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("goatedfactions.admin.spy") && !recipients.contains(onlinePlayer)) {
                    onlinePlayer.sendMessage(ChatColor.GRAY + "[F-SPY] " + ChatColor.stripColor(factionChatFormat));
                }
            }
            plugin.getLogger().info("[FactionChat] " + ChatColor.stripColor(factionChatFormat));

        } else if (plugin.isPlayerInAllyChat(playerUUID)) {
            if (!plugin.ALLY_CHAT_ENABLED) {
                player.sendMessage(ChatColor.RED + "Ally chat is currently disabled by an administrator.");
                plugin.setPlayerAllyChat(playerUUID, false); // Switch them back to public
                Bukkit.getScheduler().runTask(plugin, () -> player.chat(originalMessage));
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);

            if (playerFaction == null) {
                plugin.setPlayerAllyChat(playerUUID, false);
                player.sendMessage(ChatColor.RED + "You are no longer in ally chat as you are not in a faction.");
                Bukkit.getScheduler().runTask(plugin, () -> player.chat(originalMessage));
                return;
            }

            String allyChatFormat = plugin.ALLY_CHAT_FORMAT
                    .replace("{FACTION_NAME}", playerFaction.getName()) // Sender's faction name
                    .replace("{PLAYER_NAME}", player.getDisplayName())
                    .replace("{MESSAGE}", originalMessage);

            Set<Player> recipients = new HashSet<>();
            // Add sender's faction members
            for (UUID memberUUID : playerFaction.getMemberUUIDsOnly()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    recipients.add(member);
                }
            }
            // Add members of all allied factions
            for (String allyKey : playerFaction.getAllyFactionKeys()) {
                Faction allyFaction = plugin.getFaction(allyKey);
                if (allyFaction != null) {
                    for (UUID allyMemberUUID : allyFaction.getMemberUUIDsOnly()) {
                        Player allyMember = Bukkit.getPlayer(allyMemberUUID);
                        if (allyMember != null && allyMember.isOnline()) {
                            recipients.add(allyMember);
                        }
                    }
                }
            }

            for (Player recipient : recipients) {
                recipient.sendMessage(allyChatFormat);
            }

            // Send to spying operators
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("goatedfactions.admin.allyspy") && !recipients.contains(onlinePlayer)) { // New perm for ally spy
                    onlinePlayer.sendMessage(ChatColor.GRAY + "[A-SPY] " + ChatColor.stripColor(allyChatFormat));
                }
            }
            plugin.getLogger().info("[AllyChat] " + ChatColor.stripColor(allyChatFormat));

        } else { // Public chat - apply prefix
            if (playerFaction != null) {
                String prefix = plugin.PUBLIC_CHAT_PREFIX_FORMAT.replace("{FACTION_NAME}", playerFaction.getName());
                // event.setFormat(prefix + event.getFormat()); // This prepends to the existing format string
                // A more common way is to set the display name or modify the format string carefully.
                // For compatibility, let's try modifying the format string directly.
                // The default format is usually like "<%1$s> %2$s" where %1$s is player name, %2$s is message.
                // We want it to be "[FactionName] <%1$s> %2$s"
                // This can be tricky if other plugins also modify chat format.
                // A common approach is to use PlaceholderAPI if available, or a configurable format string.
                // For now, let's prepend the prefix to the player's display name part of the format.

                // Bukkit's default format is "<%1$s> %2$s"
                // We want "[FactionName] PlayerName: Message"
                // So, if original format is "<%1$s> %2$s", we change it to "prefix %1$s: %2$s"
                // This is a simplification. A robust solution often involves a chat handling library or PlaceholderAPI.

                // Let's try a simpler approach: set a prefix for the player's name in the chat.
                // This is often done by setting the player's display name temporarily or using a chat formatting event.
                // Since AsyncPlayerChatEvent is cancellable and we can set the format, we can try:
                String newFormat = prefix + originalFormat; // Prepend our prefix to the existing format string
                event.setFormat(newFormat);

                // Tab list prefix is handled differently, usually via Player#setPlayerListName()
                // or scoreboard teams. We'll address tab list separately.
            }
        }
    }
}
