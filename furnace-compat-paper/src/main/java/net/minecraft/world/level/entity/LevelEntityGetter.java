package net.minecraft.world.level.entity;

import java.util.UUID;

/**
 * 레벨의 엔티티 찾기 shim(Mojang 이름, T-212): {@code ServerLevel#getEntities()}. id·UUID 로 찾기만 있다 — 스레드 규칙은
 * {@code ServerLevel#getEntity(int)}·{@code getEntity(UUID)} 와 같다. 월드 전체를 도는 {@code getAll}·상자 찾기는 한 스레드가 모든 region 을 볼 수
 * 없어 두지 않는다(Bukkit {@code World#getEntities} 를 쓴다).
 */
public interface LevelEntityGetter<T extends EntityAccess> {

    /** 그 네트워크 id 의 엔티티, 없으면 null. */
    T get(int id);

    /** 그 UUID 의 엔티티(이 월드), 없으면 null. */
    T get(UUID uuid);
}
