package org.bukkit;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.permissions.ServerOperator;

/**
 * 접속 중이 아닐 수도 있는 플레이어. ConfigurationSerializable 이라 설정에 넣을 수 있다 — 별칭 {@code Player}, 키 {@code UUID}
 * (접속 중인 {@link Player} 는 {@code name} 키로 저장되고 같은 별칭으로 되읽힌다. Paper 와 같다, A15-01).
 */
public interface OfflinePlayer extends ServerOperator, org.bukkit.configuration.serialization.ConfigurationSerializable, io.papermc.paper.persistence.PersistentDataViewHolder {

    boolean isOnline();

    String getName();

    UUID getUniqueId();

    /** 접속 중이면 Player, 아니면 null. */
    Player getPlayer();

    /**
     * 이 플레이어의 영속 데이터 읽기 전용 보기(Paper 와 같다). 접속 중이면 지금 값, 아니면 읽을 때마다 디스크의 저장 데이터(없으면 빈 데이터)를
     * 읽는다 — **읽기 호출이 블로킹 IO** 다(Paper 도 같다). 서버 스레드에서 부르면 틱이 그만큼 멈춘다.
     */
    @Override
    io.papermc.paper.persistence.PersistentDataContainerView getPersistentDataContainer();
}
