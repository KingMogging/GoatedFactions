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
import org.jetbrains.annotations.Nullable; // Added import for @Nullable

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
        factionMarkerIds.clear(); // Clear local tracking
        plugin.getLogger().info("Dynmap integration deactivated.");
    }

    private String getFactionAreaMarkerId(String factionNameKey, String worldName, int areaIndex) {
        return "gfactions_area_" + factionNameKey.toLowerCase(Locale.ROOT) + "_" + worldName.toLowerCase(Locale.ROOT) + "_" + areaIndex;
    }

    public void updateAllFactionClaimsVisuals() {
        if (!enabled || factionMarkerSet == null) {
            if (plugin.DYNMAP_ENABLED) plugin.getLogger().info("Dynmap not ready for full claim visual update.");
            return;
        }
        plugin.getLogger().info("Updating all faction claims visuals on Dynmap...");

        for (AreaMarker marker : new ArrayList<>(factionMarkerSet.getAreaMarkers())) {
            if (marker.getMarkerID().startsWith("gfactions_area_")) {
                marker.deleteMarker();
            }
        }
        factionMarkerIds.clear();

        for (Faction faction : plugin.getFactionsByNameKey().values()) {
            updateFactionClaimsVisual(faction);
        }
        plugin.getLogger().info("Dynmap claims visual update complete.");
    }

    public void updateFactionClaimsVisual(Faction faction) {
        if (!enabled || factionMarkerSet == null || faction == null) return;
        plugin.getLogger().info("Updating Dynmap visuals for faction: " + faction.getName());

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
            return;
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
            // plugin.getLogger().info("Faction " + faction.getName() + " in world " + worldName + " has " + contiguousAreas.size() + " contiguous area(s).");

            int areaIndex = 0;
            for (Set<ChunkWrapper> area : contiguousAreas) {
                if (area.isEmpty()) continue;
                // plugin.getLogger().info("Processing area with " + area.size() + " chunks for " + faction.getName());

                List<Point> polygonPoints = calculateOutline(area);

                if (polygonPoints.isEmpty() || polygonPoints.size() < 3) {
                    plugin.getLogger().warning("Could not form valid polygon for an area in " + faction.getName() + ", world " + worldName + ". Area chunks: " + area.stream().map(ChunkWrapper::toStringShort).collect(Collectors.joining(", ")));
                    for(ChunkWrapper cw_fallback : area){ // Fallback drawing individual chunks
                        drawIndividualChunkMarker(faction, cw_fallback, newMarkerIdsForThisFaction, worldName, areaIndex++);
                    }
                    continue;
                }
                // plugin.getLogger().fine("Polygon points for " + faction.getName() + " area " + areaIndex + ": " + polygonPoints.stream().map(p -> "("+p.x+","+p.y+")").collect(Collectors.joining(" ")));

                double[] xCorners = polygonPoints.stream().mapToDouble(p -> p.x).toArray();
                double[] zCorners = polygonPoints.stream().mapToDouble(p -> p.y).toArray();

                String markerId = getFactionAreaMarkerId(faction.getNameKey(), worldName, areaIndex++);
                AreaMarker marker = factionMarkerSet.createAreaMarker(markerId, faction.getName(), false, worldName, xCorners, zCorners, false);

                if (marker == null) {
                    plugin.getLogger().warning("Failed to create Dynmap area marker: " + markerId + " for " + faction.getName());
                    continue;
                }

                int factionColor = getFactionDisplayColor(faction, null);
                try {
                    marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, factionColor);
                    marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT, plugin.DYNMAP_STROKE_OPACITY, getColorFromHexString(plugin.DYNMAP_STROKE_COLOR, 0x000000));
                } catch (NumberFormatException e){
                    plugin.getLogger().warning("Error setting Dynmap marker style due to color format: " + e.getMessage());
                    marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, 0x00FF00);
                    marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT, plugin.DYNMAP_STROKE_OPACITY, 0x000000);
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

        String markerId = getFactionAreaMarkerId(faction.getNameKey() + "_fbck_" + cw.getX() + "_" + cw.getZ(), worldName, uniqueIdx); // Made ID more unique for fallback
        AreaMarker marker = factionMarkerSet.findAreaMarker(markerId);
        if (marker == null) {
            marker = factionMarkerSet.createAreaMarker(markerId, faction.getName(), false, worldName, xCorners, zCorners, false);
        } else {
            marker.setCornerLocations(xCorners, zCorners);
            marker.setLabel(faction.getName());
        }

        if (marker == null) {
            plugin.getLogger().warning("Failed to create/update individual Dynmap chunk marker: " + markerId);
            return;
        }

        int factionColor = getFactionDisplayColor(faction, null);
        marker.setFillStyle(plugin.DYNMAP_FILL_OPACITY, factionColor);
        // Corrected line: DYNMAP_STROKE_WEIGHT is int, division by int 2 results in int.
        marker.setLineStyle(plugin.DYNMAP_STROKE_WEIGHT / 2, plugin.DYNMAP_STROKE_OPACITY / 2.0, factionColor);
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
        @Override public int hashCode() {
            return p1.hashCode() ^ p2.hashCode();
        }
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

        if (outlineEdges.isEmpty()) {
            // plugin.getLogger().fine("No outline edges found for area with " + areaChunks.size() + " chunks.");
            return Collections.emptyList();
        }
        // plugin.getLogger().fine("Found " + outlineEdges.size() + " outline edges: " + outlineEdges);

        List<Point> orderedPoints = new ArrayList<>();
        if (outlineEdges.isEmpty()) return orderedPoints;

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
                // plugin.getLogger().warning("Could not find next connected edge for outline. Points so far: " + orderedPoints + ". Remaining edges: " + outlineEdges);
                break;
            }
        }
        // if (safetyBreak <= 0) plugin.getLogger().warning("Outline calculation hit safety break.");

        if (orderedPoints.size() > 1 && orderedPoints.get(0).equals(orderedPoints.get(orderedPoints.size() - 1))) {
            orderedPoints.remove(orderedPoints.size() - 1);
        }

        if (orderedPoints.size() < 3) {
            // plugin.getLogger().warning("Outline resulted in less than 3 points, cannot form an area: " + orderedPoints);
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

    private int getFactionDisplayColor(Faction faction, @Nullable Player viewingPlayer) { // @Nullable is now resolved
        if (faction == null) return plugin.DYNMAP_COLOR_NEUTRAL_CLAIM;

        if (viewingPlayer != null) {
            Faction viewerFaction = plugin.getFactionByPlayer(viewingPlayer.getUniqueId());
            if (viewerFaction != null) {
                if (viewerFaction.equals(faction)) return plugin.DYNMAP_COLOR_DEFAULT_CLAIM;
                if (viewerFaction.isAlly(faction.getNameKey())) return plugin.DYNMAP_COLOR_ALLY_CLAIM;
                if (viewerFaction.isEnemy(faction.getNameKey())) return plugin.DYNMAP_COLOR_ENEMY_CLAIM;
            }
        }
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
        // plugin.getLogger().info("Dynmap: Updating relations appearance for " + f1.getName() + " and " + f2.getName());
        updateFactionClaimsVisual(f1);
        updateFactionClaimsVisual(f2);
    }

    public void updateFactionAppearance(Faction faction) {
        if (!enabled || factionMarkerSet == null || faction == null) return;
        // plugin.getLogger().info("Dynmap: Updating general appearance for " + faction.getName());
        updateFactionClaimsVisual(faction);
    }

    public void addFactionToMap(Faction faction) {
        if (!enabled || faction == null) return;
        // plugin.getLogger().info("Dynmap: Faction " + faction.getName() + " registered. Initial claims will be drawn.");
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
        // plugin.getLogger().info("Dynmap: All visual claims removed from map for disbanded faction: " + faction.getName());
    }

    public void updateMapForUnclaim(Faction faction, ChunkWrapper unclaimedChunk) {
        if (!enabled || faction == null || unclaimedChunk == null) return; // Added null check for unclaimedChunk
        // plugin.getLogger().info("Dynmap: Updating map after unclaim of " + unclaimedChunk.toStringShort() + " by " + faction.getName());
        updateFactionClaimsVisual(faction);
    }
}
