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

    /** 패킷 API — 보내기·가로채기·수정(버전 중립 타입). */
    org.domi.furnace.api.packet.PacketService packets();

    PermissionManager permissions();

    PluginManager plugins();

    /** 서버 시야 거리(청크). */
    int viewDistance();

    /** 시야 거리를 바꾼다(2~32 로 자름, 모든 차원). 접속자에게 set_chunk_cache_radius 를 보내고 청크 전송 범위를 다시 잡는다. */
    void setViewDistance(int chunks);

    /** 시뮬레이션 거리(청크). */
    int simulationDistance();

    /** 시뮬레이션 거리를 바꾼다(2~32 로 자름). 접속자에게 set_simulation_distance 를 보낸다. */
    void setSimulationDistance(int chunks);

    /**
     * 플러그인이 이 채널의 메시지를 받는다고 알린다(Bukkit registerIncomingPluginChannel). 접속하는 클라이언트와 지금 접속자에게
     * minecraft:register 로 보낸다. 채널은 "네임스페이스:이름" 소문자 형식이고 minecraft:register·unregister·brand 는 쓸 수 없다.
     * 받은 메시지는 그대로 PluginMessageEvent 로 온다(등록과 관계없이). 플러그인이 꺼지면 그 플러그인의 등록은 자동으로 풀린다.
     */
    void registerPluginChannel(org.domi.furnace.api.plugin.FurnacePlugin owner, String channel);

    /** 등록을 푼다. 다른 플러그인도 같은 채널을 등록했으면 클라이언트에는 계속 등록돼 있다. */
    void unregisterPluginChannel(org.domi.furnace.api.plugin.FurnacePlugin owner, String channel);

    /** 모든 플러그인이 등록한 채널(이름순). */
    java.util.SortedSet<String> pluginChannels();

    /** 서버를 정상 종료한다(/stop 과 같다). */
    void shutdown();
}
