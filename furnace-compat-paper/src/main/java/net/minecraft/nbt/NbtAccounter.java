package net.minecraft.nbt;

/**
 * NBT 읽기 한도(Mojang 이름, 바닐라 1.21.1 모양). 바이트 한도·깊이를 세고, 넘으면 바닐라와 같은 문구의 {@link NbtAccounterException}.
 * ProtocolLib StructureCache 는 {@code getNBTReadLimiterClass()} 로 이 클래스를 이름으로 찾는다(PR #399 검토 R1). {@link NbtIo} 가 바닐라처럼
 * 태그마다 쓴 바이트를 센다(NMS shim 5e-3, Axiom). 패킷 NBT 읽기는 compat NbtWire 가 따로 하고 깊이 512 를 지킨다.
 */
public class NbtAccounter {

    private static final int MAX_STACK_DEPTH = 512;

    private final long quota;
    private long usage;
    private final int maxDepth;
    private int depth;

    public NbtAccounter(long quota, int maxDepth) {
        this.quota = quota;
        this.maxDepth = maxDepth;
    }

    /** 바닐라: 바이트 한도 quota, 깊이 512. */
    public static NbtAccounter create(long quota) { return new NbtAccounter(quota, MAX_STACK_DEPTH); }

    /** 바닐라: 바이트 한도 없음, 깊이 512. */
    public static NbtAccounter unlimitedHeap() { return new NbtAccounter(Long.MAX_VALUE, MAX_STACK_DEPTH); }

    public void accountBytes(long multiplier, long bytes) { accountBytes(multiplier * bytes); }

    public void accountBytes(long bytes) {
        if (usage + bytes > quota) {
            throw new NbtAccounterException("Tried to read NBT tag that was too big; tried to allocate: " + usage + " + " + bytes
                    + " bytes where max allowed: " + quota);
        }
        usage += bytes;
    }

    public void pushDepth() {
        if (depth >= maxDepth) throw new NbtAccounterException("Tried to read NBT tag with too high complexity, depth > " + maxDepth);
        depth++;
    }

    public void popDepth() {
        if (depth <= 0) throw new NbtAccounterException("NBT-Accounter tried to pop stack-depth at top-level");
        depth--;
    }

    public long getUsage() { return usage; }

    public int getDepth() { return depth; }
}
