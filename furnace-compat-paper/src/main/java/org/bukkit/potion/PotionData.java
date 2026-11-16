package org.bukkit.potion;

import java.util.Objects;

/** @deprecated 1.20.5 전 물약 표현(종류 + 연장·강화 표시). 1.21.1 Bukkit 에도 남아 있다 — 지금은 LONG_·STRONG_ 종류를 쓴다. */
@Deprecated
public final class PotionData {

    private final PotionType type;
    private final boolean extended;
    private final boolean upgraded;

    public PotionData(PotionType type, boolean extended, boolean upgraded) {
        if (type == null) throw new IllegalArgumentException("Potion Type must not be null");
        if (upgraded && !type.isUpgradeable()) throw new IllegalArgumentException("Potion Type is not upgradable");
        if (extended && !type.isExtendable()) throw new IllegalArgumentException("Potion Type is not extendable");
        if (upgraded && extended) throw new IllegalArgumentException("Potion cannot be both extended and upgraded");
        this.type = type;
        this.extended = extended;
        this.upgraded = upgraded;
    }

    public PotionData(PotionType type) { this(type, false, false); }

    public PotionType getType() { return type; }

    public boolean isUpgraded() { return upgraded; }

    public boolean isExtended() { return extended; }

    @Override
    public int hashCode() {
        int h = 23;
        h = 23 * h + (type != null ? type.hashCode() : 0);
        h = 23 * h + (extended ? 1 : 0);
        return 23 * h + (upgraded ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PotionData d && d.upgraded == upgraded && d.extended == extended && Objects.equals(d.type, type);
    }
}
