package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import gg.goatedcraft.gfactions.data.FactionRank;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PlayerClaimBoundaryListener implements Listener {

    private final GFactionsPlugin plugin;
    private final Map<UUID, String> playerLastTerritoryId = new HashMap<>();
    private final Map<UUID, Long> playerLastTitleTime = new HashMap<>();


    public PlayerClaimBoundaryListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        handlePlayerTerritoryChange(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        handlePlayerTerritoryChange(event.getPlayer(), event.getFrom(), event.getTo());
    }


    private void handlePlayerTerritoryChange(Player player, Location from, Location to) {
        if (to == null || from == null || to.getWorld() == null || from.getWorld() == null) return;

        Chunk fromChunk = from.getChunk();
        Chunk toChunk = to.getChunk();

        if (fromChunk.getX() == toChunk.getX() &&
                fromChunk.getZ() == toChunk.getZ() &&
                Objects.equals(fromChunk.getWorld(), toChunk.getWorld())) {
            return;
        }

        String currentOwnerNameKey = plugin.getFactionOwningChunk(toChunk);
        Faction currentOwnerFaction = (currentOwnerNameKey != null) ? plugin.getFaction(currentOwnerNameKey) : null;

        // Autoclaim logic
        if (plugin.isPlayerAutoclaiming(player.getUniqueId()) && currentOwnerFaction == null) {
            Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());
            if (playerFaction != null) {
                // Fixed potential NullPointerException by checking rank before using it
                FactionRank rank = playerFaction.getRank(player.getUniqueId());
                if (rank != null && rank.isAdminOrHigher()) {
                    if(plugin.claimChunk(playerFaction, toChunk, false, false, null, player)) {
                        // Update currentOwnerFaction as it has just been claimed
                        currentOwnerFaction = playerFaction;
                    }
                }
            }
        }


        String currentTerritoryIdentifier;
        String displayTitle;
        String chatMessage;
        ChatColor titleColor;
        String relationColorStr = "";

        if (currentOwnerFaction != null) {
            currentTerritoryIdentifier = "faction_" + currentOwnerFaction.getNameKey();
            displayTitle = currentOwnerFaction.getName();
            Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

            if (playerFaction != null) {
                if (playerFaction.equals(currentOwnerFaction)) {
                    titleColor = ChatColor.GREEN; relationColorStr = ChatColor.GREEN.toString();
                } else if (playerFaction.isAlly(currentOwnerFaction.getNameKey())) {
                    titleColor = ChatColor.AQUA; relationColorStr = ChatColor.AQUA.toString();
                } else if (playerFaction.isEnemy(currentOwnerFaction.getNameKey())) {
                    titleColor = ChatColor.RED; relationColorStr = ChatColor.RED.toString();
                } else { // Neutral
                    titleColor = ChatColor.YELLOW; relationColorStr = ChatColor.YELLOW.toString();
                }
            } else { // Player is factionless
                titleColor = ChatColor.YELLOW; relationColorStr = ChatColor.YELLOW.toString();
            }
            chatMessage = plugin.MESSAGE_ENTERING_FACTION_TERRITORY
                    .replace("{FACTION_NAME}", displayTitle)
                    .replace("{FACTION_RELATION_COLOR}", relationColorStr);

        } else { // Wilderness
            currentTerritoryIdentifier = "Wilderness";
            displayTitle = "Wilderness";
            titleColor = ChatColor.GRAY;
            chatMessage = plugin.MESSAGE_ENTERING_WILDERNESS;
        }

        String lastTerritory = playerLastTerritoryId.get(player.getUniqueId());

        if (!currentTerritoryIdentifier.equals(lastTerritory)) {
            long currentTime = System.currentTimeMillis();
            long lastTitleSent = playerLastTitleTime.getOrDefault(player.getUniqueId(), 0L);

            if ((currentTime - lastTitleSent) > (plugin.TITLE_DISPLAY_COOLDOWN_SECONDS * 1000L)) {
                player.sendTitle(titleColor + displayTitle, null, plugin.TITLE_FADE_IN_TICKS, plugin.TITLE_STAY_TICKS, plugin.TITLE_FADE_OUT_TICKS);
                player.sendMessage(chatMessage);
                playerLastTitleTime.put(player.getUniqueId(), currentTime);
            } else {
                player.sendMessage(chatMessage);
            }
            playerLastTerritoryId.put(player.getUniqueId(), currentTerritoryIdentifier);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLastTerritoryId.remove(event.getPlayer().getUniqueId());
        playerLastTitleTime.remove(event.getPlayer().getUniqueId());
        // This will now work because we are adding the getter method in the next step.
        plugin.getPlayersWithAutoclaim().remove(event.getPlayer().getUniqueId());
    }
}
