// 원인 사슬의 고리 하나 — 무엇이(플레이어·엔티티·블록) 어떻게(action) 다음 고리(또는 이벤트 대상)를 일으켰나.
// 만든 순간의 값을 복사한 불변 스냅샷이라 어느 스레드에서 읽어도, 오래 들고 있어도(로그·DB) 안전하다. 서버 객체를 붙잡지 않는다.
package org.domi.furnace.api.event.cause;

import java.util.Objects;
import java.util.UUID;
import org.domi.furnace.api.world.BlockPos;

/**
 * @param kind   고리 종류
 * @param type   레지스트리 키 — 엔티티면 minecraft:tnt, 블록이면 minecraft:dispenser, 플레이어면 minecraft:player
 * @param uuid   엔티티·플레이어 UUID(블록이면 null)
 * @param name   플레이어 이름(그 밖에는 null)
 * @param world  차원 키(모르면 null)
 * @param pos    블록 칸, 또는 엔티티·플레이어가 그때 있던 칸(모르면 null)
 * @param action 이 고리가 한 일 — {@link #EXPLODE} 같은 소문자 키. 플러그인이 새 키를 써도 된다(이름공간 붙이기 권장)
 */
public record CauseEntry(Kind kind, String type, UUID uuid, String name, String world, BlockPos pos, String action) {

    public enum Kind {
        PLAYER,
        /** 플레이어가 아닌 엔티티(TNT·화살·몹 …). */
        ENTITY,
        BLOCK,
        /** 플러그인이 직접 단 원인(type 은 플러그인 이름). */
        PLUGIN
    }

    // ------------------------------------------------------------------ action 키(Furnace 가 쓰는 것)

    /** 터졌다(TNT·크리퍼·침대 …). */
    public static final String EXPLODE = "explode";
    /** 부싯돌·화염구로 불을 붙였다. */
    public static final String IGNITE = "ignite";
    /** 부쉈다(불안정한 TNT 를 부숴 점화한 플레이어, BlockBreakEvent 의 플레이어). */
    public static final String BREAK = "break";
    /** 놓았다(BlockPlaceEvent 의 플레이어). */
    public static final String PLACE = "place";
    /** 레드스톤 신호를 받았다(이 칸 자신 — 신호를 낸 칸은 추적하지 않는다). */
    public static final String REDSTONE = "redstone";
    /** 불이 옮겨 붙었다(불 칸). */
    public static final String FIRE = "fire";
    /** 용암이 불을 붙였다. */
    public static final String LAVA = "lava";
    /** 발사기가 내보냈다·불을 붙였다. */
    public static final String DISPENSE = "dispense";
    /** 투사체가 맞혔다. */
    public static final String HIT = "hit";
    /** 투사체를 쐈다·던졌다(투사체의 주인). */
    public static final String SHOOT = "shoot";
    /** 피해를 줬다(피해 이벤트 사슬의 맨 앞). */
    public static final String DAMAGE = "damage";
    /** 근접 공격했다. */
    public static final String ATTACK = "attack";
    /** 블록을 썼다(침대에 누우려다 터뜨린 플레이어 등). */
    public static final String USE = "use";

    public CauseEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(action, "action");
    }

    public static CauseEntry player(UUID uuid, String name, String world, BlockPos pos, String action) {
        return new CauseEntry(Kind.PLAYER, "minecraft:player", Objects.requireNonNull(uuid, "uuid"), name, world, pos, action);
    }

    public static CauseEntry entity(String type, UUID uuid, String world, BlockPos pos, String action) {
        return new CauseEntry(Kind.ENTITY, type, uuid, null, world, pos, action);
    }

    public static CauseEntry block(String type, String world, BlockPos pos, String action) {
        return new CauseEntry(Kind.BLOCK, type, null, null, world, Objects.requireNonNull(pos, "pos"), action);
    }

    /** 플러그인이 다는 원인(예: 자체 TNT 대포). */
    public static CauseEntry plugin(String pluginName, String action) {
        return new CauseEntry(Kind.PLUGIN, pluginName, null, null, null, null, action);
    }

    public boolean isPlayer() { return kind == Kind.PLAYER; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(type).append(' ').append(action);
        if (name != null) sb.append(" name=").append(name);
        if (pos != null) sb.append(" @").append(pos.x()).append(',').append(pos.y()).append(',').append(pos.z());
        return sb.toString();
    }
}
