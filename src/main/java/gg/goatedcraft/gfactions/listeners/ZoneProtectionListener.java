package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ZoneProtectionListener implements Listener {

    private final GFactionsPlugin plugin;
    private static final Set<Material> PROTECTED_INTERACT_MATERIALS = new HashSet<>(Arrays.asList(
            Material.CHEST, Material.TRAPPED_CHEST, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BARREL, Material.SHULKER_BOX, Material.DISPENSER, Material.DROPPER, Material.HOPPER,
            Material.BREWING_STAND, Material.JUKEBOX, Material.NOTE_BLOCK, Material.BEACON,
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE, Material.ENDER_CHEST,
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
            Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.IRON_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE,
            Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON,
            Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON, Material.BAMBOO_BUTTON,
            Material.CRIMSON_BUTTON, Material.WARPED_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON
    ));

    public ZoneProtectionListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canBypassProtection(Player player, Faction owningFaction) {
        if (player.hasPermission("goatedfactions.admin.bypass")) return true;
        if (owningFaction == null) return true; // Wilderness

        Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());
        if (playerFaction != null && playerFaction.equals(owningFaction)) return true;
        if (owningFaction.isTrusted(player.getUniqueId())) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        String ownerNameKey = plugin.getFactionOwningChunk(chunk);
        if (ownerNameKey != null) {
            Faction owningFaction = plugin.getFaction(ownerNameKey);
            if (owningFaction == null) {
                plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ() + " in world " + chunk.getWorld().getName() +
                        " is mapped to a non-existent faction key: " + ownerNameKey + ". Allowing action as fallback.");
                return;
            }
            if (!canBypassProtection(player, owningFaction)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot break blocks in " + owningFaction.getName() + "'s territory.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        String ownerNameKey = plugin.getFactionOwningChunk(chunk);
        if (ownerNameKey != null) {
            Faction owningFaction = plugin.getFaction(ownerNameKey);
            if (owningFaction == null) {
                plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ() + " in world " + chunk.getWorld().getName() +
                        " is mapped to a non-existent faction key: " + ownerNameKey + ". Allowing action as fallback.");
                return;
            }
            if (!canBypassProtection(player, owningFaction)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot place blocks in " + owningFaction.getName() + "'s territory.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        boolean isProtectedType = PROTECTED_INTERACT_MATERIALS.contains(clickedBlock.getType()) ||
                (clickedBlock.getState() instanceof InventoryHolder);

        if (!isProtectedType) return;

        Chunk chunk = clickedBlock.getChunk();
        String ownerNameKey = plugin.getFactionOwningChunk(chunk);
        if (ownerNameKey != null) {
            Faction owningFaction = plugin.getFaction(ownerNameKey);
            if (owningFaction == null) {
                plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ() + " in world " + chunk.getWorld().getName() +
                        " is mapped to a non-existent faction key: " + ownerNameKey + ". Allowing action as fallback.");
                return;
            }
            if (!canBypassProtection(player, owningFaction)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot interact here in " + owningFaction.getName() + "'s territory.");
            }
        }
    }
}
