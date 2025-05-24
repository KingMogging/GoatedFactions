package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import gg.goatedcraft.gfactions.data.FactionRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final GFactionsPlugin plugin;

    public PlayerDeathListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller(); // The player who killed the victim

        if (killer == null || victim.equals(killer)) return; // No killer or self-kill

        Faction victimFaction = plugin.getFactionByPlayer(victim.getUniqueId());
        Faction killerFaction = plugin.getFactionByPlayer(killer.getUniqueId());

        // Both must be in factions, and not the same faction
        if (victimFaction == null || killerFaction == null || victimFaction.equals(killerFaction)) return;

        // Check if the enemy system is enabled and if they are enemies
        if (plugin.ENEMY_SYSTEM_ENABLED && victimFaction.isEnemy(killerFaction.getNameKey())) {
            FactionRank victimRank = victimFaction.getRank(victim.getUniqueId());
            int powerLoss;

            if (victimRank != null && victimRank.isAdminOrHigher()) { // Owner or Admin
                powerLoss = plugin.POWER_LOSS_ON_DEATH_ADMIN_OWNER;
            } else { // Member or Associate (or any other non-admin/owner rank)
                powerLoss = plugin.POWER_LOSS_ON_DEATH_MEMBER;
            }

            if (powerLoss <= 0) return; // No power loss configured

            int oldPower = victimFaction.getCurrentPower();
            victimFaction.removePower(powerLoss);
            int actualPowerLost = oldPower - victimFaction.getCurrentPower(); // Power loss is capped at 0

            if (actualPowerLost > 0) { // Only message if power was actually lost
                plugin.updateFactionActivity(victimFaction.getNameKey()); // Update activity on power change

                String rankName = (victimRank != null) ? victimRank.getDisplayName() : "Member";
                String deathMessage = ChatColor.RED + victim.getName() + " (" + rankName + " of " + victimFaction.getName() + ")" +
                        ChatColor.GOLD + " slain by " +
                        ChatColor.RED + killer.getName() + " (" + killerFaction.getName() + ")!" +
                        ChatColor.GOLD + " Their faction loses " + ChatColor.RED + actualPowerLost + ChatColor.GOLD + " power.";

                victim.sendMessage(ChatColor.DARK_RED + "Killed by an enemy! Your faction lost " + actualPowerLost + " power. Now: " +
                        victimFaction.getCurrentPower() + "/" + victimFaction.getMaxPowerCalculated(plugin));
                killer.sendMessage(ChatColor.GREEN + "Killed an enemy! " + victimFaction.getName() + " lost " + actualPowerLost + " power.");

                Bukkit.broadcastMessage(deathMessage);

                if (victimFaction.getCurrentPower() <= 0) {
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + victimFaction.getName() +
                            " has 0 power and is vulnerable to overclaiming!");
                }
                if (plugin.getDynmapManager() != null && plugin.getDynmapManager().isEnabled()) {
                    plugin.getDynmapManager().updateFactionAppearance(victimFaction); // Update Dynmap description
                }
            }
        }
    }
}
