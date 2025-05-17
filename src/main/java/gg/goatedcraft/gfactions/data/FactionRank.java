package gg.goatedcraft.gfactions.data;

public enum FactionRank {
    MEMBER("Member"),
    ADMIN("Admin"),
    OWNER("Owner");

    private final String displayName;

    FactionRank(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets a FactionRank from its string name, case-insensitive.
     * Defaults to MEMBER if the string is not a valid rank name.
     * @param text The string representation of the rank.
     * @return The corresponding FactionRank, or MEMBER if not found.
     */
    public static FactionRank fromString(String text) {
        if (text == null) return MEMBER; // Default for null input
        for (FactionRank r : FactionRank.values()) {
            if (r.name().equalsIgnoreCase(text)) {
                return r;
            }
        }
        return MEMBER; // Default to member if not found or invalid
    }
}
