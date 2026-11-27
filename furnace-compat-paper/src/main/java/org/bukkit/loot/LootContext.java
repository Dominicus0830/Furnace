package org.bukkit.loot;

import com.google.common.base.Preconditions;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;

/** Paper 1.21.1 LootContext(전리품 문맥: 위치·행운·약탈 수정치·죽은 엔티티·죽인 사람). */
public final class LootContext {

    public static final int DEFAULT_LOOT_MODIFIER = -1;

    private final Location location;
    private final float luck;
    private final int lootingModifier;
    private final Entity lootedEntity;
    private final HumanEntity killer;

    private LootContext(Location location, float luck, int lootingModifier, Entity lootedEntity, HumanEntity killer) {
        Preconditions.checkArgument(location != null, "LootContext location cannot be null");
        Preconditions.checkArgument(location.getWorld() != null, "LootContext World cannot be null");
        this.location = location;
        this.luck = luck;
        this.lootingModifier = lootingModifier;
        this.lootedEntity = lootedEntity;
        this.killer = killer;
    }

    public Location getLocation() { return location; }

    public float getLuck() { return luck; }

    public int getLootingModifier() { return lootingModifier; }

    public Entity getLootedEntity() { return lootedEntity; }

    public HumanEntity getKiller() { return killer; }

    public static class Builder {

        private final Location location;
        private float luck;
        private int lootingModifier = DEFAULT_LOOT_MODIFIER;
        private Entity lootedEntity;
        private HumanEntity killer;

        public Builder(Location location) { this.location = location; }

        public Builder luck(float luck) {
            this.luck = luck;
            return this;
        }

        public Builder lootingModifier(int modifier) {
            this.lootingModifier = modifier;
            return this;
        }

        public Builder lootedEntity(Entity lootedEntity) {
            this.lootedEntity = lootedEntity;
            return this;
        }

        public Builder killer(HumanEntity killer) {
            this.killer = killer;
            return this;
        }

        public LootContext build() { return new LootContext(location, luck, lootingModifier, lootedEntity, killer); }
    }
}
