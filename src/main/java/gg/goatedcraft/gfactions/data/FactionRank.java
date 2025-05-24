package gg.goatedcraft.gfactions.data;

public enum FactionRank {
    ASSOCIATE("Associate"), // New Rank
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
            if (r.name().equalsIgnoreCase(text) || r.getDisplayName().equalsIgnoreCase(text)) { // Added check for display name
                return r;
            }
        }
        return MEMBER; // Default to member if not found or invalid
    }

    /**
     * Checks if this rank is at least the level of an Admin (Admin or Owner).
     * @return true if Admin or Owner, false otherwise.
     */
    public boolean isAdminOrHigher() {
        return this == ADMIN || this == OWNER;
    }

    /**
     * Checks if this rank is at least the level of a Member (Member, Admin, or Owner).
     * @return true if Member, Admin, or Owner, false otherwise.
     */
    public boolean isMemberOrHigher() {
        return this == MEMBER || this == ADMIN || this == OWNER;
    }

    /**
     * Checks if this rank is at least the level of an Associate.
     * (Associate, Member, Admin, or Owner).
     * @return true if Associate or higher, false otherwise.
     */
    public boolean isAssociateOrHigher() {
        return this == ASSOCIATE || this == MEMBER || this == ADMIN || this == OWNER;
    }
}
