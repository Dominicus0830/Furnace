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

    /** 모든 차원 월드(오버월드 먼저, 그다음 네더·엔드, 추가 월드는 올린 순서). */
    java.util.List<? extends World> worlds();

    /** 월드 이름(폴더 이름 — 주 월드 world, world_nether, world_the_end, 추가 월드는 그 이름)으로, 없으면 null. 대소문자 구분. */
    default World worldNamed(String name) {
        for (World w : worlds()) if (w.name().equals(name)) return w;
        return null;
    }

    /**
     * 추가 월드를 만들거나 이미 있는 {@code <이름>/} 폴더를 불러온다(Bukkit createWorld, 다중 월드 — docs/api/worlds.md).
     * 월드 준비(폴더 잠금·level.dat·생성기)는 백그라운드에서 하고, 틱을 켠 뒤 {@link org.domi.furnace.api.event.world.WorldLoadEvent} 를 보내고 완료된다.
     * 같은 이름이 이미 올라와 있으면 그 월드로 완료된다(같은 설명서일 때). 키가 다른 월드와 겹치거나, 차원 타입을 모르거나 생성기와 높이가 안 맞으면
     * IllegalArgumentException, 서버가 다중 월드를 지원하지 않으면 UnsupportedOperationException 으로 완료된다. 아무 스레드.
     */
    default java.util.concurrent.CompletableFuture<World> createWorld(org.domi.furnace.api.world.WorldCreator creator) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("createWorld"));
    }

    /**
     * 추가 월드를 내린다(Bukkit unloadWorld). 기본 세 차원(오버월드·네더·엔드)은 내릴 수 없고, 월드 안에 플레이어가 있거나
     * {@link org.domi.furnace.api.event.world.WorldUnloadEvent} 가 취소되면 false 로 완료된다. save 면 청크·엔티티·level.dat 을 다 쓴 뒤 닫는다.
     * 내린 월드의 손잡이는 더 쓸 수 없다(블록 접근은 실패로 완료). 아무 스레드.
     */
    default java.util.concurrent.CompletableFuture<Boolean> unloadWorld(World world, boolean save) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("unloadWorld"));
    }

    CommandSender console();

    /** 모든 플레이어와 콘솔에 시스템 메시지(바닐라 broadcastSystemMessage). */
    void broadcast(Text message);

    /**
     * 명령을 실행 큐에 넣는다(앞의 '/' 는 없어도 된다). 어느 스레드에서 불러도 되며 전역 스레드에서 실행된다.
     * 돌려주는 값은 접수 여부(true)다 — 명령 결과는 실행이 끝난 뒤 정해진다. 서버 종료 중이면 조용히 버려진다.
     */
    boolean dispatchCommand(CommandSender sender, String commandLine);

    /** 서버가 아는 블록 종류 표(속성·값 범위·기본 상태). */
    org.domi.furnace.api.world.BlockTypes blockTypes();

    /** 레시피 레지스트리(데이터 팩 + 플러그인). */
    org.domi.furnace.api.recipe.RecipeRegistry recipes();

    FurnaceScheduler scheduler();

    /** 블로킹 작업용 실행기(가상 스레드). */
    Executor asyncExecutor();

    /** 플러그인별 시간 프로파일러(/timings). 서버가 주지 않으면 아무것도 재지 않는 것. */
    default org.domi.furnace.api.profiler.PluginProfiler profiler() { return org.domi.furnace.api.profiler.PluginProfiler.NONE; }

    EventBus events();

    CommandRegistry commands();

    /** 패킷 API — 보내기·가로채기·수정(버전 중립 타입). */
    org.domi.furnace.api.packet.PacketService packets();

    /** 가상 엔티티·가짜 플레이어(NPC)·가짜 탭 줄 — 패킷 API 위에 쌓은 서비스. */
    default org.domi.furnace.api.virtual.VirtualEntities virtualEntities() { throw new UnsupportedOperationException("virtualEntities"); }

    /** 실제 엔티티·플레이어의 플레이어별 보이기·숨기기와 변장. */
    default org.domi.furnace.api.entity.EntityViews entityViews() { throw new UnsupportedOperationException("entityViews"); }

    /** 플레이어별 화면 덮어쓰기 — 가상 블록·바이옴 층, 월드 보더, 장비 표시. */
    default org.domi.furnace.api.view.PlayerViews playerViews() { throw new UnsupportedOperationException("playerViews"); }

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

    /**
     * 서버 내부 객체("NMS")에 닿는 공식 다리. 안정 API 로 안 되는 일에만 쓰고, 내부 클래스는 버전마다 바뀔 수 있다
     * ({@link org.domi.furnace.api.internal.Internals} 문서, docs/api/internals.md).
     */
    default org.domi.furnace.api.internal.Internals internals() {
        throw new UnsupportedOperationException("이 서버 구현은 내부 접근을 제공하지 않는다");
    }

    /** 서버를 정상 종료한다(/stop 과 같다). */
    void shutdown();

    /**
     * 종료가 시작됐는가(ServerShutdownEvent 이후 true). onDisable 에서 "리로드인가 종료인가"를 가릴 때 쓴다.
     * 종료 순서는 docs/api/lifecycle.md.
     */
    boolean isStopping();

    /** 게임 동기화 레지스트리 보기·마법부여 끄기(불만 표 27·32번). */
    default org.domi.furnace.api.registry.GameRegistries gameRegistries() { throw new UnsupportedOperationException("gameRegistries"); }

    /** 게임 규칙(바닐라 + 플러그인 규칙). */
    default org.domi.furnace.api.world.GameRuleRegistry gameRules() { throw new UnsupportedOperationException("gameRules"); }

    /** 난이도·난이도 프로필. */
    default org.domi.furnace.api.world.DifficultySettings difficulty() { throw new UnsupportedOperationException("difficulty"); }

    /**
     * UUID 로 가리키는 오프라인 플레이어(접속 중이어도 된다). 서버가 한 번도 못 본 UUID 도 받으며 그 경우 name() 은 null.
     */
    default org.domi.furnace.api.entity.OfflinePlayer offlinePlayer(UUID id) { throw new UnsupportedOperationException("offlinePlayer"); }

    /**
     * 이름(대소문자 무시)으로 오프라인 플레이어를 찾는다. 서버가 아는 이름(접속 중 + usercache.json)만 보고 없으면 null.
     * 인증 서버에 묻지 않으므로 아무 스레드에서 불러도 막히지 않는다.
     */
    default org.domi.furnace.api.entity.OfflinePlayer offlinePlayerCached(String name) { throw new UnsupportedOperationException("offlinePlayerCached"); }

    /** 플레이어 데이터 파일이 있는 모든 플레이어(디렉터리를 읽으니 비동기). */
    default java.util.concurrent.CompletableFuture<java.util.List<org.domi.furnace.api.entity.OfflinePlayer>> knownPlayers() {
        throw new UnsupportedOperationException("knownPlayers");
    }

    /** OP·밴·IP 밴·화이트리스트 목록. */
    default org.domi.furnace.api.access.AccessLists access() { throw new UnsupportedOperationException("access"); }

    /** 서비스 레지스트리(권한·경제·플레이스홀더 등 플러그인 사이 공용 인터페이스). */
    default org.domi.furnace.api.service.ServiceRegistry services() { throw new UnsupportedOperationException("services"); }
}
