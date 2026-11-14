// 서버 내부 객체("NMS")에 닿는 공식 다리. Furnace 내부 코드는 난독화되지 않은 평범한 자바라서, furnace-server 를
// compileOnly 로 받고 이 다리로 객체를 꺼내 쓰면 리플렉션·매핑 없이 바로 부를 수 있다(docs/api/internals.md).
//
// 보장하는 것: 이 인터페이스 자체(버전 중립), 소유 스레드 규칙(꺼낸 객체는 그 객체를 가진 region 스레드에서만 건네준다), 친절한 오류.
// 보장하지 않는 것: 내부 클래스·메서드 이름과 동작 — 마인크래프트 버전이나 Furnace 버전이 바뀌면 달라질 수 있다.
// 그래서 (1) 먼저 안정 API(엔티티·블록 엔티티 NBT, 이벤트, 스케줄러)로 되는지 보고, (2) 내부 접근은 플러그인 안 한 곳에 모으고,
// (3) onEnable 첫 줄에 requireMinecraft("1.21.1") 로 지원 버전을 밝힌다. 안정 API 로 올려야 할 쓰임새는 이슈로 알려 달라.
package org.domi.furnace.api.internal;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.World;

public interface Internals {

    /** 실행 중인 서버의 다리 — {@code Furnace.server().internals()} 와 같다. 처음 부른 플러그인 이름을 서버 로그에 한 번 남긴다. */
    static Internals get() { return Furnace.server().internals(); }

    // ------------------------------------------------------------------ 버전

    /** "1.21.1". */
    String minecraftVersion();

    /** 프로토콜 번호(1.21.1 = 767). */
    int protocolVersion();

    /** 저장 데이터 버전(1.21.1 = 3955) — NBT 키 구조가 이 버전을 따른다. */
    int dataVersion();

    /** 지금 마인크래프트 버전이 versions 중 하나인가. */
    default boolean isMinecraft(String... versions) {
        String v = minecraftVersion();
        for (String s : versions) if (v.equals(s)) return true;
        return false;
    }

    /**
     * 지금 마인크래프트 버전이 versions 중 하나가 아니면 {@link UnsupportedVersionException}. 내부 접근을 쓰는 플러그인은
     * onEnable 첫 줄에 불러 두면, 맞지 않는 서버에서 엉뚱한 NoSuchMethodError 대신 이유가 분명한 오류로 켜지지 않는다.
     */
    default void requireMinecraft(String... versions) {
        if (!isMinecraft(versions)) throw new UnsupportedVersionException(minecraftVersion(), versions);
    }

    // ------------------------------------------------------------------ 엔티티

    /**
     * 엔티티를 가진 region 스레드에서 내부 객체를 type 으로 꺼내 action 을 돌린다(엔티티를 따라간다 — 다른 region 으로 넘어가도 된다).
     * 내부 객체는 플레이어면 {@code org.domi.furnace.server.entity.Player}, 그 밖은 {@code org.domi.furnace.server.entity.Entity} 의 하위 클래스.
     * 엔티티가 사라졌으면 IllegalStateException, 내부 객체가 type 이 아니면 ClassCastException(실제 클래스 이름이 메시지에)으로 완료.
     * action 이 받은 객체를 밖으로 들고 나가 다른 스레드에서 쓰지 않는다.
     */
    <H, T> CompletableFuture<T> withEntity(Entity entity, Class<H> type, Function<? super H, ? extends T> action);

    /** 돌려줄 값이 없는 {@link #withEntity}. */
    default <H> CompletableFuture<Void> runWithEntity(Entity entity, Class<H> type, Consumer<? super H> action) {
        return withEntity(entity, type, h -> {
            action.accept(h);
            return null;
        });
    }

    /**
     * 지금 스레드가 그 엔티티를 가진 region 스레드일 때만 내부 객체를 바로 돌려준다(예: 그 region 에서 발화한 이벤트 처리기 안, entityScheduler 작업 안).
     * 아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}, 사라졌으면 IllegalStateException, 종류가 다르면 ClassCastException.
     */
    <H> H entityNow(Entity entity, Class<H> type);

    /** 내부 엔티티(서버 Entity 또는 Player) → API 핸들. 그 엔티티를 가진 region 스레드에서만(아니면 ThreadOwnershipException). */
    Entity wrapEntity(Object internalEntity);

    // ------------------------------------------------------------------ 칸·region

    /**
     * 그 칸을 가진 region 스레드에서 그 region 의 레벨({@code org.domi.furnace.server.world.RegionLevel} — 블록·블록 엔티티·엔티티·빛 접근)을
     * type 으로 꺼내 action 을 돌린다. 청크가 로드돼 있지 않으면 IllegalStateException 으로 완료(청크를 만들지 않는다).
     * 레벨은 그 region 이 가진 칸만 다룬다 — region 경계 너머 칸은 그 칸의 region 으로 따로 보낸다.
     */
    <H, T> CompletableFuture<T> withLevel(World world, BlockPos pos, Class<H> type, Function<? super H, ? extends T> action);

    /** 지금 스레드가 그 칸을 가진 region 스레드일 때만 그 레벨을 바로 돌려준다. 아니면 ThreadOwnershipException. */
    <H> H levelNow(World world, BlockPos pos, Class<H> type);

    // ------------------------------------------------------------------ 월드·서버 전체

    /**
     * 차원 월드의 내부 객체({@code org.domi.furnace.server.world.World} — 차원 설정·레지스트리·region 관리). 아무 스레드에서 꺼낼 수 있지만
     * 그 객체 메서드의 스레드 규칙(블록·엔티티는 소유 region 에서)은 부르는 쪽이 지킨다 — 블록·엔티티는 {@link #withLevel}·{@link #withEntity} 로.
     */
    <H> H world(World world, Class<H> type);

    /** 서버 내부 객체({@code org.domi.furnace.server.ServerCore}). 아무 스레드에서 꺼낼 수 있지만 스레드 규칙은 {@link #world} 와 같다. */
    <H> H server(Class<H> type);

    /** 전역 스레드(게임 규칙·시간·날씨·점수판·명령처럼 region 밖 상태의 주인)에서 서버 내부 객체로 action 을 돌린다. */
    <H, T> CompletableFuture<T> withServer(Class<H> type, Function<? super H, ? extends T> action);

    /** 내부 월드 → API 월드(아무 스레드). 모르는 객체면 IllegalArgumentException. */
    World wrapWorld(Object internalWorld);

    // ------------------------------------------------------------------ 청크 내부(N2a)

    /**
     * 청크 섹션·하이트맵·바이옴의 번호 단위 읽기 다리 — Bukkit·Paper 호환 계층의 NMS 청크 shim 이 쓴다(불안정, 일반 플러그인은 World API).
     * 이 서버가 다리를 주지 않으면 UnsupportedOperationException.
     */
    default ChunkInternals chunks() { throw new UnsupportedOperationException("chunks"); }
}
