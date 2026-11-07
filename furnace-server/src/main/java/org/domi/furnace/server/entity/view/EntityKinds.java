// 변장할 때 메타데이터 칸을 얼마나 넘겨도 되는지 가르는 엔티티 갈래(1.21.1 클라이언트 클래스 계층 기준).
// 바닐라 SynchedEntityData 칸 번호는 부모 클래스부터 차례로 붙는다: Entity 0~7, LivingEntity 8~14, Mob 15, 그 아래는 종류마다 다르다.
// 갈래가 다르면 뒤쪽 칸의 뜻·값 종류가 달라 클라이언트가 끊으므로(값 종류가 다르면 IllegalStateException), 공통 앞부분만 넘긴다.
package org.domi.furnace.server.entity.view;

import java.util.Set;
import org.domi.furnace.server.entity.EntityType;

public final class EntityKinds {

    private EntityKinds() {}

    /** 살아 있지 않은 것(아이템·투사체·탈것·표시 엔티티 …): Entity 칸 0~7 만 공통. */
    public static final int NON_LIVING = 0;
    /** LivingEntity 이지만 Mob 이 아닌 것(갑옷 거치대). 칸 0~14. */
    public static final int LIVING = 1;
    /** Mob(나머지 살아 있는 것 전부). 칸 0~15. */
    public static final int MOB = 2;
    /** 플레이어(LivingEntity 아래 Player 칸 15~). */
    public static final int PLAYER = 3;

    public static final int PLAYER_TYPE_ID = EntityType.registryId("minecraft:player");

    /** 1.21.1 에서 LivingEntity 가 아닌 엔티티 종류. */
    static final Set<String> NON_LIVING_TYPES = Set.of(
            "minecraft:area_effect_cloud", "minecraft:arrow", "minecraft:block_display", "minecraft:boat", "minecraft:breeze_wind_charge",
            "minecraft:chest_boat", "minecraft:chest_minecart", "minecraft:command_block_minecart", "minecraft:dragon_fireball", "minecraft:egg",
            "minecraft:end_crystal", "minecraft:ender_pearl", "minecraft:evoker_fangs", "minecraft:experience_bottle", "minecraft:experience_orb",
            "minecraft:eye_of_ender", "minecraft:falling_block", "minecraft:firework_rocket", "minecraft:furnace_minecart", "minecraft:glow_item_frame",
            "minecraft:hopper_minecart", "minecraft:interaction", "minecraft:item", "minecraft:item_display", "minecraft:item_frame",
            "minecraft:ominous_item_spawner", "minecraft:fireball", "minecraft:leash_knot", "minecraft:lightning_bolt", "minecraft:llama_spit",
            "minecraft:marker", "minecraft:minecart", "minecraft:painting", "minecraft:potion", "minecraft:shulker_bullet", "minecraft:small_fireball",
            "minecraft:snowball", "minecraft:spawner_minecart", "minecraft:spectral_arrow", "minecraft:text_display", "minecraft:tnt",
            "minecraft:tnt_minecart", "minecraft:trident", "minecraft:wind_charge", "minecraft:wither_skull", "minecraft:fishing_bobber");

    public static int kindOf(String typeName) {
        if ("minecraft:player".equals(typeName)) return PLAYER;
        if ("minecraft:armor_stand".equals(typeName)) return LIVING;
        return NON_LIVING_TYPES.contains(typeName) ? NON_LIVING : MOB;
    }

    public static int kindOf(int typeId) {
        String n = EntityType.registryName(typeId);
        return n == null ? NON_LIVING : kindOf(n);
    }

    public static boolean isLiving(int kind) { return kind != NON_LIVING; }

    /** 진짜 종류의 칸 중 변장 종류에게 그대로 넘겨도 되는 앞쪽 칸 수(같은 종류면 전부). */
    public static int sharedPrefix(int realTypeId, int realKind, int shownTypeId, int shownKind) {
        if (realTypeId == shownTypeId) return Integer.MAX_VALUE;
        if (realKind == NON_LIVING || shownKind == NON_LIVING) return 8;
        if (realKind == MOB && shownKind == MOB) return 16;
        return 15;
    }
}
