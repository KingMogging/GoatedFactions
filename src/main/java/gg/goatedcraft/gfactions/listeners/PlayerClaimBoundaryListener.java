package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent; // Added for teleport handling

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerClaimBoundaryListener implements Listener {

    private final GFactionsPlugin plugin;
    private final Map<UUID, String> playerLastTerritoryId = new HashMap<>();
    private final Map<UUID, Long> playerLastTitleTime = new HashMap<>();


    public PlayerClaimBoundaryListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMoveOrTeleport(PlayerMoveEvent event) { // Renamed to reflect it handles general movement
        handlePlayerTerritoryChange(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) { // Handle teleports explicitly too
        handlePlayerTerritoryChange(event.getPlayer(), event.getFrom(), event.getTo());
    }


    private void handlePlayerTerritoryChange(Player player, Location from, Location to) {
        if (to == null || from == null || to.getWorld() == null || from.getWorld() == null) return;

        Chunk fromChunk = from.getChunk();
        Chunk toChunk = to.getChunk();

        // Only process if player actually moved to a new chunk
        if (fromChunk.getX() == toChunk.getX() &&
                fromChunk.getZ() == toChunk.getZ() &&
                Objects.equals(fromChunk.getWorld(), toChunk.getWorld())) {
            return;
        }

        // plugin.getLogger().log(Level.FINER, "[PCBL] " + player.getName() + " moved to new chunk: " + toChunk.getX() + "," + toChunk.getZ());

        String currentOwnerNameKey = plugin.getFactionOwningChunk(toChunk);
        Faction currentOwnerFaction = (currentOwnerNameKey != null) ? plugin.getFaction(currentOwnerNameKey) : null;

        String currentTerritoryIdentifier;
        String displayTitle;
        String chatMessage; // This will be the message sent in chat
        ChatColor titleColor; // This will be the primary color for the title
        String relationColorStr = ""; // For placeholder in chat message

        if (currentOwnerFaction != null) {
            currentTerritoryIdentifier = "faction_" + currentOwnerFaction.getNameKey();
            displayTitle = currentOwnerFaction.getName(); // Assume name is not null due to validation
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
                titleColor = ChatColor.YELLOW; relationColorStr = ChatColor.YELLOW.toString(); // Neutral view
            }
            // Construct the message for entering faction territory
            chatMessage = plugin.MESSAGE_ENTERING_FACTION_TERRITORY
                    .replace("{FACTION_NAME}", displayTitle) // displayTitle already has correct name
                    .replace("{FACTION_RELATION_COLOR}", relationColorStr);

        } else { // Wilderness
            currentTerritoryIdentifier = "Wilderness";
            displayTitle = "Wilderness"; // Title for wilderness
            titleColor = ChatColor.GRAY;
            chatMessage = plugin.MESSAGE_ENTERING_WILDERNESS;
        }

        String lastTerritory = playerLastTerritoryId.get(player.getUniqueId());

        if (!currentTerritoryIdentifier.equals(lastTerritory)) {
            long currentTime = System.currentTimeMillis();
            long lastTitleSent = playerLastTitleTime.getOrDefault(player.getUniqueId(), 0L);

            if ((currentTime - lastTitleSent) > (plugin.TITLE_DISPLAY_COOLDOWN_SECONDS * 1000L)) {
                // plugin.getLogger().log(Level.INFO, "[PCBL] Sending title for " + player.getName() + " entering " + displayTitle);
                player.sendTitle(titleColor + displayTitle, null, plugin.TITLE_FADE_IN_TICKS, plugin.TITLE_STAY_TICKS, plugin.TITLE_FADE_OUT_TICKS);
                player.sendMessage(chatMessage); // Send chat message along with title
                playerLastTitleTime.put(player.getUniqueId(), currentTime);
            } else {
                // Cooldown active, but territory changed, so still send chat message if it's different.
                // Or, if you want chat message also on cooldown, move sendMessage inside the if block.
                // For now, chat message always sends on territory change if title is on cooldown.
                // plugin.getLogger().log(Level.FINER, "[PCBL] Title cooldown for " + player.getName() + ". Territory changed to " + displayTitle + ", sending chat message only.");
                player.sendMessage(chatMessage);
            }
            playerLastTerritoryId.put(player.getUniqueId(), currentTerritoryIdentifier);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLastTerritoryId.remove(event.getPlayer().getUniqueId());
        playerLastTitleTime.remove(event.getPlayer().getUniqueId());
    }
}
