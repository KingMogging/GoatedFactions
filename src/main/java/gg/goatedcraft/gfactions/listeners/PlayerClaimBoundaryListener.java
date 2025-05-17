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
    // Store the last displayed title string to allow for immediate overwrite
    private final Map<UUID, String> playerLastDisplayedTitle = new HashMap<>();
    private final Map<UUID, Long> playerTitleCooldown = new HashMap<>(); // Cooldown for sending *any* title again too quickly

    public PlayerClaimBoundaryListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Check if player actually moved between chunks
        if (from.getChunk().getX() == to.getChunk().getX() &&
                from.getChunk().getZ() == to.getChunk().getZ() &&
                Objects.equals(from.getWorld(), to.getWorld())) {
            return; // Still in the same chunk
        }

        long currentTime = System.currentTimeMillis();
        // Cooldown check: only allow a new title/message if cooldown has passed
        if (playerTitleCooldown.getOrDefault(player.getUniqueId(), 0L) + (plugin.TITLE_DISPLAY_COOLDOWN_SECONDS * 1000L) > currentTime) {
            // If we are on cooldown, but the *territory* changed, we might still want to send an update.
            // The cooldown is more to prevent spam if they jitter on a border.
            // For now, a simple cooldown. More complex logic could allow immediate update if territory *name* changes.
        }


        Chunk currentChunk = to.getChunk();
        String currentOwnerNameKey = plugin.getFactionOwningChunk(currentChunk);
        Faction currentOwnerFaction = (currentOwnerNameKey != null) ? plugin.getFaction(currentOwnerNameKey) : null;

        String displayTitle;
        String chatMessage;
        ChatColor titleColor;
        String relationColorStr = ""; // For chat message placeholders

        if (currentOwnerFaction != null) {
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
                } else { // Neutral
                    titleColor = ChatColor.YELLOW;
                    relationColorStr = ChatColor.YELLOW.toString();
                }
            } else { // Player is factionless
                titleColor = ChatColor.YELLOW; // Neutral color for factionless players viewing others' land
                relationColorStr = ChatColor.YELLOW.toString();
            }
            chatMessage = plugin.MESSAGE_ENTERING_FACTION_TERRITORY
                    .replace("{FACTION_NAME}", currentOwnerFaction.getName())
                    .replace("{FACTION_RELATION_COLOR}", relationColorStr);
        } else {
            displayTitle = "Wilderness";
            titleColor = ChatColor.GRAY;
            chatMessage = plugin.MESSAGE_ENTERING_WILDERNESS;
        }

        String fullDisplayTitle = titleColor + displayTitle;
        String lastDisplayed = playerLastDisplayedTitle.get(player.getUniqueId());

        // Only send title and message if it's different from the last one shown or if cooldown has passed
        // The title system will naturally overwrite. We just control when we send a *new* title event.
        if (!fullDisplayTitle.equals(lastDisplayed) || (playerTitleCooldown.getOrDefault(player.getUniqueId(), 0L) + (plugin.TITLE_DISPLAY_COOLDOWN_SECONDS * 1000L) <= currentTime)) {
            player.sendTitle(fullDisplayTitle, null, plugin.TITLE_FADE_IN_TICKS, plugin.TITLE_STAY_TICKS, plugin.TITLE_FADE_OUT_TICKS);
            player.sendMessage(chatMessage); // Send chat message only to the player

            playerLastDisplayedTitle.put(player.getUniqueId(), fullDisplayTitle);
            playerTitleCooldown.put(player.getUniqueId(), currentTime); // Update cooldown timestamp
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerLastDisplayedTitle.remove(event.getPlayer().getUniqueId());
        playerTitleCooldown.remove(event.getPlayer().getUniqueId());
    }
}