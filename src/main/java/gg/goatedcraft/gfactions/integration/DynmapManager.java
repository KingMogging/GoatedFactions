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
import org.bukkit.ChatColor;
import org.jetbrains.annotations.Nullable;

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
    private final Map<String, List<String>> factionMarkerIds = new HashMap<>();

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
            updateAllFactionClaimsVisuals();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error activating Dynmap integration.", e);
            this.enabled = false;
        }
    }

    public void deactivate() {
        this.enabled = false;
        // Do not delete marker set on disable, so it persists if plugin re-enables
        factionMarkerIds.clear();
        plugin.getLogger().info("Dynmap integration deactivated.");
    }

    private String getFactionAreaMarkerId(String factionNameKey, String worldName, int areaIndex) {
        return "gfactions_area_" + factionNameKey.toLowerCase(Locale.ROOT) + "_" + worldName.toLowerCase(Locale.ROOT) + "_" + areaIndex;
    }

    public void updateAllFactionClaimsVisuals() {
        if (!enabled || factionMarkerSet == null) {
            if (plugin.DYNMAP_ENABLED) plugin.getLogger().fine("Dynmap not ready for full claim visual update."); // Changed to fine
            return;
        }
        plugin.getLogger().info("Updating all faction claims visuals on Dynmap...");
        // Clear existing faction markers from this set
        factionMarkerSet.getAreaMarkers().stream()
                .filter(marker -> marker.getMarkerID().startsWith("gfactions_area_"))
                .forEach(AreaMarker::deleteMarker);
        factionMarkerIds.clear(); // Clear local tracking

        for (Faction faction : plugin.getFactionsByNameKey().values()) {
            updateFactionClaimsVisual(faction);
        }
        plugin.getLogger().info("Dynmap claims visual update complete.");
    }

    public void updateFactionClaimsVisual(Faction faction) {
        if (!enabled || factionMarkerSet == null || faction == null) return;

        // Remove old markers for this specific faction
        List<String> oldMarkerIdsForFaction = factionMarkerIds.remove(faction.getNameKey());
        if (oldMarkerIdsForFaction != null) {
            for (String markerId : oldMarkerIdsForFaction) {
                AreaMarker oldMarker = factionMarkerSet.findAreaMarker(markerId);
                if (oldMarker != null) {
                    oldMarker.deleteMarker();
                }
            }
        }

        Set<ChunkWrapper> claims = faction.getClaimedChunks();
        if (claims.isEmpty()) {
            return; // No claims, nothing to draw
        }

        List<String> newMarkerIdsForThisFaction = new ArrayList<>();
        Map<String, Set<ChunkWrapper>> claimsByWorld = new HashMap<>();
        for (ChunkWrapper claim : claims) {
            claimsByWorld.computeIfAbsent(claim.getWorldName(), k -> new HashSet<>()).add(claim);
        }

        for (Map.Entry<String, Set<ChunkWrapper>> worldEntry : claimsByWorld.entrySet()) {
            String worldName = worldEntry.getKey();
            Set<ChunkWrapper> claimsInWorld = worldEntry.getValue();
            if (claimsInWorld.isEmpty()) continue;

            List<Set<ChunkWrapper>> contiguousAreas = mergeContiguousChunks(new ArrayList<>(claimsInWorld));
            int areaIndex = 0;
            for (Set<ChunkWrapper> area : contiguousAreas) {
                if (area.isEmpty()) continue;

                List<Point> polygonPoints = calculateOutline(area);
                if (polygonPoints.isEmpty() || polygonPoints.size() < 3) {
                    for(ChunkWrapper cw_fallback : area){
                        drawIndividualChunkMarker(faction, cw_fallback, newMarkerIdsForThisFaction, worldName, areaIndex++);
                    }
                    continue;
                }
                double[] xCorners = polygonPoints.stream().mapToDouble(p -> p.x).toArray();
                double[] zCorners = polygonPoints.stream().mapToDouble(p -> p.y).toArray();

                String markerId = getFactionAreaMarkerId(faction.getNameKey(), worldName, areaIndex++);
                AreaMarker marker = factionMarkerSet.createAreaMarker(markerId, faction.getName(), false, worldName, xCorners, zCorners, false);
                if (marker == null) {
                    plugin.getLogger().warning("Failed to create Dynmap area marker: " + markerId + " for " + faction.getName());
                    continue;
                }

                int factionColor = getFactionDisplayColor(faction, null); // Viewing player null for general display
                try {
                    marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, factionColor);
                    marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT, plugin.DYNMAP_STROKE_OPACITY, getColorFromHexString(plugin.DYNMAP_STROKE_COLOR, 0x000000));
                } catch (NumberFormatException e){
                    plugin.getLogger().warning("Error setting Dynmap marker style due to color format: " + e.getMessage());
                    marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, 0x00FF00); // Default green
                    marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT, plugin.DYNMAP_STROKE_OPACITY, 0x000000); // Default black
                }

                marker.setDescription(generatePopupDescription(faction));
                newMarkerIdsForThisFaction.add(markerId);
            }
        }
        if (!newMarkerIdsForThisFaction.isEmpty()) {
            factionMarkerIds.put(faction.getNameKey(), newMarkerIdsForThisFaction);
        }
    }

    private void drawIndividualChunkMarker(Faction faction, ChunkWrapper cw, List<String> markerIdList, String worldName, int uniqueIdx) {
        double[] xCorners = {(double) cw.getX() * 16, (double) cw.getX() * 16 + 16, (double) cw.getX() * 16 + 16, (double) cw.getX() * 16};
        double[] zCorners = {(double) cw.getZ() * 16, (double) cw.getZ() * 16, (double) cw.getZ() * 16 + 16, (double) cw.getZ() * 16 + 16};
        String markerId = getFactionAreaMarkerId(faction.getNameKey() + "_fbck_" + cw.getX() + "_" + cw.getZ(), worldName, uniqueIdx);

        AreaMarker marker = factionMarkerSet.findAreaMarker(markerId);
        if (marker == null) {
            marker = factionMarkerSet.createAreaMarker(markerId, faction.getName(), false, worldName, xCorners, zCorners, false);
        } else {
            marker.setCornerLocations(xCorners, zCorners); // Update existing
            marker.setLabel(faction.getName());
        }

        if (marker == null) {
            plugin.getLogger().warning("Failed to create/update individual Dynmap chunk marker: " + markerId);
            return;
        }

        int factionColor = getFactionDisplayColor(faction, null);
        marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, factionColor);
        marker.setLineStyle(Math.max(1, plugin.DYNMAP_STROKE_WEIGHT / 2), plugin.DYNMAP_STROKE_OPACITY / 1.5, factionColor);
        marker.setDescription(generatePopupDescription(faction));
        markerIdList.add(markerId);
    }

    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
        @Override public String toString() { return "(" + x + "," + y + ")"; }
    }

    private static class Edge {
        Point p1, p2;
        Edge(Point p1, Point p2) { this.p1 = p1; this.p2 = p2; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Edge edge = (Edge) o;
            return (p1.equals(edge.p1) && p2.equals(edge.p2)) || (p1.equals(edge.p2) && p2.equals(edge.p1));
        }
        @Override public int hashCode() { return p1.hashCode() ^ p2.hashCode(); }
        @Override public String toString() { return p1.toString() + " -> " + p2.toString(); }
    }

    private List<Point> calculateOutline(Set<ChunkWrapper> areaChunks) {
        if (areaChunks.isEmpty()) return Collections.emptyList();
        Map<Edge, Integer> edgeCounts = new HashMap<>();
        for (ChunkWrapper cw : areaChunks) {
            int x = cw.getX() * 16;
            int z = cw.getZ() * 16;
            Point pNW = new Point(x, z);
            Point pNE = new Point(x + 16, z);
            Point pSE = new Point(x + 16, z + 16);
            Point pSW = new Point(x, z + 16);

            Edge top = new Edge(pNW, pNE);
            Edge right = new Edge(pNE, pSE);
            Edge bottom = new Edge(pSW, pSE);
            Edge left = new Edge(pNW, pSW);

            edgeCounts.put(top, edgeCounts.getOrDefault(top, 0) + 1);
            edgeCounts.put(right, edgeCounts.getOrDefault(right, 0) + 1);
            edgeCounts.put(bottom, edgeCounts.getOrDefault(bottom, 0) + 1);
            edgeCounts.put(left, edgeCounts.getOrDefault(left, 0) + 1);
        }

        List<Edge> outlineEdges = new ArrayList<>();
        for (Map.Entry<Edge, Integer> entry : edgeCounts.entrySet()) {
            if (entry.getValue() == 1) {
                outlineEdges.add(entry.getKey());
            }
        }

        if (outlineEdges.isEmpty()) return Collections.emptyList();

        List<Point> orderedPoints = new ArrayList<>();
        Edge currentEdge = outlineEdges.remove(0);
        orderedPoints.add(currentEdge.p1);
        Point currentPoint = currentEdge.p2;
        orderedPoints.add(currentPoint);

        int safetyBreak = outlineEdges.size() + 2;
        while (!outlineEdges.isEmpty() && safetyBreak-- > 0) {
            boolean foundNext = false;
            for (int i = 0; i < outlineEdges.size(); i++) {
                Edge nextPossibleEdge = outlineEdges.get(i);
                if (nextPossibleEdge.p1.equals(currentPoint)) {
                    currentPoint = nextPossibleEdge.p2;
                    orderedPoints.add(currentPoint);
                    outlineEdges.remove(i);
                    foundNext = true;
                    break;
                } else if (nextPossibleEdge.p2.equals(currentPoint)) {
                    currentPoint = nextPossibleEdge.p1;
                    orderedPoints.add(currentPoint);
                    outlineEdges.remove(i);
                    foundNext = true;
                    break;
                }
            }
            if (!foundNext) {
                break;
            }
        }

        if (orderedPoints.size() > 1 && orderedPoints.get(0).equals(orderedPoints.get(orderedPoints.size() - 1))) {
            orderedPoints.remove(orderedPoints.size() - 1);
        }

        if (orderedPoints.size() < 3) {
            return Collections.emptyList();
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

    private int getFactionDisplayColor(Faction faction, @Nullable Player viewingPlayer) {
        if (faction == null) return plugin.DYNMAP_COLOR_NEUTRAL_CLAIM;

        // NEW: Prioritize the faction's custom color if it's set
        String customColorHex = faction.getDynmapColorHex();
        if (customColorHex != null) {
            return getColorFromHexString(customColorHex, plugin.DYNMAP_COLOR_DEFAULT_CLAIM);
        }

        if (viewingPlayer != null) {
            Faction viewerFaction = plugin.getFactionByPlayer(viewingPlayer.getUniqueId());
            if (viewerFaction != null) {
                if (viewerFaction.equals(faction)) return plugin.DYNMAP_COLOR_DEFAULT_CLAIM;
                if (plugin.ALLY_CHAT_ENABLED && viewerFaction.isAlly(faction.getNameKey())) return plugin.DYNMAP_COLOR_ALLY_CLAIM;
                if (plugin.ENEMY_SYSTEM_ENABLED && viewerFaction.isEnemy(faction.getNameKey())) return plugin.DYNMAP_COLOR_ENEMY_CLAIM;
            }
        }
        // Fallback for non-related factions or if viewingPlayer is null
        return plugin.DYNMAP_COLOR_NEUTRAL_CLAIM;
    }

    private int getColorFromHexString(String hex, int defaultColor) {
        if (hex == null || hex.trim().isEmpty()) return defaultColor;
        try {
            return Integer.decode(hex.trim());
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid hex color string '" + hex + "'. Defaulting for Dynmap stroke. Error: " + e.getMessage());
            return defaultColor;
        }
    }

    private String generatePopupDescription(Faction faction) {
        if (faction == null) return "Wilderness";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-weight:bold;font-size:120%;color:#FFFFFF;background-color:#333333;padding:3px;\">")
                .append(ChatColor.stripColor(faction.getName())).append("</div>");

        if (plugin.DESCRIPTION_ENABLED && faction.getDescription() != null && !faction.getDescription().isEmpty()) {
            sb.append(ChatColor.stripColor(faction.getDescription())).append("<br>");
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(faction.getOwnerUUID());
        sb.append("Owner: ").append(owner.getName() != null ? owner.getName() : "Unknown").append("<br>");
        sb.append("Power: ").append(faction.getCurrentPower()).append(" / ").append(faction.getMaxPowerCalculated(plugin)).append("<br>");
        sb.append("Members: ").append(faction.getTotalSize()).append("<br>");
        if (plugin.ALLY_CHAT_ENABLED && !faction.getAllyFactionKeys().isEmpty()) {
            sb.append("Allies: ").append(faction.getAllyFactionKeys().stream()
                    .map(key -> { Faction f = plugin.getFaction(key); return f != null ? ChatColor.stripColor(f.getName()) : key; })
                    .collect(Collectors.joining(", "))).append("<br>");
        }
        if (plugin.ENEMY_SYSTEM_ENABLED && !faction.getEnemyFactionKeys().isEmpty()) {
            sb.append("Enemies: ").append(faction.getEnemyFactionKeys().stream()
                    .map(key -> { Faction f = plugin.getFaction(key); return f != null ? ChatColor.stripColor(f.getName()) : key; })
                    .collect(Collectors.joining(", "))).append("<br>");
        }
        int maxClaims = faction.getMaxClaimsCalculated(plugin);
        sb.append("Claims: ").append(faction.getClaimedChunks().size());
        if (maxClaims != Integer.MAX_VALUE) {
            sb.append(" / ").append(maxClaims);
        }

        if (plugin.OUTPOST_SYSTEM_ENABLED && !faction.getOutposts().isEmpty()){
            sb.append("<br>Outposts: ").append(faction.getOutposts().size());
            if (plugin.MAX_OUTPOSTS_PER_FACTION > 0) {
                sb.append(" / ").append(plugin.MAX_OUTPOSTS_PER_FACTION);
            }
        }
        return sb.toString();
    }

    public boolean isEnabled() { return enabled; }

    public void updateFactionRelations(Faction f1, Faction f2) {
        if (!enabled) return;
        updateFactionClaimsVisual(f1);
        updateFactionClaimsVisual(f2);
    }

    public void updateFactionAppearance(Faction faction) {
        if (!enabled || factionMarkerSet == null || faction == null) return;
        updateFactionClaimsVisual(faction);
    }

    public void addFactionToMap(Faction faction) {
        if (!enabled || faction == null) return;
        updateFactionClaimsVisual(faction);
    }

    public void removeFactionClaimsFromMap(Faction faction) {
        if (!enabled || factionMarkerSet == null || faction == null) return;
        List<String> markerIds = factionMarkerIds.remove(faction.getNameKey());
        if (markerIds != null) {
            for (String markerId : markerIds) {
                AreaMarker marker = factionMarkerSet.findAreaMarker(markerId);
                if (marker != null) {
                    marker.deleteMarker();
                }
            }
        }
    }

    public void updateMapForUnclaim(Faction faction, ChunkWrapper unclaimedChunk) {
        if (!enabled || faction == null || unclaimedChunk == null) return;
        updateFactionClaimsVisual(faction);
    }
}
