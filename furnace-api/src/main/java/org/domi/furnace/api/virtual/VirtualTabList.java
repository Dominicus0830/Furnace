// 한 플레이어의 탭 목록에 가짜 줄을 더하는 손잡이. 서버 월드나 실제 접속자와 무관하고, 그 플레이어 화면에만 보인다.
// 연결에 묶여 있다 — 그 플레이어가 나가면 줄도 사라지고, 다시 접속하면 새로 더해야 한다.
// 만든 플러그인이 꺼지면 그 플러그인이 더한 줄은 자동으로 지워진다.
package org.domi.furnace.api.virtual;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;

public interface VirtualTabList {

    /** 줄을 더한다. name 1~16자, skin 은 비어도 된다(기본 스킨), displayName null 이면 이름 그대로. */
    Entry add(FurnacePlugin owner, String name, List<ProfileProperty> skin, GameMode gameMode, int latencyMs, Text displayName);

    /** 이 손잡이로 더한 줄. */
    List<Entry> entries();

    interface Entry {
        UUID uuid();

        String name();

        void setDisplayName(Text displayName);

        void setLatency(int latencyMs);

        void setGameMode(GameMode mode);

        boolean isRemoved();

        void remove();
    }
}
