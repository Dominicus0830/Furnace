package org.bukkit.boss;

import java.util.List;
import org.bukkit.entity.Player;

/**
 * 플러그인 보스바(Bukkit.createBossBar). 바꾸면 보는 플레이어 모두에게 곧바로 보인다. 어느 스레드에서 불러도 된다.
 * 저장되지 않는다(서버를 다시 켜면 사라진다 — Paper 와 같다).
 */
public interface BossBar {

    String getTitle();

    /** § 서식 가능. null 이면 빈 제목. */
    void setTitle(String title);

    BarColor getColor();

    void setColor(BarColor color);

    BarStyle getStyle();

    void setStyle(BarStyle style);

    void removeFlag(BarFlag flag);

    void addFlag(BarFlag flag);

    boolean hasFlag(BarFlag flag);

    /** 0.0..1.0 밖이면 IllegalArgumentException. */
    void setProgress(double progress);

    double getProgress();

    /** 접속 상태가 아니면 IllegalArgumentException. */
    void addPlayer(Player player);

    void removePlayer(Player player);

    void removeAll();

    List<Player> getPlayers();

    /** 숨겨도 보는 플레이어 목록은 그대로다. */
    void setVisible(boolean visible);

    boolean isVisible();

    @Deprecated(since = "1.9")
    void show();

    @Deprecated(since = "1.9")
    void hide();
}
