package gg.goatedcraft.gfactions.integration;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FactionPlaceholders extends PlaceholderExpansion {

    private final GFactionsPlugin plugin;

    public FactionPlaceholders(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "gfactions";
    }

    @Override
    public @NotNull String getAuthor() {
        return "GoatedCraft";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

        if (params.equalsIgnoreCase("name")) {
            return playerFaction != null ? playerFaction.getName() : "";
        }

        if (params.equalsIgnoreCase("tag")) {
            if (playerFaction == null) return "";
            return playerFaction.getName().substring(0, Math.min(playerFaction.getName().length(), plugin.FACTION_TAG_LENGTH));
        }

        // Example: %gfactions_prefix%
        if (params.equalsIgnoreCase("prefix")) {
            if (playerFaction == null) return "";

            ChatColor factionColor = plugin.getFactionRelationColor(playerFaction, playerFaction); // "self" color

            return plugin.PUBLIC_CHAT_PREFIX_FORMAT
                    .replace("{FACTION_NAME}", factionColor + playerFaction.getName() + ChatColor.RESET);
        }

        // Example: %gfactions_tag_prefix%
        if (params.equalsIgnoreCase("tag_prefix")) {
            if (playerFaction == null) return "";

            ChatColor factionColor = plugin.getFactionRelationColor(playerFaction, playerFaction); // "self" color
            String tag = playerFaction.getName().substring(0, Math.min(playerFaction.getName().length(), plugin.FACTION_TAG_LENGTH));

            return plugin.PUBLIC_CHAT_TAG_FORMAT
                    .replace("{FACTION_TAG_COLOR}", factionColor.toString())
                    .replace("{FACTION_TAG}", tag);
        }

        if (playerFaction == null) {
            return ""; // Return empty for all other placeholders if no faction
        }

        switch (params.toLowerCase()) {
            case "power":
                return String.valueOf(playerFaction.getCurrentPower());
            case "power_max":
                return String.valueOf(playerFaction.getMaxPowerCalculated(plugin));
            case "claims":
                return String.valueOf(playerFaction.getClaimedChunks().size());
            case "claims_max":
                int maxClaims = playerFaction.getMaxClaimsCalculated(plugin);
                return maxClaims == Integer.MAX_VALUE ? "Unlimited" : String.valueOf(maxClaims);
            case "members_online":
                return String.valueOf(playerFaction.getMemberUUIDsOnly().stream().filter(uuid -> plugin.getServer().getPlayer(uuid) != null).count());
            case "members_total":
                return String.valueOf(playerFaction.getTotalSize());
            default:
                return null; // Placeholder is unknown
        }
    }
}
