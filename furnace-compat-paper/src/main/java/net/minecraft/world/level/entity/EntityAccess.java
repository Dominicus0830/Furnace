package net.minecraft.world.level.entity;

/**
 * 엔티티 접근 shim(Mojang 이름, T-212). {@link LevelEntityGetter} 의 형식 인자 경계라 서술자에 이 이름이 나온다
 * ({@code get(UUID)} 의 반환 타입). 엔티티 shim 이 이미 가진 멤버만 둔다 — 레벨 콜백·지움 설정 같은 서버 내부 멤버는 없다.
 */
public interface EntityAccess {

    int getId();

    java.util.UUID getUUID();

    net.minecraft.core.BlockPos blockPosition();
}
