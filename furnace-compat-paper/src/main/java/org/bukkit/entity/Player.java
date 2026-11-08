package org.bukkit.entity;

import java.net.InetSocketAddress;
import org.bukkit.OfflinePlayer;

/** 접속 중인 플레이어. 인벤토리·스코어보드·소리 같은 것은 H3. */
public interface Player extends HumanEntity, OfflinePlayer, org.bukkit.plugin.messaging.PluginMessageRecipient {

    /** 채팅 서식 %1$s 에 쓰는 이름 — 정하지 않았으면 플레이어 이름 그대로(Paper·Spigot 과 같다, 바닐라 팀 서식은 붙지 않는다). */
    String getDisplayName();

    /** 표시 이름을 정한다(§ 서식 가능). null 이면 이름 그대로로 되돌린다. 접속을 끊으면 사라진다. */
    void setDisplayName(String name);

    /** 접속 상태 안에서만 쓸 수 있다. */
    InetSocketAddress getAddress();

    /** 이유와 함께 내보낸다(Bukkit 의 kickPlayer). */
    void kickPlayer(String message);

    int getLevel();

    void setLevel(int level);

    void giveExp(int amount);

    int getFoodLevel();

    void setFoodLevel(int value);

    /** 이 플레이어가 명령을 친 것처럼 실행한다. Furnace 는 결과를 알려 주지 않아 실행을 맡기면 true. */
    boolean performCommand(String command);

    int getPing();

    String getLocale();

    /**
     * 클라이언트가 인벤토리를 서버 값에 맞추게 한다. Furnace 는 서버 쪽 변경을 다음 틱 창 갱신에서 자동으로 보내므로 따로 할 일이 없다
     * (그래서 아무 일도 하지 않는다).
     */
    void updateInventory();
}
