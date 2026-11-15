package net.minecraft.world.level.chunk;

/**
 * 섹션 하나의 빛 값 16×16×16 니블 배열(Mojang 이름 shim, N2b-6). 바닐라 1.21.1 그대로 — 배열이 없으면(아직 안 씀) 모든 칸이 기본값이고,
 * 처음 쓸 때 기본값으로 채운 2048바이트를 만든다. 칸 순서는 YZX, 짝수 칸이 아래 니블.
 * 서버 빛과는 이어지지 않은 떨어진 값이다 — 서버 빛을 읽고 쓰는 길(빛 엔진의 층 듣기·queueSectionData)은 N2b-5.
 */
public class DataLayer {

    public static final int LAYER_COUNT = 16;
    public static final int LAYER_SIZE = 128;
    public static final int SIZE = 2048;
    private static final int NIBBLE_SIZE = 4;

    protected byte[] data;
    private int defaultValue;

    public DataLayer() { this(0); }

    public DataLayer(int defaultValue) { this.defaultValue = defaultValue; }

    public DataLayer(byte[] data) {
        this.data = data;
        this.defaultValue = 0;
        if (data.length != SIZE) throw new IllegalArgumentException("DataLayer should be 2048 bytes not: " + data.length);
    }

    public int get(int x, int y, int z) { return get(getIndex(x, y, z)); }

    public void set(int x, int y, int z, int value) { set(getIndex(x, y, z), value); }

    private static int getIndex(int x, int y, int z) { return y << 8 | z << 4 | x; }

    private int get(int index) {
        if (data == null) return defaultValue;
        int b = getByteIndex(index);
        int n = getNibbleIndex(index);
        return data[b] >> NIBBLE_SIZE * n & 15;
    }

    private void set(int index, int value) {
        byte[] d = getData();
        int b = getByteIndex(index);
        int n = getNibbleIndex(index);
        int mask = ~(15 << NIBBLE_SIZE * n);
        int v = (value & 15) << NIBBLE_SIZE * n;
        d[b] = (byte) (d[b] & mask | v);
    }

    private static int getNibbleIndex(int index) { return index & 1; }

    private static int getByteIndex(int index) { return index >> 1; }

    public void fill(int value) {
        defaultValue = value;
        data = null;
    }

    private static byte packFilled(int value) {
        byte b = (byte) value;
        for (int i = NIBBLE_SIZE; i < 8; i += NIBBLE_SIZE) b = (byte) (b | value << i);
        return b;
    }

    public byte[] getData() {
        if (data == null) {
            data = new byte[SIZE];
            if (defaultValue != 0) java.util.Arrays.fill(data, packFilled(defaultValue));
        }
        return data;
    }

    public DataLayer copy() { return data == null ? new DataLayer(defaultValue) : new DataLayer(data.clone()); }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            b.append(Integer.toHexString(get(i)));
            if ((i & 15) == 15) b.append('\n');
            if ((i & 255) == 255) b.append('\n');
        }
        return b.toString();
    }

    public String layerToString(int y) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            b.append(Integer.toHexString(get(i)));
            if ((i & 15) == 15) b.append('\n');
        }
        return b.toString();
    }

    public boolean isDefinitelyHomogenous() { return data == null; }

    public boolean isDefinitelyFilledWith(int value) { return data == null && defaultValue == value; }

    public boolean isEmpty() { return data == null && defaultValue == 0; }
}
