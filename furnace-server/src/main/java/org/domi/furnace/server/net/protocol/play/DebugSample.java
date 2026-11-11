// S→C debug_sample(0x1B) — OP 가 F3 틱 시간 그래프를 켰을 때 틱마다 보내는 표본. long 배열(VarInt 길이 + 값) + 종류(VarInt, TICK_TIME = 0).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record DebugSample(long[] sample, int type) implements Packet {

    public static final int ID = 0x1B;
    /** 바닐라 RemoteDebugSampleType.TICK_TIME. */
    public static final int TICK_TIME = 0;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, sample.length);
        for (long v : sample) buf.writeLong(v);
        Types.writeVarInt(buf, type);
    }
}
