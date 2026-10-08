// 바닐라 1.21.1 서버 코드로 export한 블록 상태별 속성(state id 인덱스). 월드젠 포팅이 바닐라의 BlockState 질의(isAir, isFaceSturdy,
// canBeReplaced 등)를 이름 추측 없이 그대로 쓰게 한다. 리소스: blocks/blockattrs_1.21.1.txt (tools/vanilla-data/ExportBlockAttrs.java).
package org.domi.furnace.server.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class BlockAttrs {

    /** 방향 인덱스: 마인크래프트 Direction.values() 순서. */
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    private static final int AIR = 1, OCCLUDE = 2, REPLACEABLE = 4, LIQUID = 8, BLOCKS_MOTION = 16,
            SOLID = 32, SOLID_RENDER = 64, COLLISION_EMPTY = 128;

    private static volatile BlockAttrs INSTANCE;

    private final short[] flags;
    private final byte[] light;
    private final byte[] sturdy;

    private BlockAttrs(short[] flags, byte[] light, byte[] sturdy) {
        this.flags = flags;
        this.light = light;
        this.sturdy = sturdy;
    }

    public static BlockAttrs get() {
        BlockAttrs a = INSTANCE;
        if (a == null) {
            synchronized (BlockAttrs.class) {
                a = INSTANCE;
                if (a == null) INSTANCE = a = load("blocks/blockattrs_1.21.1.txt");
            }
        }
        return a;
    }

    static BlockAttrs load(String resource) {
        try (InputStream in = BlockAttrs.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("블록 속성 리소스 없음: " + resource);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line = r.readLine();
            int n = Integer.parseInt(line.substring("# states ".length()).strip());
            short[] flags = new short[n];
            byte[] light = new byte[n];
            byte[] sturdy = new byte[n];
            int i = 0;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] p = line.split(" ");
                int f = 0;
                String bits = p[0];
                for (int b = 0; b < 8; b++) if (bits.charAt(b) == '1') f |= 1 << b;
                flags[i] = (short) f;
                light[i] = (byte) Integer.parseInt(p[1]);
                sturdy[i] = (byte) Integer.parseInt(p[2]);
                i++;
            }
            if (i != n) throw new IllegalStateException("블록 속성 줄 수 불일치: " + i + " != " + n);
            return new BlockAttrs(flags, light, sturdy);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean has(int stateId, int flag) {
        return stateId >= 0 && stateId < flags.length && (flags[stateId] & flag) != 0;
    }

    /** BlockState.isAir() — air, cave_air, void_air. */
    public boolean isAir(int stateId) { return has(stateId, AIR); }
    /** BlockState.canOcclude(). */
    public boolean canOcclude(int stateId) { return has(stateId, OCCLUDE); }
    /** BlockState.canBeReplaced(). */
    public boolean isReplaceable(int stateId) { return has(stateId, REPLACEABLE); }
    /** 액체 블록(물/용암). */
    public boolean isLiquid(int stateId) { return has(stateId, LIQUID); }
    /** BlockState.blocksMotion(). */
    public boolean blocksMotion(int stateId) { return has(stateId, BLOCKS_MOTION); }
    /** BlockState.isSolid()(legacy). */
    public boolean isSolid(int stateId) { return has(stateId, SOLID); }
    /** BlockState.isSolidRender(). */
    public boolean isSolidRender(int stateId) { return has(stateId, SOLID_RENDER); }
    /** 충돌 모양이 비어 있는가. */
    public boolean collisionEmpty(int stateId) { return has(stateId, COLLISION_EMPTY); }

    public int lightEmission(int stateId) {
        return stateId >= 0 && stateId < light.length ? light[stateId] : 0;
    }

    /** BlockState.isFaceSturdy(level, pos, direction) — SupportType.FULL. direction은 DOWN..EAST 인덱스. */
    public boolean isFaceSturdy(int stateId, int direction) {
        return stateId >= 0 && stateId < sturdy.length && (sturdy[stateId] & (1 << direction)) != 0;
    }

    public int stateCount() { return flags.length; }
}
