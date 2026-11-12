// 엔티티 메타데이터 칸 하나 — (주인 엔티티 부류, 칸 이름, 값 종류). 숫자 칸 번호 대신 이것을 쓴다.
// 이름은 바닐라(Mojang) 이름을 따른다: 부류는 클래스 이름("Display", "LivingEntity"), 칸은 필드 이름에서 DATA_·_ID 를 뺀 소문자("scale").
// 칸 번호는 마인크래프트 버전마다 밀리므로 서버가 실행 중인 버전에 맞춰 정한다(PacketService.entityDataIndex).
// 상수는 EntityDataKeys 에 있다. 같은 칸은 늘 같은 객체라 == 로 비교해도 된다.
package org.domi.furnace.api.entity.data;

import java.util.Objects;

public final class EntityDataKey<T> {

    private final String owner;
    private final String name;
    private final EntityDataType<T> type;

    EntityDataKey(String owner, String name, EntityDataType<T> type) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    /** 이 칸을 정의한 바닐라 엔티티 부류(예: "Display"). 그 부류와 하위 부류의 엔티티에만 있다. */
    public String owner() { return owner; }

    /** 칸 이름(예: "scale"). */
    public String name() { return name; }

    public EntityDataType<T> type() { return type; }

    /**
     * 실행 중인 서버 버전에서의 칸 번호(이 버전에 없는 칸이면 empty). 번호는 버전마다 다르므로 저장하거나 상수로 쓰지 않는다.
     * 서버가 준비되기 전이면 IllegalStateException.
     */
    public java.util.OptionalInt index() {
        java.util.function.Function<EntityDataKey<?>, java.util.OptionalInt> r = resolver;
        if (r == null) throw new IllegalStateException("서버가 아직 메타데이터 칸 표를 걸지 않았다");
        return r.apply(this);
    }

    private static volatile java.util.function.Function<EntityDataKey<?>, java.util.OptionalInt> resolver;

    /** 서버 구현 전용 — 실행 중인 버전의 칸 번호 표를 건다. */
    public static void installIndexResolver(java.util.function.Function<EntityDataKey<?>, java.util.OptionalInt> r) {
        resolver = java.util.Objects.requireNonNull(r, "resolver");
    }

    /** "Display.scale". */
    public String id() { return owner + "." + name; }

    @Override
    public String toString() { return id() + ":" + type.name(); }
}
