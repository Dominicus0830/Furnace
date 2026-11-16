package org.bukkit.block;

import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.SkullType;

/**
 * 머리 블록(해골·좀비·플레이어 머리 등)의 상태. 소유자는 플레이어 머리일 때만 쓰이고 저장된다(다른 머리에 정해도 {@link #update}에 실리지 않는다).
 * 소유자의 이름·UUID·스킨은 {@link com.destroystokyo.paper.profile.PlayerProfile} 로 직접 정할 수 있어 Mojang 조회 없이 스킨을 줄 수 있다.
 */
public interface Skull extends TileState {

    /** 소유자가 있는가(플레이어 머리에 프로필이 있으면 참). */
    @Deprecated
    boolean hasOwner();

    /** 소유자 이름(프로필에 이름이 없으면 null). */
    @Deprecated
    String getOwner();

    /**
     * 서버가 아는 이름(사용자 캐시, 오프라인 모드면 오프라인 UUID)으로 소유자를 정한다. 이름이 null 이거나 16글자를 넘거나 서버가 모르는 이름이면 false.
     * 인증 모드에서 캐시에 없는 이름은 묻지 않고 false 다(Mojang 에 묻는 {@code Bukkit.createProfile(name).update()} 를 쓴다).
     */
    @Deprecated
    boolean setOwner(String name);

    /** 소유자 플레이어(UUID 가 있으면 그것, 아니면 이름으로). 소유자가 없으면 null. */
    OfflinePlayer getOwningPlayer();

    /** 소유자를 정한다(접속 중인 플레이어면 접속 때 받은 스킨 속성까지). */
    void setOwningPlayer(OfflinePlayer player);

    /** 소유자 프로필(스킨 속성 포함)을 정한다. UUID 도 이름도 없는 프로필도 받는다. */
    void setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile profile);

    /** 소유자 프로필(Paper 형). 소유자가 없으면 null. */
    com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile();

    /** 소유자 프로필(Bukkit 형). 소유자가 없으면 null. */
    @Deprecated
    org.bukkit.profile.PlayerProfile getOwnerProfile();

    /** 프로필을 정한다. null 이면 지운다. 이름도 UUID 도 텍스처도 없으면 IllegalArgumentException. */
    @Deprecated
    void setOwnerProfile(org.bukkit.profile.PlayerProfile profile);

    /** 소리 블록 위에 놓았을 때 나는 소리(없으면 null). */
    NamespacedKey getNoteBlockSound();

    /** 소리를 정한다. 주의: null 로 지워도 {@link #update} 때 블록 엔티티의 옛 소리는 남는다(Paper 도 같다). */
    void setNoteBlockSound(NamespacedKey noteBlockSound);

    /** 바닥 머리는 16방향, 벽 머리는 바라보는 방향. */
    @Deprecated
    BlockFace getRotation();

    @Deprecated
    void setRotation(BlockFace rotation);

    @Deprecated
    SkullType getSkullType();

    /** 지원하지 않는다 — 블록 종류를 바꿔야 한다(UnsupportedOperationException). */
    @Deprecated
    void setSkullType(SkullType skullType);
}
