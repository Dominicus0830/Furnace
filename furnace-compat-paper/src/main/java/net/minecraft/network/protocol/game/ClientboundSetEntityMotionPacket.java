package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import net.minecraft.world.phys.Vec3;

/**
 * 엔티티 속도(Mojang 이름, H7c). 바닐라처럼 축마다 ±3.9 로 자르고 1/8000 단위 정수로 담는다 — 읽기 메서드도 그 값을 돌려준다.
 * 바닐라의 {@code (Entity)} 생성자는 네트워크 엔티티 id 를 내보내지 않아 두지 않았다.
 */
public class ClientboundSetEntityMotionPacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final int id;
    private final int xa;
    private final int ya;
    private final int za;

    public ClientboundSetEntityMotionPacket(int id, Vec3 motion) {
        this.id = id;
        this.xa = (int) (clamp(motion.x) * 8000.0);
        this.ya = (int) (clamp(motion.y) * 8000.0);
        this.za = (int) (clamp(motion.z) * 8000.0);
    }

    /** 호환 모듈 전용(ProtocolLib 다리 2b-2): 와이어 값(1/8000 단위 정수) 그대로 — Furnace 가 보내는 패킷을 shim 으로 되돌릴 때 반올림이 끼지 않게. */
    public static ClientboundSetEntityMotionPacket ofWire(int id, int xa, int ya, int za) { return new ClientboundSetEntityMotionPacket(id, xa, ya, za); }

    private ClientboundSetEntityMotionPacket(int id, int xa, int ya, int za) {
        this.id = id;
        this.xa = xa;
        this.ya = ya;
        this.za = za;
    }

    private static double clamp(double v) { return v < -3.9 ? -3.9 : Math.min(v, 3.9); }

    public int getId() { return id; }

    public double getXa() { return xa / 8000.0; }

    public double getYa() { return ya / 8000.0; }

    public double getZa() { return za / 8000.0; }

    @Override
    public ClientboundPacket toFurnace() {
        // 와이어는 short — 서버 코덱이 읽는 값과 같게(바닐라 writeShort 처럼 아래 16비트)
        return new org.domi.furnace.api.packet.clientbound.ClientboundSetEntityMotionPacket(id, (short) xa / 8000.0, (short) ya / 8000.0, (short) za / 8000.0);
    }
}
