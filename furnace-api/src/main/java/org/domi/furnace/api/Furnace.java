// 서버 파사드 — 플러그인이 서버와 이야기하는 입구. FurnacePlugin.server() 또는 Furnace.server() 로 얻는다.
package org.domi.furnace.api;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.domi.furnace.api.command.CommandRegistry;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.EventBus;
import org.domi.furnace.api.permission.PermissionManager;
import org.domi.furnace.api.plugin.PluginManager;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.World;

public interface Furnace {

    /** 실행 중인 서버. 서버가 시작될 때 한 번 정해진다. */
    static Furnace server() {
        Furnace s = Holder.server;
        if (s == null) throw new IllegalStateException("서버가 아직 준비되지 않았다");
        return s;
    }

    /** 서버 구현 전용 — 한 번만 정할 수 있다. */
    static void setServer(Furnace server) {
        synchronized (Holder.class) {
            if (Holder.server != null && Holder.server != server) throw new IllegalStateException("서버는 한 번만 정할 수 있다");
            Holder.server = server;
        }
    }

    final class Holder {
        private static volatile Furnace server;

        private Holder() {}
    }

    /** "Furnace". */
    String name();

    String version();

    /** "1.21.1". */
    String minecraftVersion();

    Collection<? extends Player> onlinePlayers();

    Player player(UUID id);

    /** 이름(대소문자 무시)으로, 접속 중이 아니면 null. */
    Player player(String name);

    int maxPlayers();

    Text motd();

    /** 차원 키(minecraft:overworld)의 월드, 없으면 null. */
    World world(String dimension);

    /** 모든 차원 월드(오버월드 먼저). */
    java.util.List<? extends World> worlds();

    CommandSender console();

    /** 모든 플레이어와 콘솔에 시스템 메시지(바닐라 broadcastSystemMessage). */
    void broadcast(Text message);

    /**
     * 명령을 실행 큐에 넣는다(앞의 '/' 는 없어도 된다). 어느 스레드에서 불러도 되며 전역 스레드에서 실행된다.
     * 돌려주는 값은 접수 여부(true)다 — 명령 결과는 실행이 끝난 뒤 정해진다. 서버 종료 중이면 조용히 버려진다.
     */
    boolean dispatchCommand(CommandSender sender, String commandLine);

    FurnaceScheduler scheduler();

    /** 블로킹 작업용 실행기(가상 스레드). */
    Executor asyncExecutor();

    EventBus events();

    CommandRegistry commands();

    PermissionManager permissions();

    PluginManager plugins();

    /** 서버를 정상 종료한다(/stop 과 같다). */
    void shutdown();
}
