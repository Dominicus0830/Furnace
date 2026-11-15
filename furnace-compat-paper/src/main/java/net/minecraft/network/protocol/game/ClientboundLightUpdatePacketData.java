package net.minecraft.network.protocol.game;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 빛 자료 shim(Mojang 이름, T-182). 바닐라 1.21.1 과 같은 모양 — 마스크 넷(하늘·블록 빛이 있는 구역, 빈 구역)과 2048바이트 빛 배열 목록 둘.
 * 패킷 라이브러리는 필드를 형식과 순서로 찾는다(ProtocolLib WrappedLevelChunkData.LightData: BitSet 필드 0~3, List 필드 0~1).
 * 목록은 바꿀 수 있는 ArrayList 다(라이브러리가 {@code getSkyUpdates().addAll(...)} 로 채운다).
 */
public class ClientboundLightUpdatePacketData {

    private final BitSet skyYMask;
    private final BitSet blockYMask;
    private final BitSet emptySkyYMask;
    private final BitSet emptyBlockYMask;
    private final List<byte[]> skyUpdates;
    private final List<byte[]> blockUpdates;

    /** 바닐라: 바이트에서 읽기(x·z 는 바닐라도 쓰지 않는다). 라이브러리가 빈 자료를 만들 때 0 버퍼로 부른다. */
    public ClientboundLightUpdatePacketData(FriendlyByteBuf buf, int x, int z) {
        this.skyYMask = BitSet.valueOf(buf.readLongArray());
        this.blockYMask = BitSet.valueOf(buf.readLongArray());
        this.emptySkyYMask = BitSet.valueOf(buf.readLongArray());
        this.emptyBlockYMask = BitSet.valueOf(buf.readLongArray());
        this.skyUpdates = layers(buf);
        this.blockUpdates = layers(buf);
    }

    /** 이 모듈 전용: 값으로(Furnace 패킷에서 되돌릴 때). 바닐라에 없는 생성자라 공개하지 않는다. */
    public static ClientboundLightUpdatePacketData furnace(BitSet skyYMask, BitSet blockYMask, BitSet emptySkyYMask, BitSet emptyBlockYMask,
            List<byte[]> skyUpdates, List<byte[]> blockUpdates) {
        return new ClientboundLightUpdatePacketData(skyYMask, blockYMask, emptySkyYMask, emptyBlockYMask, skyUpdates, blockUpdates);
    }

    private ClientboundLightUpdatePacketData(BitSet skyYMask, BitSet blockYMask, BitSet emptySkyYMask, BitSet emptyBlockYMask, List<byte[]> skyUpdates,
            List<byte[]> blockUpdates) {
        this.skyYMask = skyYMask;
        this.blockYMask = blockYMask;
        this.emptySkyYMask = emptySkyYMask;
        this.emptyBlockYMask = emptyBlockYMask;
        this.skyUpdates = new ArrayList<>(skyUpdates);
        this.blockUpdates = new ArrayList<>(blockUpdates);
    }

    private static List<byte[]> layers(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<byte[]> out = new ArrayList<>(Math.min(n, 64));
        for (int i = 0; i < n; i++) out.add(buf.readByteArray(2048));
        return out;
    }

    /** 바닐라: 바이트로 쓰기. */
    public void write(FriendlyByteBuf buf) {
        buf.writeLongArray(skyYMask.toLongArray());
        buf.writeLongArray(blockYMask.toLongArray());
        buf.writeLongArray(emptySkyYMask.toLongArray());
        buf.writeLongArray(emptyBlockYMask.toLongArray());
        writeLayers(buf, skyUpdates);
        writeLayers(buf, blockUpdates);
    }

    private static void writeLayers(FriendlyByteBuf buf, List<byte[]> layers) {
        buf.writeVarInt(layers.size());
        for (byte[] a : layers) buf.writeByteArray(a);
    }

    public List<byte[]> getSkyUpdates() { return skyUpdates; }

    public BitSet getSkyYMask() { return skyYMask; }

    public BitSet getEmptySkyYMask() { return emptySkyYMask; }

    public List<byte[]> getBlockUpdates() { return blockUpdates; }

    public BitSet getBlockYMask() { return blockYMask; }

    public BitSet getEmptyBlockYMask() { return emptyBlockYMask; }
}
