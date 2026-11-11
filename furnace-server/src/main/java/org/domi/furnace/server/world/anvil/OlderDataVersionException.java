// 이 서버(1.21.1, DataVersion 3955)보다 옛 판으로 저장된 데이터를 열지 않는다(A05-04).
// 바닐라·Paper 는 데이터 픽서로 올려서 읽지만 Furnace 에는 청크·플레이어용 픽서가 아직 없다(구조물용 Q8 표만 있다).
// 픽서 없이 읽으면 1.20.5 이후 컴포넌트로 바뀐 아이템(개수·마법 부여·이름)이 사라지고, 1.18 이전 청크(높이 범위·팔레트 형식)는
// 빈 청크로 보이며 그 위에 무엇이든 놓으면 원래 땅이 덮인다. 그래서 읽지도, 새로 생성해 덮지도 않는다.
// 탈출구(-Dfurnace.ignoreOlderWorldDataVersion=true)는 그 손실을 알고 쓰는 사람용이다.
package org.domi.furnace.server.world.anvil;

import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class OlderDataVersionException extends UnsupportedDataVersionException {

    public static final int CURRENT = AnvilChunkCodec.DATA_VERSION;
    /**
     * 바닐라 1.21.1 DataFixers(azx)의 마지막 스키마가 3945 라서 3945~3954(1.21 계열) 데이터는 바닐라도 변환 없이 읽는다 — 그 이상이면 통과.
     * 3944 이하부터 픽서가 일하므로(Furnace 에는 없다) 거부한다.
     */
    public static final int MIN_READABLE = 3945;
    public static final String IGNORE_PROPERTY = "furnace.ignoreOlderWorldDataVersion";

    public OlderDataVersionException(String what, int dataVersion) {
        super(what + " 의 데이터 버전 " + dataVersion + " 이 이 서버가 변환 없이 읽는 최소 판(" + MIN_READABLE + ", 1.21)보다 옛것이다 — 옛 판 데이터를 올리는 픽서가 아직 없어"
                + " 열지 않는다(아이템 개수·마법 부여·블록이 사라진다). 바닐라 1.21.1 로 한 번 열어 저장한 월드를 가져오거나, 손실을 알고 강제로 열려면 -D"
                + IGNORE_PROPERTY + "=true", dataVersion);
    }

    public static boolean ignored() {
        return Boolean.getBoolean(IGNORE_PROPERTY);
    }

    /** 뿌리의 DataVersion 이 MIN_READABLE 보다 작으면(없으면 0 — 1.9 이전) 던진다. */
    public static void check(NbtCompound root, String what) {
        int v = root.tags().get("DataVersion") instanceof Number n ? n.intValue() : 0;
        if (v < MIN_READABLE && !ignored()) throw new OlderDataVersionException(what, v);
    }
}
