// 접속 중이 아닐 수도 있는 플레이어(UUID 로 가리킨다). 저장 데이터(playerdata) 읽기·영속 데이터 편집과 OP 레벨·밴·화이트리스트를 한 곳에서 다룬다.
// 접속 중이면 데이터 편집은 플레이어를 가진 스레드로 넘어가고(접속자 메모리가 정본이라 파일을 고쳐도 퇴장 때 덮이므로), 아니면 파일을 고친다.
// OP·밴·화이트리스트 목록은 서버 목록 파일(ops.json·banned-players.json·whitelist.json)을 바로 고치며 어느 스레드에서 불러도 된다.
package org.domi.furnace.api.entity;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.domi.furnace.api.access.BanEntry;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;

public interface OfflinePlayer {

    UUID uniqueId();

    /** 마지막으로 알려진 이름. 이 서버가 한 번도 보지 못한 UUID 면 null. */
    String name();

    /** 지금 접속 중인가. */
    boolean isOnline();

    /** 접속 중이면 그 플레이어, 아니면 null. */
    Player player();

    // ------------------------------------------------------------------ 저장 데이터

    /** 플레이어 데이터 파일(playerdata/&lt;uuid&gt;.dat)이 있는가. 접속 중이면 항상 true. */
    CompletableFuture<Boolean> hasPlayedBefore();

    /**
     * 저장된 플레이어 NBT 의 사본(읽기 전용 취급). 데이터가 없으면 null 로 끝난다. 접속 중이면 지금 상태의 스냅샷이다.
     * 파일 읽기는 서버 밖 스레드(플레이어 데이터 IO)에서 한다.
     */
    CompletableFuture<NbtCompound> readNbt();

    /** 저장된 영속 데이터(BukkitValues)의 사본. 데이터가 없으면 null 로 끝난다. */
    CompletableFuture<PersistentDataView> persistentData();

    /**
     * {@link #persistentData()} 의 동기판 — 이 스레드에서 곧바로 돌려준다(접속 중이면 지금 값, 아니면 저장 파일을 **이 스레드에서 읽는다 — 블로킹 IO**).
     * 서버 스레드에서 부르면 읽는 동안 틱이 멈추므로 드물게만(Bukkit/Paper 의 OfflinePlayer PDC 보기가 이렇게 동작한다). 데이터가 없으면 null.
     */
    default PersistentDataView persistentDataNow() { throw new UnsupportedOperationException("persistentDataNow"); }

    /**
     * 영속 데이터를 고친다. 접속 중이면 그 플레이어를 가진 스레드에서, 아니면 파일을 읽고 고쳐 써서(이전 파일은 .dat_old) 바꾼다.
     * 데이터 파일이 없는 플레이어는 만들지 않고 false 로 끝난다(손상 파일이 되기 때문). edit 가 예외를 던지면 아무것도 쓰지 않고 그 예외로 끝난다.
     * 편집 도중 그 플레이어가 접속하면 접속 쪽으로 넘겨 고친다(편집이 사라지지 않는다).
     */
    CompletableFuture<Boolean> editPersistentData(Consumer<PersistentDataContainer> edit);

    // ------------------------------------------------------------------ OP

    /** ops.json 의 OP 레벨(1~4), OP 가 아니면 0. */
    int opLevel();

    default boolean isOp() { return opLevel() > 0; }

    /**
     * OP 레벨을 정한다. 0 이면 OP 를 푼다(/deop). 1~4 는 /op 와 같지만 레벨을 직접 정한다.
     * 접속 중이면 권한 레벨 패킷과 명령 트리를 다시 보내고, 푼 경우 enforce-whitelist 규칙대로 화이트리스트에 없으면 내보낸다.
     * 이름을 모르는 플레이어는 IllegalStateException.
     */
    void setOpLevel(int level);

    // ------------------------------------------------------------------ 밴

    boolean isBanned();

    /** 밴 항목, 밴이 아니면 null. */
    BanEntry banEntry();

    /**
     * 밴한다(/ban 과 같다). 이미 밴이면 항목을 바꿔 다시 쓴다. 접속 중이면 내보낸다.
     *
     * @param reason  null 이면 "Banned by an operator."
     * @param expires null 이면 영구
     * @param source  null 이면 "(Unknown)"
     */
    void ban(String reason, Instant expires, String source);

    default void ban(String reason) { ban(reason, null, null); }

    /** 밴을 푼다(/pardon). 밴이 아니었으면 false. */
    boolean unban();

    // ------------------------------------------------------------------ 화이트리스트

    boolean isWhitelisted();

    /** 목록에 넣거나 뺀다(/whitelist add·remove). 뺀 경우 enforce-whitelist 규칙대로 접속 중이면 내보낸다. */
    void setWhitelisted(boolean listed);
}
