// 접속한 플레이어. 읽기는 어느 스레드에서나 안전하고(최근 값), 상태를 바꾸는 호출도 스레드 안전하다 — 서버가 소유 스레드로 넘긴다.
package org.domi.furnace.api.entity;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;

public interface Player extends CommandSender, LivingEntity {

    @Override
    UUID uniqueId();

    /** 바닐라 Player.getDisplayName: 이름 + "/tell 이름" 제안 클릭 + 삽입. */
    Text displayName();

    boolean isOnline();

    /** 마지막으로 알려진 위치. */
    @Override
    Location location();

    GameMode gameMode();

    /** 바닐라 setGameMode: 같으면 false. PlayerGameModeChangeEvent 를 거친다. */
    boolean setGameMode(GameMode mode);

    @Override
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

    /** 클라이언트가 minecraft:brand 로 알린 이름(예: "vanilla"), 아직 모르면 null. */
    String clientBrand();

    /**
     * 플러그인 채널로 바이트를 보낸다(play 단계 custom_payload). 채널은 "네임스페이스:이름" 소문자 형식, 본문은 최대 1048576바이트.
     * 어느 스레드에서 불러도 된다. 접속이 끊겼으면 아무 일도 없다.
     */
    void sendPluginMessage(String channel, byte[] data);

    /** 이 플레이어로서 명령을 실행 큐에 넣는다(앞의 '/' 는 없어도 된다). 접수 여부를 돌려준다(결과는 나중에 정해진다). */
    boolean performCommand(String command);

    /** 플레이어 인벤토리 핸들(비동기 접근). */
    Inventory inventory();

    @Override
    default String type() { return "minecraft:player"; }

    @Override
    default Text customName() { return null; }

    @Override
    default boolean isValid() { return isOnline(); }

    /** 플레이어 핸들은 항상 최신 값을 읽으므로 자기 자신. */
    @Override
    default CompletableFuture<Player> refresh() { return CompletableFuture.completedFuture(this); }

    @Override
    default CompletableFuture<Void> updateHealth(float health) {
        setHealth(health);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    default CompletableFuture<Boolean> remove() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("플레이어는 remove 할 수 없다 — kick 을 쓴다"));
    }
}
