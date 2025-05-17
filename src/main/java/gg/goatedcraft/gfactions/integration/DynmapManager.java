package gg.goatedcraft.gfactions.integration;

import gg.goatedcraft.gfactions.GFactionsPlugin;
import gg.goatedcraft.gfactions.data.ChunkWrapper;
import gg.goatedcraft.gfactions.data.Faction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.bukkit.ChatColor; // Keep for now

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class DynmapManager {

    private final GFactionsPlugin plugin;
    private DynmapAPI dynmapAPI;
    private MarkerAPI markerAPI;
    private MarkerSet factionMarkerSet;
    private boolean enabled = false;
    private final String MARKER_SET_ID = "goatedfactions.markerset";
    private final Map<String, List<AreaMarker>> factionAreaMarkers = new HashMap<>(); // FactionNameKey -> List of its AreaMarkers

    public DynmapManager(GFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void activate() {
        if (!plugin.DYNMAP_ENABLED) {
            plugin.getLogger().info("Dynmap integration disabled in config.");
            this.enabled = false;
            return;
        }
        Plugin dynmapPluginHook = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmapPluginHook == null || !dynmapPluginHook.isEnabled()) {
            plugin.getLogger().info("Dynmap plugin not found/enabled. Integration disabled.");
            this.enabled = false;
            return;
        }
        try {
            this.dynmapAPI = (DynmapAPI) dynmapPluginHook;
            this.markerAPI = dynmapAPI.getMarkerAPI();
            if (markerAPI == null) {
                plugin.getLogger().warning("Error obtaining Dynmap Marker API. Integration disabled.");
                this.enabled = false; return;
            }
            this.factionMarkerSet = markerAPI.getMarkerSet(MARKER_SET_ID);
            if (this.factionMarkerSet == null) {
                this.factionMarkerSet = markerAPI.createMarkerSet(MARKER_SET_ID, plugin.DYNMAP_MARKERSET_LABEL, null, false);
            } else {
                this.factionMarkerSet.setMarkerSetLabel(plugin.DYNMAP_MARKERSET_LABEL);
            }
            if (this.factionMarkerSet == null) {
                plugin.getLogger().severe("Failed to create/get Dynmap MarkerSet. Integration disabled.");
                this.enabled = false; return;
            }
            this.enabled = true;
            plugin.getLogger().info("Hooked into Dynmap. MarkerSet: '" + plugin.DYNMAP_MARKERSET_LABEL + "'.");
            updateAllFactionClaimsVisuals(); // Full redraw on activation
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error activating Dynmap integration.", e);
            this.enabled = false;
        }
    }

    public void deactivate() {
        this.enabled = false;
        if (factionMarkerSet != null) {
            factionMarkerSet.deleteMarkerSet(); // Clean up the marker set
            factionMarkerSet = null;
        }
        factionAreaMarkers.clear();
        plugin.getLogger().info("Dynmap integration deactivated.");
    }

    private String getFactionAreaMarkerId(String factionNameKey, int areaIndex) {
        return "gfactions_area_" + factionNameKey.toLowerCase(Locale.ROOT) + "_" + areaIndex;
    }

    // Called when a single claim is added or removed
    public void updateClaimOnMap(Faction faction, ChunkWrapper cwChanged) {
        if (!enabled || factionMarkerSet == null || faction == null) return;
        // For simplicity with merged polygons, just update the whole faction's visual
        updateFactionClaimsVisual(faction);
    }

    // Called when a single claim is removed
    public void removeClaimFromMap(ChunkWrapper cwRemoved) {
        if (!enabled || factionMarkerSet == null || cwRemoved == null) return;
        // Need to find which faction owned it to update that faction's visuals
        // This is tricky if the faction object is already gone or the claim is unassigned
        // A full refresh or targeted refresh of potentially affected factions is safer.
        // For now, let's assume a faction context is available or we do a broader refresh.
        // A simpler way for remove: iterate all factions, check if this cw was theirs, then update.
        // However, the call to removeClaimFromMap often comes *after* the claim is removed from the Faction object.
        // So, it's better to call updateAllFactionClaimsVisuals() or update a specific faction if known.
        // The GFactionsPlugin.unclaimChunkAdmin/Player should call updateFactionClaimsVisual for the affected faction.
    }


    public void updateAllFactionClaimsVisuals() {
        if (!enabled || factionMarkerSet == null) {
            if (plugin.DYNMAP_ENABLED) plugin.getLogger().info("Dynmap not ready for full claim visual update.");
            return;
        }
        plugin.getLogger().info("Updating all faction claims visuals on Dynmap...");

        // Clear all existing markers from our set
        factionMarkerSet.getAreaMarkers().forEach(AreaMarker::deleteMarker);
        factionAreaMarkers.clear();

        for (Faction faction : plugin.getFactionsByNameKey().values()) {
            updateFactionClaimsVisual(faction);
        }
        plugin.getLogger().info("Dynmap claims visual update complete.");
    }

    public void updateFactionClaimsVisual(Faction faction) {
        if (!enabled || factionMarkerSet == null || faction == null) return;

        // 1. Remove old markers for this faction
        List<AreaMarker> oldMarkers = factionAreaMarkers.remove(faction.getNameKey());
        if (oldMarkers != null) {
            for (AreaMarker oldMarker : oldMarkers) {
                oldMarker.deleteMarker();
            }
        }

        Set<ChunkWrapper> claims = faction.getClaimedChunks();
        if (claims.isEmpty()) {
            return; // No claims, nothing to draw
        }

        List<AreaMarker> newMarkersForFaction = new ArrayList<>();

        // 2. Group claims into contiguous areas (per world)
        Map<String, List<Set<ChunkWrapper>>> worldContiguousAreas = new HashMap<>();
        for (ChunkWrapper claim : claims) {
            worldContiguousAreas.computeIfAbsent(claim.getWorldName(), k -> new ArrayList<>())
                    .add(new HashSet<>(Collections.singletonList(claim))); // Initially, each claim is its own area
        }

        for (Map.Entry<String, List<Set<ChunkWrapper>>> worldEntry : worldContiguousAreas.entrySet()) {
            String worldName = worldEntry.getKey();
            List<Set<ChunkWrapper>> areasInWorld = mergeContiguousChunks(new ArrayList<>(faction.getClaimedChunks().stream().filter(c -> c.getWorldName().equals(worldName)).collect(Collectors.toSet())));

            int areaIndex = 0;
            for (Set<ChunkWrapper> area : areasInWorld) {
                if (area.isEmpty()) continue;

                List<Point> polygonPoints = calculateOutline(area);
                if (polygonPoints.isEmpty() || polygonPoints.size() < 3) {
                    // Fallback for very small/problematic areas: draw individual chunks
                    // This part is complex to get right for all cases, so a fallback can be useful.
                    // For now, we'll assume calculateOutline works for valid areas.
                    // If not, we might log an error or draw single chunk markers.
                    plugin.getLogger().warning("Could not form valid polygon for area in " + faction.getName() + ", world " + worldName);
                    // As a robust fallback, you could draw individual chunk markers here if polygon fails
                    for(ChunkWrapper cw : area){
                        drawIndividualChunkMarker(faction, cw, newMarkersForFaction, areaIndex++);
                    }
                    continue;
                }

                double[] xCorners = polygonPoints.stream().mapToDouble(p -> p.x).toArray();
                double[] zCorners = polygonPoints.stream().mapToDouble(p -> p.y).toArray(); // Using y for z

                String markerId = getFactionAreaMarkerId(faction.getNameKey(), areaIndex++);
                AreaMarker marker = factionMarkerSet.createAreaMarker(markerId, faction.getName(), false, worldName, xCorners, zCorners, false);

                if (marker == null) {
                    plugin.getLogger().warning("Failed to create Dynmap area marker: " + markerId + " for " + faction.getName());
                    continue;
                }

                int factionColor = getFactionDisplayColor(faction, null); // ViewingPlayer null for general view
                try {
                    marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, factionColor);
                    marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT, plugin.DYNMAP_STROKE_OPACITY, getColorFromHexString(plugin.DYNMAP_STROKE_COLOR, 0x000000));
                } catch (NumberFormatException e){
                    plugin.getLogger().warning("Error setting Dynmap marker style due to color format: " + e.getMessage());
                    marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, 0x00FF00); // Default green
                    marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT, plugin.DYNMAP_STROKE_OPACITY, 0x000000); // Default black
                }

                marker.setDescription(generatePopupDescription(faction));
                newMarkersForFaction.add(marker);
            }
        }
        factionAreaMarkers.put(faction.getNameKey(), newMarkersForFaction);
    }

    private void drawIndividualChunkMarker(Faction faction, ChunkWrapper cw, List<AreaMarker> markerList, int areaIndexOffset) {
        World world = Bukkit.getWorld(cw.getWorldName());
        if (world == null) return;

        double[] xCorners = {(double) cw.getX() * 16, (double) cw.getX() * 16 + 16, (double) cw.getX() * 16 + 16, (double) cw.getX() * 16};
        double[] zCorners = {(double) cw.getZ() * 16, (double) cw.getZ() * 16, (double) cw.getZ() * 16 + 16, (double) cw.getZ() * 16 + 16};

        String markerId = getFactionAreaMarkerId(faction.getNameKey() + "_chunk_" + cw.getX() + "_" + cw.getZ(), areaIndexOffset);
        AreaMarker marker = factionMarkerSet.createAreaMarker(markerId, faction.getName(), false, world.getName(), xCorners, zCorners, false);

        if (marker == null) {
            plugin.getLogger().warning("Failed to create individual Dynmap chunk marker: " + markerId);
            return;
        }

        int factionColor = getFactionDisplayColor(faction, null);
        marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, factionColor);
        marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT, plugin.DYNMAP_STROKE_OPACITY, getColorFromHexString(plugin.DYNMAP_STROKE_COLOR, 0x000000));
        marker.setDescription(generatePopupDescription(faction)); // Simplified description for individual chunk if needed
        markerList.add(marker);
    }


    private static class Point { int x, y; Point(int x, int y) { this.x = x; this.y = y; }}
    private static class Edge { Point p1, p2; Edge(Point p1, Point p2) { this.p1 = p1; this.p2 = p2; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; Edge edge = (Edge) o; return (Objects.equals(p1, edge.p1) && Objects.equals(p2, edge.p2)) || (Objects.equals(p1, edge.p2) && Objects.equals(p2, edge.p1)); }
        @Override public int hashCode() { return Objects.hash(p1, p2) + Objects.hash(p2, p1); } // Ensure undirected hash
    }

    // Basic polygon outline calculation (this is non-trivial for complex shapes and holes)
    // This version is a simplified approach for external boundary of a set of squares.
    private List<Point> calculateOutline(Set<ChunkWrapper> areaChunks) {
        if (areaChunks.isEmpty()) return Collections.emptyList();

        List<Edge> edges = new ArrayList<>();
        for (ChunkWrapper cw : areaChunks) {
            int x = cw.getX() * 16;
            int z = cw.getZ() * 16;
            Point p1 = new Point(x, z);          // Top-left
            Point p2 = new Point(x + 16, z);      // Top-right
            Point p3 = new Point(x + 16, z + 16);  // Bottom-right
            Point p4 = new Point(x, z + 16);     // Bottom-left

            edges.add(new Edge(p1, p2)); edges.add(new Edge(p2, p3));
            edges.add(new Edge(p3, p4)); edges.add(new Edge(p4, p1));
        }

        // Keep edges that appear only once (exterior edges)
        List<Edge> outlineEdges = new ArrayList<>();
        Map<Edge, Integer> edgeCounts = new HashMap<>();
        for (Edge e : edges) {
            edgeCounts.put(e, edgeCounts.getOrDefault(e, 0) + 1);
        }
        for (Map.Entry<Edge, Integer> entry : edgeCounts.entrySet()) {
            if (entry.getValue() == 1) {
                outlineEdges.add(entry.getKey());
            }
        }

        // Order points to form polygon (this is the very hard part for complex shapes)
        // This simplistic ordering will only work for convex hull like shapes or simple rectangles.
        // A proper solution involves graph traversal or more advanced geometric algorithms.
        if (outlineEdges.isEmpty()) return Collections.emptyList();

        List<Point> orderedPoints = new ArrayList<>();
        Edge currentEdge = outlineEdges.remove(0);
        orderedPoints.add(currentEdge.p1);
        Point currentPoint = currentEdge.p2;
        orderedPoints.add(currentPoint);

        while (!outlineEdges.isEmpty() && orderedPoints.size() < edgeCounts.size() * 2) { // Safety break
            boolean foundNext = false;
            for (int i = 0; i < outlineEdges.size(); i++) {
                Edge nextPossibility = outlineEdges.get(i);
                if (nextPossibility.p1.equals(currentPoint)) {
                    currentPoint = nextPossibility.p2;
                    orderedPoints.add(currentPoint);
                    outlineEdges.remove(i);
                    foundNext = true;
                    break;
                } else if (nextPossibility.p2.equals(currentPoint)) {
                    currentPoint = nextPossibility.p1;
                    orderedPoints.add(currentPoint);
                    outlineEdges.remove(i);
                    foundNext = true;
                    break;
                }
            }
            if (!foundNext) break; // Cannot find next connected edge, polygon might be disjointed or logic error
        }

        // Remove last point if it's same as first (closed polygon for Dynmap)
        if (orderedPoints.size() > 1 && orderedPoints.get(0).equals(orderedPoints.get(orderedPoints.size() - 1))) {
            orderedPoints.remove(orderedPoints.size() - 1);
        }
        return orderedPoints;
    }

    private List<Set<ChunkWrapper>> mergeContiguousChunks(List<ChunkWrapper> allClaimsInWorld) {
        if (allClaimsInWorld.isEmpty()) return Collections.emptyList();

        List<Set<ChunkWrapper>> contiguousAreas = new ArrayList<>();
        Set<ChunkWrapper> visited = new HashSet<>();

        for (ChunkWrapper startChunk : allClaimsInWorld) {
            if (!visited.contains(startChunk)) {
                Set<ChunkWrapper> currentArea = new HashSet<>();
                Queue<ChunkWrapper> queue = new LinkedList<>();

                queue.add(startChunk);
                visited.add(startChunk);
                currentArea.add(startChunk);

                while (!queue.isEmpty()) {
                    ChunkWrapper current = queue.poll();

                    // Check cardinal neighbors
                    int[] dX = {0, 0, 1, -1};
                    int[] dZ = {1, -1, 0, 0};

                    for (int i = 0; i < 4; i++) {
                        ChunkWrapper neighbor = new ChunkWrapper(current.getWorldName(), current.getX() + dX[i], current.getZ() + dZ[i]);
                        if (allClaimsInWorld.contains(neighbor) && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            currentArea.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                contiguousAreas.add(currentArea);
            }
        }
        return contiguousAreas;
    }


    private int getFactionDisplayColor(Faction faction, Player viewingPlayer) {
        if (faction == null) return plugin.DYNMAP_COLOR_NEUTRAL_CLAIM; // Wilderness or unowned

        if (viewingPlayer != null) {
            Faction viewerFaction = plugin.getFactionByPlayer(viewingPlayer.getUniqueId());
            if (viewerFaction != null) {
                if (viewerFaction.equals(faction)) return plugin.DYNMAP_COLOR_DEFAULT_CLAIM;
                if (viewerFaction.isAlly(faction.getNameKey())) return plugin.DYNMAP_COLOR_ALLY_CLAIM;
                if (viewerFaction.isEnemy(faction.getNameKey())) return plugin.DYNMAP_COLOR_ENEMY_CLAIM;
            }
        }
        // Default if no specific relation or no viewing player (e.g. neutral faction)
        return plugin.DYNMAP_COLOR_NEUTRAL_CLAIM;
    }

    private int getColorFromHexString(String hex, int defaultColor) {
        if (hex == null) return defaultColor;
        try {
            return Integer.decode(hex);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid hex color string '" + hex + "'. Defaulting for Dynmap stroke.");
            return defaultColor;
        }
    }

    private String generatePopupDescription(Faction faction) {
        if (faction == null) return "Wilderness";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-weight:bold;font-size:120%;color:#FFFFFF;background-color:#333333;padding:3px;\">")
                .append(ChatColor.stripColor(faction.getName())).append("</div>");
        OfflinePlayer owner = Bukkit.getOfflinePlayer(faction.getOwnerUUID());
        sb.append("Owner: ").append(owner.getName() != null ? owner.getName() : "Unknown").append("<br>");
        sb.append("Power: ").append(faction.getCurrentPower()).append(" / ").append(faction.getMaxPower()).append("<br>");
        sb.append("Members: ").append(faction.getTotalSize()).append("<br>");
        if (!faction.getAllyFactionKeys().isEmpty()) {
            sb.append("Allies: ").append(faction.getAllyFactionKeys().stream()
                    .map(key -> { Faction f = plugin.getFaction(key); return f != null ? ChatColor.stripColor(f.getName()) : key; })
                    .collect(Collectors.joining(", "))).append("<br>");
        }
        if (!faction.getEnemyFactionKeys().isEmpty()) {
            sb.append("Enemies: ").append(faction.getEnemyFactionKeys().stream()
                    .map(key -> { Faction f = plugin.getFaction(key); return f != null ? ChatColor.stripColor(f.getName()) : key; })
                    .collect(Collectors.joining(", "))).append("<br>");
        }
        sb.append("Claims: ").append(faction.getClaimedChunks().size());
        if (plugin.MAX_CLAIMS_PER_FACTION > 0) {
            sb.append(" / ").append(plugin.MAX_CLAIMS_PER_FACTION);
        }
        if (!faction.getOutposts().isEmpty()){
            sb.append("<br>Outposts: ").append(faction.getOutposts().size());
        }
        return sb.toString();
    }

    public boolean isEnabled() { return enabled; }

    public void updateFactionRelations(Faction f1, Faction f2) {
        if (!enabled) return;
        plugin.getLogger().info("Dynmap: Updating relations appearance for " + f1.getName() + " and " + f2.getName());
        updateFactionClaimsVisual(f1);
        updateFactionClaimsVisual(f2);
    }

    public void updateFactionAppearance(Faction faction) {
        if (!enabled || factionMarkerSet == null || faction == null) return;
        plugin.getLogger().info("Dynmap: Updating general appearance for " + faction.getName());
        updateFactionClaimsVisual(faction);
    }

    public void addFactionToMap(Faction faction) {
        if (!enabled || faction == null) return;
        plugin.getLogger().info("Dynmap: Faction " + faction.getName() + " registered. Claims will appear as they are made.");
        updateFactionClaimsVisual(faction); // Draw initial claims if any (like spawnblock)
    }

    public void removeFactionClaimsFromMap(Faction faction) { // Called on disband
        if (!enabled || factionMarkerSet == null || faction == null) return;
        List<AreaMarker> markers = factionAreaMarkers.remove(faction.getNameKey());
        if (markers != null) {
            for (AreaMarker marker : markers) {
                marker.deleteMarker();
            }
        }
        plugin.getLogger().info("Dynmap: All visual claims removed from map for disbanded faction: " + faction.getName());
    }
}