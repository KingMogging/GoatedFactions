package gg.goatedcraft.gfactions.listeners;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.Faction;
import gg.goatedcraft.gfactions.data.FactionRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location; // Added missing import
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class ZoneProtectionListener implements Listener {

    private final GFactionsPlugin plugin;

    private static final Set<Material> MEMBER_PLUS_INTERACT_ONLY_MATERIALS = new HashSet<>(Arrays.asList(
            Material.CHEST, Material.TRAPPED_CHEST, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BARREL, Material.SHULKER_BOX, Material.DISPENSER, Material.DROPPER, Material.HOPPER,
            Material.BREWING_STAND, Material.JUKEBOX, Material.BEACON,
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE, Material.ENDER_CHEST
    ));

    private static final Set<Material> ASSOCIATE_ALLOWED_INTERACT_MATERIALS = new HashSet<>(Arrays.asList(
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
            Material.CRIMSON_BUTTON, Material.WARPED_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,
            Material.NOTE_BLOCK
    ));


    public ZoneProtectionListener(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean canPlayerPerformAction(Player player, Faction owningFaction, boolean isBuildBreak, @Nullable Material interactedMaterial) {
        if (player.hasPermission("goatedfactions.admin.bypass")) return true;
        if (owningFaction == null) return true; // Wilderness

        Faction playerFaction = plugin.getFactionByPlayer(player.getUniqueId());

        if (playerFaction != null && playerFaction.equals(owningFaction)) {
            FactionRank rank = playerFaction.getRank(player.getUniqueId());
            if (rank == null) return false;

            if (isBuildBreak) {
                return rank.isMemberOrHigher();
            } else if (interactedMaterial != null) {
                if (rank.isMemberOrHigher()) {
                    return true;
                } else if (rank == FactionRank.ASSOCIATE) {
                    if (MEMBER_PLUS_INTERACT_ONLY_MATERIALS.contains(interactedMaterial)) {
                        return false;
                    }
                    if (clickedBlockIsInventoryHolder(interactedMaterial) && !ASSOCIATE_ALLOWED_INTERACT_MATERIALS.contains(interactedMaterial)) {
                        return false;
                    }
                    return true;
                }
            }
            return false;
        }

        if (owningFaction.isTrusted(player.getUniqueId())) {
            return true;
        }

        return false;
    }

    private boolean clickedBlockIsInventoryHolder(Material material) {
        try {
            if (Bukkit.getWorlds().isEmpty()) {
                plugin.getLogger().warning("No worlds loaded, cannot check InventoryHolder status for material: " + material);
                return false;
            }
            World world = Bukkit.getWorlds().get(0);
            Location tempLoc = new Location(world, 0, world.getMinHeight() + 64, 0); // Ensure Location is org.bukkit.Location

            // Check if the chunk is loaded before getting the block to avoid loading it.
            // However, for a simple type check, this might be less critical than for operations that cause updates.
            // Block block = world.getBlockAt(tempLoc.getBlockX(), tempLoc.getBlockY(), tempLoc.getBlockZ()); // Alternative way to get block
            Block block = tempLoc.getBlock(); // Simpler way to get block from Location

            Material originalType = block.getType();
            // Temporarily set the type to check its state. This is a common workaround.
            // Be cautious if other plugins listen to BlockPhysicsEvent or similar, though 'false' should prevent most updates.
            block.setType(material, false);
            boolean isHolder = block.getState() instanceof InventoryHolder;
            block.setType(originalType, false); // Revert to original type
            return isHolder;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking if material is InventoryHolder: " + material, e);
            return false;
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        String ownerNameKey = plugin.getFactionOwningChunk(chunk);
        Faction owningFaction = (ownerNameKey != null) ? plugin.getFaction(ownerNameKey) : null;

        if (owningFaction == null && ownerNameKey != null) {
            plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ() + " in world " + chunk.getWorld().getName() +
                    " is mapped to a non-existent faction key: " + ownerNameKey + ". Allowing action as fallback.");
            return;
        }

        if (owningFaction != null) {
            if (!canPlayerPerformAction(player, owningFaction, true, event.getBlock().getType())) {
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
        Faction owningFaction = (ownerNameKey != null) ? plugin.getFaction(ownerNameKey) : null;

        if (owningFaction == null && ownerNameKey != null) {
            plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ() + " in world " + chunk.getWorld().getName() +
                    " is mapped to a non-existent faction key: " + ownerNameKey + ". Allowing action as fallback.");
            return;
        }

        if (owningFaction != null) {
            if (!canPlayerPerformAction(player, owningFaction, true, event.getBlock().getType())) {
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

        boolean isPotentiallyProtectedInteraction = MEMBER_PLUS_INTERACT_ONLY_MATERIALS.contains(clickedBlock.getType()) ||
                ASSOCIATE_ALLOWED_INTERACT_MATERIALS.contains(clickedBlock.getType()) ||
                (clickedBlock.getState() instanceof InventoryHolder);

        if (!isPotentiallyProtectedInteraction) return;

        Chunk chunk = clickedBlock.getChunk();
        String ownerNameKey = plugin.getFactionOwningChunk(chunk);
        Faction owningFaction = (ownerNameKey != null) ? plugin.getFaction(ownerNameKey) : null;

        if (owningFaction == null && ownerNameKey != null) {
            plugin.getLogger().warning("Chunk " + chunk.getX() + "," + chunk.getZ() + " in world " + chunk.getWorld().getName() +
                    " is mapped to a non-existent faction key: " + ownerNameKey + ". Allowing action as fallback.");
            return;
        }

        if (owningFaction != null) {
            if (!canPlayerPerformAction(player, owningFaction, false, clickedBlock.getType())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot interact here in " + owningFaction.getName() + "'s territory.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        Faction victimFaction = plugin.getFactionOwningChunkAsFaction(victim.getLocation().getChunk());

        if (victimFaction != null && victimFaction.isPvpProtected()) {
            Faction attackerFaction = plugin.getFactionByPlayer(attacker.getUniqueId());

            if (victimFaction.equals(attackerFaction)) {
                return;
            }

            if (plugin.ENEMY_SYSTEM_ENABLED && attackerFaction != null && victimFaction.isAlly(attackerFaction.getNameKey())) {
                return;
            }

            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "You cannot attack players in " + victimFaction.getName() + "'s territory as it is PvP protected.");
        }
    }
}
