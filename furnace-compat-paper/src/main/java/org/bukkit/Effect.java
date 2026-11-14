// Paper 1.21.1 org.bukkit.Effect: 레벨 이벤트(level_event 패킷) 종류. 상수·번호·데이터 종류·@Deprecated 표시는 Paper 1.21.1 과 같다
// (getById 는 Paper 처럼 @Deprecated 상수를 빼고 찾는다 — 번호가 겹치는 옛 문·다락문 소리가 새 상수를 가리지 않게).
// 보내기는 World#playEffect·Player#playEffect, 데이터 → 정수 바꾸기는 org.bukkit.craftbukkit.CraftEffect.
package org.bukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.block.BlockFace;

public enum Effect {
    CLICK2(1000, Type.SOUND),
    CLICK1(1001, Type.SOUND),
    BOW_FIRE(1002, Type.SOUND),
    @Deprecated
    DOOR_TOGGLE(1006, Type.SOUND),
    @Deprecated
    IRON_DOOR_TOGGLE(1005, Type.SOUND),
    @Deprecated
    TRAPDOOR_TOGGLE(1007, Type.SOUND),
    @Deprecated
    IRON_TRAPDOOR_TOGGLE(1037, Type.SOUND),
    @Deprecated
    FENCE_GATE_TOGGLE(1008, Type.SOUND),
    @Deprecated
    DOOR_CLOSE(1012, Type.SOUND),
    @Deprecated
    IRON_DOOR_CLOSE(1011, Type.SOUND),
    @Deprecated
    TRAPDOOR_CLOSE(1013, Type.SOUND),
    @Deprecated
    IRON_TRAPDOOR_CLOSE(1036, Type.SOUND),
    @Deprecated
    FENCE_GATE_CLOSE(1014, Type.SOUND),
    EXTINGUISH(1009, Type.SOUND),
    RECORD_PLAY(1010, Type.SOUND, Material.class),
    GHAST_SHRIEK(1015, Type.SOUND),
    GHAST_SHOOT(1016, Type.SOUND),
    BLAZE_SHOOT(1018, Type.SOUND),
    ZOMBIE_CHEW_WOODEN_DOOR(1019, Type.SOUND),
    ZOMBIE_CHEW_IRON_DOOR(1020, Type.SOUND),
    ZOMBIE_DESTROY_DOOR(1021, Type.SOUND),
    SMOKE(2000, Type.VISUAL, BlockFace.class),
    STEP_SOUND(2001, Type.SOUND, org.bukkit.block.data.BlockData.class, Material.class),
    POTION_BREAK(2002, Type.VISUAL, Color.class),
    INSTANT_POTION_BREAK(2007, Type.VISUAL, Color.class),
    ENDER_SIGNAL(2003, Type.VISUAL),
    MOBSPAWNER_FLAMES(2004, Type.VISUAL),
    BREWING_STAND_BREW(1035, Type.SOUND),
    CHORUS_FLOWER_GROW(1033, Type.SOUND),
    CHORUS_FLOWER_DEATH(1034, Type.SOUND),
    PORTAL_TRAVEL(1032, Type.SOUND),
    @Deprecated(forRemoval = true, since = "1.21")
    ENDEREYE_LAUNCH(1003, Type.SOUND),
    FIREWORK_SHOOT(1004, Type.SOUND),
    @Deprecated(forRemoval = true, since = "1.20.5")
    VILLAGER_PLANT_GROW(2005, Type.VISUAL, Integer.class),
    DRAGON_BREATH(2006, Type.VISUAL),
    ANVIL_BREAK(1029, Type.SOUND),
    ANVIL_USE(1030, Type.SOUND),
    ANVIL_LAND(1031, Type.SOUND),
    ENDERDRAGON_SHOOT(1017, Type.SOUND),
    WITHER_BREAK_BLOCK(1022, Type.SOUND),
    WITHER_SHOOT(1024, Type.SOUND),
    ZOMBIE_INFECT(1026, Type.SOUND),
    ZOMBIE_CONVERTED_VILLAGER(1027, Type.SOUND),
    BAT_TAKEOFF(1025, Type.SOUND),
    END_GATEWAY_SPAWN(3000, Type.VISUAL),
    ENDERDRAGON_GROWL(3001, Type.SOUND),
    PHANTOM_BITE(1039, Type.SOUND),
    ZOMBIE_CONVERTED_TO_DROWNED(1040, Type.SOUND),
    HUSK_CONVERTED_TO_ZOMBIE(1041, Type.SOUND),
    GRINDSTONE_USE(1042, Type.SOUND),
    BOOK_PAGE_TURN(1043, Type.SOUND),
    SMITHING_TABLE_USE(1044, Type.SOUND),
    POINTED_DRIPSTONE_LAND(1045, Type.SOUND),
    POINTED_DRIPSTONE_DRIP_LAVA_INTO_CAULDRON(1046, Type.SOUND),
    POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON(1047, Type.SOUND),
    SKELETON_CONVERTED_TO_STRAY(1048, Type.SOUND),
    COMPOSTER_FILL_ATTEMPT(1500, Type.VISUAL, Boolean.class),
    LAVA_INTERACT(1501, Type.VISUAL),
    REDSTONE_TORCH_BURNOUT(1502, Type.VISUAL),
    END_PORTAL_FRAME_FILL(1503, Type.VISUAL),
    DRIPPING_DRIPSTONE(1504, Type.VISUAL),
    BONE_MEAL_USE(1505, Type.VISUAL, Integer.class),
    ENDER_DRAGON_DESTROY_BLOCK(2008, Type.VISUAL),
    SPONGE_DRY(2009, Type.VISUAL),
    ELECTRIC_SPARK(3002, Type.VISUAL, Axis.class),
    COPPER_WAX_ON(3003, Type.VISUAL),
    COPPER_WAX_OFF(3004, Type.VISUAL),
    OXIDISED_COPPER_SCRAPE(3005, Type.VISUAL),
    WITHER_SPAWNED(1023, Type.SOUND),
    ENDER_DRAGON_DEATH(1028, Type.SOUND),
    END_PORTAL_CREATED_IN_OVERWORLD(1038, Type.SOUND),
    SOUND_STOP_JUKEBOX_SONG(1011, Type.SOUND),
    CRAFTER_CRAFT(1049, Type.SOUND),
    CRAFTER_FAIL(1050, Type.SOUND),
    SHOOT_WHITE_SMOKE(2010, Type.VISUAL, BlockFace.class),
    BEE_GROWTH(2011, Type.VISUAL, Integer.class),
    TURTLE_EGG_PLACEMENT(2012, Type.VISUAL, Integer.class),
    SMASH_ATTACK(2013, Type.VISUAL, Integer.class),
    PARTICLES_SCULK_CHARGE(3006, Type.VISUAL, Integer.class),
    PARTICLES_SCULK_SHRIEK(3007, Type.SOUND),
    PARTICLES_AND_SOUND_BRUSH_BLOCK_COMPLETE(3008, Type.VISUAL, org.bukkit.block.data.BlockData.class),
    PARTICLES_EGG_CRACK(3009, Type.VISUAL),
    @Deprecated(forRemoval = true, since = "1.20.5")
    GUST_DUST(3010, Type.VISUAL),
    TRIAL_SPAWNER_SPAWN(3011, Type.VISUAL, Boolean.class),
    TRIAL_SPAWNER_SPAWN_MOB_AT(3012, Type.VISUAL, Boolean.class),
    TRIAL_SPAWNER_DETECT_PLAYER(3013, Type.VISUAL, Integer.class),
    TRIAL_SPAWNER_EJECT_ITEM(3014, Type.VISUAL),
    VAULT_ACTIVATE(3015, Type.VISUAL, Boolean.class),
    VAULT_DEACTIVATE(3016, Type.VISUAL, Boolean.class),
    VAULT_EJECT_ITEM(3017, Type.VISUAL),
    SPAWN_COBWEB(3018, Type.VISUAL),
    TRIAL_SPAWNER_DETECT_PLAYER_OMINOUS(3019, Type.VISUAL, Integer.class),
    TRIAL_SPAWNER_BECOME_OMINOUS(3020, Type.VISUAL, Boolean.class),
    TRIAL_SPAWNER_SPAWN_ITEM(3021, Type.VISUAL, Boolean.class),
    SOUND_WITH_CHARGE_SHOT(1051, Type.SOUND);

    private final int id;
    private final Type type;
    private final List<Class<?>> data;   // Paper: 데이터 종류 여럿(첫째가 getData)
    private static final Map<Integer, Effect> BY_ID = new HashMap<>();

    Effect(int id, Type type) {
        this(id, type, (Class<?>[]) null);
    }

    Effect(int id, Type type, Class<?>... data) {
        this.id = id;
        this.type = type;
        this.data = data != null ? List.of(data) : null;
    }

    /** 레벨 이벤트 번호(바닐라 LevelEvent). Paper 에서도 내부용 표시가 붙은 공개 메서드다. */
    public int getId() { return id; }

    /** 소리인가 시각 효과인가(Paper 에서 사용 중단 표시). */
    @Deprecated
    public Type getType() { return type; }

    /** 이 효과가 받는 데이터 종류(없으면 null). 여럿이면 첫째. */
    public Class<?> getData() { return data == null ? null : data.get(0); }

    /** 이 값을 데이터로 줄 수 있는가(Paper isApplicable). */
    public boolean isApplicable(Object obj) {
        if (data == null || obj == null) return false;
        for (Class<?> c : data) if (c.isAssignableFrom(obj.getClass())) return true;
        return false;
    }

    /** 번호 → 효과. Paper 처럼 @Deprecated 상수는 빠진다. 없으면 null. */
    public static Effect getById(int id) { return BY_ID.get(id); }

    static {
        for (Effect effect : values()) {
            if (!isDeprecated(effect)) BY_ID.put(effect.id, effect);
        }
    }

    private static boolean isDeprecated(Effect effect) {
        try {
            return Effect.class.getDeclaredField(effect.name()).isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Deprecated
    public enum Type { SOUND, VISUAL }
}
