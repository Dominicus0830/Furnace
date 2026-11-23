package net.minecraft.world.level.entity;

import java.util.UUID;

/**
 * 레벨의 엔티티 찾기 shim(Mojang 이름, T-212): {@code ServerLevel#getEntities()}. id·UUID 로 찾기는 스레드 규칙이
 * {@code ServerLevel#getEntity(int)}·{@code getEntity(UUID)} 와 같다. {@link #getAll} 은 Bukkit {@code World#getEntities} 와 같은 스냅샷이다(5e-3c-2).
 * 상자 찾기는 아직 없다.
 */
public interface LevelEntityGetter<T extends EntityAccess> {

    /** 그 네트워크 id 의 엔티티, 없으면 null. */
    T get(int id);

    /** 그 UUID 의 엔티티(이 월드), 없으면 null. */
    T get(UUID uuid);

    /**
     * 바닐라 {@code getAll()}: 이 월드의 엔티티 전부(플레이어 포함). Furnace 는 한 스레드가 모든 region 을 돌 수 없어 부른 때의 스냅샷 목록이다 —
     * Bukkit {@code World#getEntities} 와 같은 목록이라 어느 스레드에서나 부를 수 있고, 꺼낸 엔티티의 상태를 읽을 때의 스레드 규칙은 그 엔티티의 것이다.
     */
    Iterable<T> getAll();
}
