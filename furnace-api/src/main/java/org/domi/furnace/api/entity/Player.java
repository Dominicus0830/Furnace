// 접속한 플레이어. 읽기는 어느 스레드에서나 안전하고(최근 값), 상태를 바꾸는 호출도 스레드 안전하다 — 서버가 소유 스레드로 넘긴다.
package org.domi.furnace.api.entity;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;

public interface Player extends CommandSender {

    UUID uniqueId();

    /** 바닐라 Player.getDisplayName: 이름 + "/tell 이름" 제안 클릭 + 삽입. */
    Text displayName();

    boolean isOnline();

    /** 마지막으로 알려진 위치. */
    Location location();

    GameMode gameMode();

    /** 바닐라 setGameMode: 같으면 false. PlayerGameModeChangeEvent 를 거친다. */
    boolean setGameMode(GameMode mode);

    float health();

    /** 0..최대 체력으로 잘린다. 0 이면 죽는다. */
    void setHealth(float health);

    int foodLevel();

    void setFoodLevel(int food);

    int level();

    void setLevel(int level);

    /** 경험치 점수를 더한다(바닐라 giveExperiencePoints). */
    void giveExperience(int points);

    boolean isDead();

    /** 클라이언트 언어(client_information). */
    Locale locale();

    /** 핑(ms, 탭 목록 값). */
    int ping();

    InetSocketAddress address();

    /** 사유를 보여 주고 연결을 끊는다. */
    void kick(Text reason);

    /** 이 플레이어로서 명령 실행(앞의 '/' 는 없어도 된다). */
    boolean performCommand(String command);
}
