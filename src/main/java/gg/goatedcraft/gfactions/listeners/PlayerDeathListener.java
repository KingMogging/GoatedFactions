package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
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
        Player killer = victim.getKiller();

        if (killer == null) return;

        Faction victimFaction = plugin.getFactionByPlayer(victim.getUniqueId());
        Faction killerFaction = plugin.getFactionByPlayer(killer.getUniqueId());

        if (victimFaction == null || killerFaction == null || victimFaction.equals(killerFaction)) return;

        if (victimFaction.isEnemy(killerFaction.getNameKey())) {
            int powerLoss = plugin.POWER_LOSS_ON_DEATH_BY_ENEMY;
            victimFaction.removePower(powerLoss);

            String deathMessage = ChatColor.RED + victim.getName() + " (" + victimFaction.getName() + ")" +
                    ChatColor.GOLD + " slain by " +
                    ChatColor.RED + killer.getName() + " (" + killerFaction.getName() + ")!" +
                    ChatColor.GOLD + " Faction loses " + ChatColor.RED + powerLoss + ChatColor.GOLD + " power.";

            victim.sendMessage(ChatColor.DARK_RED + "Killed by enemy! Faction lost " + powerLoss + " power. Now: " +
                    victimFaction.getCurrentPower() + "/" + victimFaction.getMaxPower());
            killer.sendMessage(ChatColor.GREEN + "Killed enemy! " + victimFaction.getName() + " lost " + powerLoss + " power.");

            Bukkit.broadcastMessage(deathMessage);

            if (victimFaction.getCurrentPower() <= 0) {
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + victimFaction.getName() +
                        " has 0 power and is vulnerable to overclaiming!");
            }
        }
    }
}
