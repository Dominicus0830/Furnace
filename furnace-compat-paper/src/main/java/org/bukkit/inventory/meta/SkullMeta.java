package org.bukkit.inventory.meta;

import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.profile.PlayerProfile;

/**
 * 머리 아이템(플레이어 머리·몹 머리)의 소유자 프로필과 음표 소리(Paper 1.21.1 SkullMeta). 블록 쪽은 {@link org.bukkit.block.Skull}.
 * 이름만 있는 프로필에 서버가 스킨을 자동으로 채우지는 않는다 — 스킨이 필요하면 {@code PlayerProfile#update} 로 채운 프로필을 넣는다(결정 항목).
 */
public interface SkullMeta extends ItemMeta {

    /** 소유자 이름(프로필에 이름이 없으면 null). */
    @Deprecated
    String getOwner();

    boolean hasOwner();

    /**
     * 소유자를 이름으로 정한다. 그 이름의 플레이어가 접속 중이면 그 프로필(스킨 포함), 아니면 UUID 없이 이름만(Paper). 16글자를 넘으면 false.
     * null 이면 소유자를 지운다.
     */
    @Deprecated
    boolean setOwner(String owner);

    /** 소유자 프로필(Paper 형, 스킨 속성 포함). null 이면 지운다. 이름·UUID·텍스처가 없어도 받는다. */
    void setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile profile);

    /** 소유자 프로필(Paper 형). 소유자가 없으면 null. */
    com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile();

    /** 소유자 플레이어(UUID 가 있으면 그것, 아니면 이름으로). 소유자가 없으면 null. */
    OfflinePlayer getOwningPlayer();

    /** 소유자를 정한다(접속 중인 플레이어면 스킨 속성까지). null 이면 지운다. 늘 true. */
    boolean setOwningPlayer(OfflinePlayer owner);

    /** 소유자 프로필(Bukkit 형). 소유자가 없으면 null. */
    PlayerProfile getOwnerProfile();

    /** 프로필을 정한다. null 이면 지운다. 이름이 없고 UUID·텍스처도 없으면 IllegalArgumentException. */
    void setOwnerProfile(PlayerProfile profile);

    void setNoteBlockSound(NamespacedKey noteBlockSound);

    NamespacedKey getNoteBlockSound();

    @Override
    SkullMeta clone();
}
