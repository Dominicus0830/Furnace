// 바닐라 RconConsoleSource — RCON 명령의 출력 대상. 명령 컨텍스트가 내보내는 메시지를 평문으로 모아 응답으로 돌려준다.
// 바닐라처럼 메시지 사이에 줄바꿈을 넣지 않고 이어 붙인다. 이름 "Rcon", 권한 4(CommandSource.rcon), 성공·실패 메시지를 모두 받는다.
// 다른 OP 에게 알릴지는 broadcast-rcon-to-ops. 메시지는 전역 스레드에서 오고 응답은 RCON IO 스레드가 읽으므로 StringBuffer 를 쓴다.
package org.domi.furnace.server.remote;

import java.util.function.BooleanSupplier;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.BlockCommandSink;
import org.domi.furnace.server.entity.Rider;

public final class RconConsole implements BlockCommandSink {

    private final StringBuffer buffer = new StringBuffer();
    private final BooleanSupplier broadcastToOps;
    private final double x, y, z;

    public RconConsole(BooleanSupplier broadcastToOps, double x, double y, double z) {
        this.broadcastToOps = broadcastToOps;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** 모인 출력을 꺼내고 비운다. */
    public String takeOutput() {
        String s = buffer.toString();
        buffer.setLength(0);
        return s;
    }

    @Override public String textName() { return "Rcon"; }

    @Override public Text displayName() { return Text.text("Rcon"); }

    @Override public double x() { return x; }

    @Override public double y() { return y; }

    @Override public double z() { return z; }

    @Override public float xRot() { return 0f; }

    @Override public float yRot() { return 0f; }

    @Override public Rider entity() { return null; }

    @Override public boolean acceptsSuccess() { return true; }

    @Override public boolean acceptsFailure() { return true; }

    @Override public boolean shouldInformAdmins() { return broadcastToOps.getAsBoolean(); }

    @Override public void sendSystemMessage(Text message) { buffer.append(message.getString()); }
}
