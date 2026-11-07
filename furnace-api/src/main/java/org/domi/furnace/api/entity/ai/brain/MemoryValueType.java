// 기억 값이 API 에서 어떤 모양인가(버전 중립). 서버는 바닐라 기억마다 맞는 모양을 알고, 다른 모양으로 읽고 쓰면 IllegalArgumentException.
// 엔티티를 가리키는 기억은 UUID 로 읽고 쓴다(쓸 때 같은 region 에서 그 엔티티를 찾는다). 플러그인 기억은 object(클래스)로 아무 값이나.
package org.domi.furnace.api.entity.ai.brain;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.world.BlockPos;

public final class MemoryValueType<T> {

    private final String name;
    private final Class<?> javaType;
    private final boolean plugin;

    private MemoryValueType(String name, Class<?> javaType, boolean plugin) {
        this.name = name;
        this.javaType = javaType;
        this.plugin = plugin;
    }

    /** 표시(값 없는 표지) 기억: 있으면 Boolean.TRUE 로 읽는다. true 를 쓰면 켜고, false 를 쓰면 지운다. */
    public static final MemoryValueType<Boolean> FLAG = new MemoryValueType<>("flag", Boolean.class, false);
    public static final MemoryValueType<Boolean> BOOLEAN = new MemoryValueType<>("boolean", Boolean.class, false);
    public static final MemoryValueType<Integer> INTEGER = new MemoryValueType<>("integer", Integer.class, false);
    public static final MemoryValueType<Long> LONG = new MemoryValueType<>("long", Long.class, false);
    /** UUID 그 자체(예: angry_at 은 엔티티가 내려가 있어도 남는 UUID). */
    public static final MemoryValueType<UUID> UUID_VALUE = new MemoryValueType<>("uuid", UUID.class, false);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final MemoryValueType<List<UUID>> UUID_LIST = new MemoryValueType("uuid_list", List.class, false);
    /** 살아 있는 엔티티 하나(UUID 로). 쓸 때 같은 region 에 없거나 종류가 맞지 않으면 쓰기가 false. */
    public static final MemoryValueType<UUID> ENTITY = new MemoryValueType<>("entity", UUID.class, false);
    /** 살아 있는 엔티티 목록(UUID 로, 가까운 순서). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final MemoryValueType<List<UUID>> ENTITY_LIST = new MemoryValueType("entity_list", List.class, false);
    public static final MemoryValueType<BlockPos> BLOCK_POS = new MemoryValueType<>("block_pos", BlockPos.class, false);
    public static final MemoryValueType<GlobalBlockPos> GLOBAL_POS = new MemoryValueType<>("global_pos", GlobalBlockPos.class, false);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final MemoryValueType<List<GlobalBlockPos>> GLOBAL_POS_LIST = new MemoryValueType("global_pos_list", List.class, false);
    public static final MemoryValueType<WalkTarget> WALK_TARGET = new MemoryValueType<>("walk_target", WalkTarget.class, false);
    public static final MemoryValueType<BrainTarget> TARGET = new MemoryValueType<>("target", BrainTarget.class, false);

    /** 플러그인 기억 값(저장되지 않는다). 바닐라 기억에는 쓸 수 없다. */
    public static <T> MemoryValueType<T> object(Class<T> type) {
        if (type == null) throw new NullPointerException("type");
        return new MemoryValueType<>("object:" + type.getName(), type, true);
    }

    public String name() { return name; }

    /** 값의 자바 종류(목록이면 List). */
    public Class<?> javaType() { return javaType; }

    /** object(…) 로 만든 플러그인 값 모양인가. */
    public boolean isPluginType() { return plugin; }

    @Override
    public boolean equals(Object o) { return o instanceof MemoryValueType<?> t && t.name.equals(name) && t.javaType == javaType; }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() { return name; }
}
