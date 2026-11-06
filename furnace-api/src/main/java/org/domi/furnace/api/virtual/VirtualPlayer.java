// 가짜 플레이어(NPC) — 서버에 접속하지 않았는데 월드에 서 있는 사람 모양 엔티티. 스킨과 이름은 프로필 정보로 보낸다.
// 이름표(프로필 이름)는 기본으로 숨긴다(setNameTagVisible 로 켠다). 탭 목록에는 기본으로 올리지 않는다.
package org.domi.furnace.api.virtual;

import java.util.List;
import org.domi.furnace.api.entity.ProfileProperty;

public interface VirtualPlayer extends VirtualEntity {

    /** 프로필 이름(1~16자). */
    String profileName();

    List<ProfileProperty> skin();

    /** 스킨을 바꾼다. 보던 플레이어에게는 다시 나타나는 식으로 반영된다(클라이언트가 스킨을 엔티티 생성 때만 읽는다). */
    void setSkin(List<ProfileProperty> skin);

    /** 탭 목록에 보이는지(기본 false). */
    boolean isListed();

    void setListed(boolean listed);

    /** 머리 위 프로필 이름 표시(기본 false). */
    boolean isNameTagVisible();

    void setNameTagVisible(boolean visible);

    /** 스킨 겉 레이어 비트(1 망토 2 재킷 4 왼소매 8 오른소매 16 왼바지 32 오른바지 64 모자). 기본 127(전부). */
    void setSkinLayers(int mask);

    /** 웅크림·수영 같은 자세 번호는 {@link #setMetadata} 의 칸 6 으로 정한다. */
}
