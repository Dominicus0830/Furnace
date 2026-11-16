package org.bukkit.event.entity;

import org.bukkit.entity.LivingEntity;

/**
 * 살아 있는 것이 월드에 나타나려 한다. Bukkit 처럼 처리기 목록은 {@link EntitySpawnEvent} 것을 같이 쓴다
 * (EntitySpawnEvent 리스너도 받고, CreatureSpawnEvent 리스너는 이 종류만 받는다).
 */
public class CreatureSpawnEvent extends EntitySpawnEvent {

    private final SpawnReason spawnReason;

    public CreatureSpawnEvent(LivingEntity spawnee, SpawnReason spawnReason) {
        super(spawnee);
        this.spawnReason = spawnReason;
    }

    @Override
    public LivingEntity getEntity() { return (LivingEntity) entity; }

    public SpawnReason getSpawnReason() { return spawnReason; }

    /** 스폰 이유(Bukkit 1.21.1 의 이름 그대로). Furnace 가 알리는 이유는 이 가운데 같은 이름이고 모르는 경로는 DEFAULT 다. */
    public enum SpawnReason {
        NATURAL, JOCKEY, CHUNK_GEN, SPAWNER, EGG, SPAWNER_EGG, LIGHTNING, BUILD_SNOWMAN, BUILD_IRONGOLEM, BUILD_WITHER, VILLAGE_DEFENSE,
        VILLAGE_INVASION, BREEDING, SLIME_SPLIT, REINFORCEMENTS, NETHER_PORTAL, DISPENSE_EGG, INFECTION, CURED, OCELOT_BABY, SILVERFISH_BLOCK,
        MOUNT, TRAP, ENDER_PEARL, SHOULDER_ENTITY, DROWNED, SHEARED, EXPLOSION, RAID, PATROL, BEEHIVE, PIGLIN_ZOMBIFIED, SPELL, FROZEN,
        METAMORPHOSIS, DUPLICATION, COMMAND, ENCHANTMENT, TRIAL_SPAWNER, CUSTOM, DEFAULT
    }
}
