package org.bukkit.entity;

import java.net.InetSocketAddress;
import org.bukkit.OfflinePlayer;

/** 접속 중인 플레이어. 인벤토리·스코어보드·소리 같은 것은 H3. */
public interface Player extends HumanEntity, OfflinePlayer {

    String getDisplayName();

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
}
