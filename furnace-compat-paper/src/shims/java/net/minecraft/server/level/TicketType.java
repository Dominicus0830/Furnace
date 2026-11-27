package net.minecraft.server.level;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;

/**
 * 청크 티켓 종류(Mojang 이름 shim, N2b-6) — 이름·비교기·만료 틱(0 = 만료 없음)은 Paper 1.21.1 그대로. 티켓을 거는 길은
 * {@link ServerChunkCache#addRegionTicket}·{@link ServerChunkCache#addTicketAtLevel}(Furnace 청크 티켓으로 옮긴다 — {@code NmsTickets}).
 */
class TicketType {

    public static final TicketType<Long> FUTURE_AWAIT = create("future_await", Long::compareTo);
    private final String name;
    private final Comparator<T> comparator;
    public long timeout;
    public static final TicketType<Unit> START = create("start", (a, b) -> 0);
    public static final TicketType<Unit> DRAGON = create("dragon", (a, b) -> 0);
    public static final TicketType<ChunkPos> PLAYER = create("player", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<ChunkPos> FORCED = create("forced", Comparator.comparingLong(ChunkPos::toLong));
    public static final TicketType<BlockPos> PORTAL = create("portal", BlockPos::compareTo, 300);
    public static final TicketType<Integer> POST_TELEPORT = create("post_teleport", Integer::compareTo, 5);
    public static final TicketType<ChunkPos> UNKNOWN = create("unknown", Comparator.comparingLong(ChunkPos::toLong), 1);
    public static final TicketType<Unit> PLUGIN = create("plugin", (a, b) -> 0);
    public static final TicketType<org.bukkit.plugin.Plugin> PLUGIN_TICKET = create("plugin_ticket", (a, b) -> a.getName().compareTo(b.getName()));

    public static <T> TicketType<T> create(String name, Comparator<T> comparator) { return new TicketType<>(name, comparator, 0L); }

    public static <T> TicketType<T> create(String name, Comparator<T> comparator, int timeout) { return new TicketType<>(name, comparator, timeout); }

    protected TicketType(String name, Comparator<T> comparator, long timeout) {
        this.name = name;
        this.comparator = comparator;
        this.timeout = timeout;
    }

    @Override
    public String toString() { return name; }

    public Comparator<T> getComparator() { return comparator; }

    public long timeout() { return timeout; }
}
