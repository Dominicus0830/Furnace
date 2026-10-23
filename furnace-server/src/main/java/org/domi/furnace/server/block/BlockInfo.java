// 바닐라에서 뽑은 블록·상태 속성 표(블록 동작 틀이 BlockBehaviour.Properties·BlockStateBase 캐시 대신 읽는다).
//  blocks/blockprops_1.21.1.txt      블록별: 충돌 유무·폭발 저항·도구 필요·무작위 틱·마찰·피스톤 반응·동적 모양·어긋남·소리·전리품 표
//  blocks/stateflags_1.21.1.txt.gz   상태별: FULL/CENTER/RIGID 지지면, replaceable·legacySolid·blocksMotion·블록 엔티티·비교기 출력·
//                                    전도체·질식·시야 막음·후처리·발광 렌더, 빛 세기, 지도 색
//  blocks/blockshapes_1.21.1.txt.gz  상태별 모양(outline·collision·support·occlusion·interaction)
//  blocks/hardness_1.21.1.txt        블록별 destroyTime
package org.domi.furnace.server.block;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.domi.furnace.server.world.BlockHardness;

public final class BlockInfo {

    public static final int FULL = 0, CENTER = 1, RIGID = 2;

    private static volatile BlockInfo INSTANCE;

    public static BlockInfo get() {
        BlockInfo i = INSTANCE;
        if (i == null) {
            synchronized (BlockInfo.class) {
                if (INSTANCE == null) INSTANCE = new BlockInfo(Blocks.get());
                i = INSTANCE;
            }
        }
        return i;
    }

    // 상태 플래그 비트
    private static final int REPLACEABLE = 1, LEGACY_SOLID = 2, BLOCKS_MOTION = 4, HAS_BLOCK_ENTITY = 8, ANALOG = 16, CONDUCTOR = 32,
            SUFFOCATING = 64, VIEW_BLOCKING = 128, POST_PROCESS = 256, EMISSIVE = 512;

    private final Blocks blocks;
    // 블록별
    private final boolean[] hasCollision, requiresTool, randomlyTicking, canOcclude, ignitedByLava, replaceableProp, dynamicShape, offset;
    private final float[] explosionResistance, friction, speedFactor, jumpFactor, soundVolume, soundPitch, destroyTime;
    private final String[] pushReaction, breakSound, stepSound, placeSound, hitSound, fallSound, lootTable;
    // 상태별
    private final byte[][] sturdy;   // [FULL|CENTER|RIGID][state] 면 비트(DUNSWE: 비트 i = 방향 i)
    private final short[] flags;
    private final byte[] light;
    private final int[] mapColor;
    private final Shape[] outline, collision, support, occlusion, interaction;

    private BlockInfo(Blocks b) {
        this.blocks = b;
        int n = b.blockCount(), s = b.stateCount();
        hasCollision = new boolean[n]; requiresTool = new boolean[n]; randomlyTicking = new boolean[n]; canOcclude = new boolean[n];
        ignitedByLava = new boolean[n]; replaceableProp = new boolean[n]; dynamicShape = new boolean[n]; offset = new boolean[n];
        explosionResistance = new float[n]; friction = new float[n]; speedFactor = new float[n]; jumpFactor = new float[n];
        soundVolume = new float[n]; soundPitch = new float[n]; destroyTime = new float[n];
        pushReaction = new String[n]; breakSound = new String[n]; stepSound = new String[n]; placeSound = new String[n];
        hitSound = new String[n]; fallSound = new String[n]; lootTable = new String[n];
        sturdy = new byte[3][s];
        flags = new short[s];
        light = new byte[s];
        mapColor = new int[s];
        outline = new Shape[s]; collision = new Shape[s]; support = new Shape[s]; occlusion = new Shape[s]; interaction = new Shape[s];
        loadProps();
        loadFlags();
        loadShapes();
        BlockHardness h = BlockHardness.get();
        for (int i = 0; i < n; i++) destroyTime[i] = h.of(b.name(i));
    }

    private static BufferedReader open(String path, boolean gz) throws IOException {
        InputStream in = BlockInfo.class.getClassLoader().getResourceAsStream(path);
        if (in == null) throw new IllegalStateException(path + " 없음");
        return new BufferedReader(new InputStreamReader(gz ? new GZIPInputStream(in) : in, StandardCharsets.UTF_8));
    }

    private void loadProps() {
        try (BufferedReader r = open("blocks/blockprops_1.21.1.txt", false)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] p = line.split(" ");
                int i = blocks.block(p[0]);
                if (i < 0) continue;
                hasCollision[i] = p[1].equals("1");
                explosionResistance[i] = Float.parseFloat(p[2]);
                requiresTool[i] = p[3].equals("1");
                randomlyTicking[i] = p[4].equals("1");
                friction[i] = Float.parseFloat(p[5]);
                speedFactor[i] = Float.parseFloat(p[6]);
                jumpFactor[i] = Float.parseFloat(p[7]);
                canOcclude[i] = p[8].equals("1");
                ignitedByLava[i] = p[9].equals("1");
                pushReaction[i] = p[10];
                replaceableProp[i] = p[11].equals("1");
                dynamicShape[i] = p[12].equals("1");
                offset[i] = p[13].equals("1");
                soundVolume[i] = Float.parseFloat(p[14]);
                soundPitch[i] = Float.parseFloat(p[15]);
                breakSound[i] = p[16]; stepSound[i] = p[17]; placeSound[i] = p[18]; hitSound[i] = p[19]; fallSound[i] = p[20];
                lootTable[i] = p[21];
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void loadFlags() {
        try (BufferedReader r = open("blocks/stateflags_1.21.1.txt.gz", true)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] p = line.split(" ");
                int id = Integer.parseInt(p[0]);
                for (int t = 0; t < 3; t++) {
                    int m = 0;
                    for (int d = 0; d < 6; d++) if (p[1 + t].charAt(d) == '1') m |= 1 << d;
                    sturdy[t][id] = (byte) m;
                }
                String f = p[4];
                int bits = 0;
                for (int k = 0; k < f.length(); k++) if (f.charAt(k) == '1') bits |= 1 << k;
                flags[id] = (short) bits;
                light[id] = (byte) Integer.parseInt(p[5]);
                mapColor[id] = Integer.parseInt(p[6]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void loadShapes() {
        List<Shape> dict = new ArrayList<>();
        try (BufferedReader r = open("blocks/blockshapes_1.21.1.txt.gz", true)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                if (line.startsWith("S ")) {
                    int sp = line.indexOf(' ', 2);
                    dict.add(Shape.parse(line.substring(sp + 1)));
                    continue;
                }
                String[] p = line.split(" ");
                int id = Integer.parseInt(p[0]);
                outline[id] = dict.get(Integer.parseInt(p[1]));
                collision[id] = dict.get(Integer.parseInt(p[2]));
                support[id] = dict.get(Integer.parseInt(p[3]));
                occlusion[id] = dict.get(Integer.parseInt(p[4]));
                interaction[id] = dict.get(Integer.parseInt(p[5]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ 상태 질의(바닐라 BlockStateBase)

    public boolean isAir(int state) {
        String n = blocks.nameOfState(state);
        return n.equals("minecraft:air") || n.equals("minecraft:cave_air") || n.equals("minecraft:void_air");
    }

    /** BlockStateBase.canBeReplaced() — Properties.replaceable 플래그. */
    public boolean replaceable(int state) { return (flags[state] & REPLACEABLE) != 0; }

    /** isSolid()(legacySolid). */
    public boolean legacySolid(int state) { return (flags[state] & LEGACY_SOLID) != 0; }

    public boolean blocksMotion(int state) { return (flags[state] & BLOCKS_MOTION) != 0; }

    public boolean hasBlockEntity(int state) { return (flags[state] & HAS_BLOCK_ENTITY) != 0; }

    public boolean hasAnalogOutputSignal(int state) { return (flags[state] & ANALOG) != 0; }

    /** isRedstoneConductor(빈 세계 기준 — 위치 의존 블록은 동작 쪽에서 다시 판정). */
    public boolean redstoneConductor(int state) { return (flags[state] & CONDUCTOR) != 0; }

    public boolean suffocating(int state) { return (flags[state] & SUFFOCATING) != 0; }

    public boolean viewBlocking(int state) { return (flags[state] & VIEW_BLOCKING) != 0; }

    public int lightEmission(int state) { return light[state]; }

    public int mapColor(int state) { return mapColor[state]; }

    /** isFaceSturdy(dir, type) — 빈 세계 기준 캐시값(동적 모양 블록도 같은 표). */
    public boolean faceSturdy(int state, int dir, int type) { return (sturdy[type][state] >> dir & 1) != 0; }

    public Shape shape(int state) { return outline[state]; }

    public Shape collisionShape(int state) { return collision[state]; }

    public Shape supportShape(int state) { return support[state]; }

    public Shape occlusionShape(int state) { return occlusion[state]; }

    public Shape interactionShape(int state) { return interaction[state]; }

    // ------------------------------------------------------------------ 블록 질의(바닐라 Properties)

    public boolean hasCollision(int block) { return hasCollision[block]; }

    public float explosionResistance(int block) { return explosionResistance[block]; }

    public float destroyTime(int block) { return destroyTime[block]; }

    public boolean requiresCorrectToolForDrops(int block) { return requiresTool[block]; }

    public boolean randomlyTicking(int block) { return randomlyTicking[block]; }

    public float friction(int block) { return friction[block]; }

    public float speedFactor(int block) { return speedFactor[block]; }

    public float jumpFactor(int block) { return jumpFactor[block]; }

    public boolean canOcclude(int block) { return canOcclude[block]; }

    public boolean ignitedByLava(int block) { return ignitedByLava[block]; }

    /** PushReaction 이름: normal, destroy, block, ignore, push_only. */
    public String pushReaction(int block) { return pushReaction[block]; }

    public boolean dynamicShape(int block) { return dynamicShape[block]; }

    public boolean hasOffset(int block) { return offset[block]; }

    public float soundVolume(int block) { return soundVolume[block]; }

    public float soundPitch(int block) { return soundPitch[block]; }

    public String placeSound(int block) { return placeSound[block]; }

    public String breakSound(int block) { return breakSound[block]; }

    public String hitSound(int block) { return hitSound[block]; }

    public String stepSound(int block) { return stepSound[block]; }

    public String fallSound(int block) { return fallSound[block]; }

    /** 전리품 표 id(minecraft:empty 면 드롭 없음). */
    public String lootTable(int block) { return lootTable[block]; }
}
