package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PlayerClaimBoundaryListener implements Listener {

    private final GFactionsPlugin plugin;
    private final Map<UUID, String> playerLastTerritoryId = new HashMap<>(); // Stores a unique ID for the territory (e.g., "Wilderness" or "faction_key_FactionName")
    private final Map<UUID, Long> playerTitleCooldown = new HashMap<>();

    public PlayerClaimBoundaryListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getChunk().getX() == to.getChunk().getX() &&
                from.getChunk().getZ() == to.getChunk().getZ() &&
                Objects.equals(from.getWorld(), to.getWorld())) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        Chunk currentChunk = to.getChunk();
        String currentOwnerNameKey = plugin.getFactionOwningChunk(currentChunk);
        Faction currentOwnerFaction = (currentOwnerNameKey != null) ? plugin.getFaction(currentOwnerNameKey) : null;

        String currentTerritoryIdentifier;
        String displayTitle;
        String chatMessage;
        ChatColor titleColor;
        String relationColorStr = "";

        if (currentOwnerFaction != null) {
            currentTerritoryIdentifier = "faction_" + currentOwnerFaction.getNameKey(); // Unique ID for this faction's territory
            displayTitle = currentOwnerFaction.getName();
            Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

            if (playerFaction != null) {
                if (playerFaction.equals(currentOwnerFaction)) {
                    titleColor = ChatColor.GREEN;
                    relationColorStr = ChatColor.GREEN.toString();
                } else if (playerFaction.isAlly(currentOwnerFaction.getNameKey())) {
                    titleColor = ChatColor.AQUA;
                    relationColorStr = ChatColor.AQUA.toString();
                } else if (playerFaction.isEnemy(currentOwnerFaction.getNameKey())) {
                    titleColor = ChatColor.RED;
                    relationColorStr = ChatColor.RED.toString();
                } else {
                    titleColor = ChatColor.YELLOW;
                    relationColorStr = ChatColor.YELLOW.toString();
                }
            } else {
                titleColor = ChatColor.YELLOW;
                relationColorStr = ChatColor.YELLOW.toString();
            }
            chatMessage = plugin.MESSAGE_ENTERING_FACTION_TERRITORY
                    .replace("{FACTION_NAME}", currentOwnerFaction.getName())
                    .replace("{FACTION_RELATION_COLOR}", relationColorStr);
        } else {
            currentTerritoryIdentifier = "Wilderness"; // Unique ID for wilderness
            displayTitle = "Wilderness";
            titleColor = ChatColor.GRAY;
            chatMessage = plugin.MESSAGE_ENTERING_WILDERNESS;
        }

        String lastTerritory = playerLastTerritoryId.get(player.getUniqueId());

        // Only send title and message if the territory identifier has changed
        if (!currentTerritoryIdentifier.equals(lastTerritory)) {
            // Cooldown check now applies *only if* we are about to send a new title
            if (playerTitleCooldown.getOrDefault(player.getUniqueId(), 0L) + (plugin.TITLE_DISPLAY_COOLDOWN_SECONDS * 1000L) > currentTime) {
                // Still on cooldown, but territory changed. We might want to allow this.
                // For now, if territory changes, we ignore the general title cooldown and show it.
                // The primary purpose of playerLastTerritoryId is to prevent spam in the *same* territory.
            }

            player.sendTitle(titleColor + displayTitle, null, plugin.TITLE_FADE_IN_TICKS, plugin.TITLE_STAY_TICKS, plugin.TITLE_FADE_OUT_TICKS);
            player.sendMessage(chatMessage);

            playerLastTerritoryId.put(player.getUniqueId(), currentTerritoryIdentifier);
            playerTitleCooldown.put(player.getUniqueId(), currentTime); // Update cooldown for the next *different* territory change
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLastTerritoryId.remove(event.getPlayer().getUniqueId());
        playerTitleCooldown.remove(event.getPlayer().getUniqueId());
    }
}
