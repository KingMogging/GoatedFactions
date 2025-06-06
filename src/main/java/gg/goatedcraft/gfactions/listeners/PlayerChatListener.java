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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) // Changed to HIGHEST for public chat
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String originalMessage = event.getMessage();
        // String originalFormat = event.getFormat();
        // We will construct the format

        Faction playerFaction = plugin.getFactionByPlayer(playerUUID);
        if (plugin.isPlayerInFactionChat(playerUUID)) {
            if (!plugin.FACTION_CHAT_ENABLED) {
                player.sendMessage(ChatColor.RED + "Faction chat is currently disabled by an administrator.");
                plugin.setPlayerFactionChat(playerUUID, false); // Switch them back to public
                Bukkit.getScheduler().runTask(plugin, () -> player.chat(originalMessage));
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            // Cancel the original public message

            if (playerFaction == null) {
                plugin.setPlayerFactionChat(playerUUID, false);
                player.sendMessage(ChatColor.RED + "You are no longer in faction chat as you are not in a faction.");
                Bukkit.getScheduler().runTask(plugin, () -> player.chat(originalMessage));
                // Resend as public
                return;
            }

            FactionRank rank = playerFaction.getRank(playerUUID);
            String rankDisplay = (rank != null) ? rank.getDisplayName() : FactionRank.ASSOCIATE.getDisplayName();
            // Using the modified FACTION_CHAT_FORMAT (rank only prefix)
            String factionChatFormat = plugin.FACTION_CHAT_FORMAT // This format should now be rank-prefix focused
                    .replace("{RANK}", rankDisplay)
                    .replace("{PLAYER_NAME}", player.getDisplayName()) // Use getDisplayName for consistency with other plugins potentially modifying it
                    .replace("{MESSAGE}", originalMessage)
                    // {FACTION_NAME} placeholder might still be in the string from config,
                    // ensure your config default is updated or it will show "null" or the literal placeholder.
                    // For safety, explicitly replace it here if it's not part of the desired rank-only format.
                    .replace("{FACTION_NAME}", playerFaction.getName());
            // Keep this if format string might still contain it, otherwise remove


            Set<Player> recipients = new HashSet<>();
            for (UUID memberUUID : playerFaction.getMemberUUIDsOnly()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    recipients.add(member);
                }
            }
            // Send to trusted players of this faction if they are online and not already members
            if (plugin.TRUSTED_PLAYERS_CAN_HEAR_FACTION_CHAT){ // New config option needed
                for (UUID trustedUUID : playerFaction.getTrustedPlayers()) {
                    if (!playerFaction.getMembers().containsKey(trustedUUID)) { // Ensure not already a member
                        Player trustedPlayer = Bukkit.getPlayer(trustedUUID);
                        if (trustedPlayer != null && trustedPlayer.isOnline()) {
                            recipients.add(trustedPlayer);
                        }
                    }
                }
            }


            for (Player recipient : recipients) {
                recipient.sendMessage(factionChatFormat);
            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("goatedfactions.admin.spy") && !recipients.contains(onlinePlayer)) {
                    onlinePlayer.sendMessage(ChatColor.GRAY + "[F-SPY] " + ChatColor.stripColor(factionChatFormat));
                }
            }
            plugin.getLogger().info("[FactionChat] " + ChatColor.stripColor(factionChatFormat));
        } else if (plugin.isPlayerInAllyChat(playerUUID)) {
            if (!plugin.ALLY_CHAT_ENABLED || !plugin.ENEMY_SYSTEM_ENABLED) { // Ally chat disabled if enemy system is off
                player.sendMessage(ChatColor.RED + "Ally chat is currently disabled.");
                plugin.setPlayerAllyChat(playerUUID, false);
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
                    .replace("{FACTION_NAME}", playerFaction.getName())
                    .replace("{PLAYER_NAME}", player.getDisplayName())
                    .replace("{MESSAGE}", originalMessage);
            Set<Player> recipients = new HashSet<>();
            for (UUID memberUUID : playerFaction.getMemberUUIDsOnly()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    recipients.add(member);
                }
            }
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

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("goatedfactions.admin.allyspy") && !recipients.contains(onlinePlayer)) {
                    onlinePlayer.sendMessage(ChatColor.GRAY + "[A-SPY] " + ChatColor.stripColor(allyChatFormat));
                }
            }
            plugin.getLogger().info("[AllyChat] " + ChatColor.stripColor(allyChatFormat));
        } else { // Public chat - apply prefix
            if (playerFaction != null) {
                // MODIFIED: Use the full name prefix format
                String chatPrefix = plugin.PUBLIC_CHAT_PREFIX_FORMAT
                        .replace("{FACTION_NAME}", playerFaction.getName());

                // Construct a new format string: [FACTION_NAME] <PlayerName>: Message
                // %1$s is player's display name, %2$s is the message.
                // The prefix is already color-translated when loaded from the config.
                event.setFormat(chatPrefix + "%1$s" + ChatColor.RESET + ": %2$s");
            }
            // If no faction, default chat handling applies
        }
    }
}