// 이 서버(1.21.1, DataVersion 3955)보다 새 판으로 저장된 데이터를 열지 않는다(A05-03).
// 바닐라 1.21.1 은 판 계열("main")만 비교해 새 판 월드를 그대로 열고, 모르는 블록을 공기로 읽어 다음 저장에서 지운다.
// Paper 는 "Do not let the server load chunks from newer versions" 패치로 그런 청크를 만나면 서버를 멈춘다(-DPaper.ignoreWorldDataVersion=true 로 끈다).
// Furnace 는 기동 때 level.dat 판을 보고 멈추고(LevelStorage), 청크 하나만 새 판이면 그 청크를 올리지 않는다(새로 생성해 덮지도 않는다).
package org.domi.furnace.server.world.anvil;

import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class NewerDataVersionException extends RuntimeException {

    /** 이 서버가 읽고 쓰는 데이터 판(1.21.1). */
    public static final int CURRENT = AnvilChunkCodec.DATA_VERSION;

    /** Paper 와 같은 이름의 탈출구: 켜면 새 판 데이터도 연다(모르는 블록·아이템은 지워진다). */
    public static final String IGNORE_PROPERTY = "Paper.ignoreWorldDataVersion";

    private final int dataVersion;

    public NewerDataVersionException(String what, int dataVersion) {
        super(what + " 의 데이터 버전 " + dataVersion + " 이 이 서버(" + CURRENT + ", 1.21.1)보다 새것이다 — 새 판에서 저장된 데이터는 열지 않는다"
                + "(모르는 블록·아이템이 지워진다). 강제로 열려면 -D" + IGNORE_PROPERTY + "=true");
        this.dataVersion = dataVersion;
    }

    public int dataVersion() { return dataVersion; }

    /** 기본은 꺼짐 — 새 판 데이터를 거부한다. */
    public static boolean ignored() {
        return Boolean.getBoolean(IGNORE_PROPERTY);
    }

    /** 청크 뿌리의 DataVersion 이 이 서버보다 새것이면 던진다(Paper ChunkSerializer.read 와 같은 자리·같은 조건). */
    public static void check(NbtCompound chunkRoot) {
        if (chunkRoot.tags().get("DataVersion") instanceof Number n) {
            int v = n.intValue();
            if (v > CURRENT && !ignored()) {
                int x = chunkRoot.tags().get("xPos") instanceof Number nx ? nx.intValue() : 0;
                int z = chunkRoot.tags().get("zPos") instanceof Number nz ? nz.intValue() : 0;
                throw new NewerDataVersionException("청크 " + x + "," + z, v);
            }
        }
    }
}
