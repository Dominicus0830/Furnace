// 바닐라 ChainModifiers(hn) — bit1 forked(as @e 같은 포크 수정자를 지났다), bit2 isReturn(/return run).
package org.domi.furnace.server.command.exec;

public record ChainModifiers(byte flags) {

    public static final ChainModifiers DEFAULT = new ChainModifiers((byte) 0);
    private static final byte FLAG_FORKED = 1;
    private static final byte FLAG_IS_RETURN = 2;

    private ChainModifiers setFlag(byte flag) {
        int f = flags | flag;
        return f != flags ? new ChainModifiers((byte) f) : this;
    }

    public boolean isForked() { return (flags & FLAG_FORKED) != 0; }

    public ChainModifiers setForked() { return setFlag(FLAG_FORKED); }

    public boolean isReturn() { return (flags & FLAG_IS_RETURN) != 0; }

    public ChainModifiers setReturn() { return setFlag(FLAG_IS_RETURN); }
}
