// 블록별 SoundType(바닐라 BlockBehaviour.Properties.sound → BlockState.getSoundType) — blocks/soundtypes_1.21.1.txt.
// 걸음·착지·부수기·놓기·맞기 소리의 크기·높이·이름. 상태가 달라도 소리 종류가 달라지는 블록은 바닐라 1.21.1 에 없다
// (장식 항아리는 BlockState.getSoundType 을 덮어 금 간 상태만 break 소리가 다르다 — 표의 [state-overrides] 구역).
package org.domi.furnace.server.block;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BlockSounds {

    /** SoundType: 크기·높이와 다섯 소리(이름은 minecraft: 포함). */
    public record SoundType(float volume, float pitch, String breakSound, String stepSound, String placeSound, String hitSound,
                            String fallSound) {

        /** 말의 걸음 소리가 나무 소리를 쓰는 종류(AbstractHorse.isWoodSoundType: WOOD·NETHER_WOOD·STEM·CHERRY_WOOD·BAMBOO_WOOD). */
        public boolean isWoodForHorse() {
            // 상수 동일성 비교라 HARD_CROP(걸음·부수기는 block.wood.*, 놓기만 item.crop.plant)은 빠진다 — 놓기 소리 이름까지 같은 종류만 인정
            return switch (stepSound) {
                case "minecraft:block.wood.step", "minecraft:block.nether_wood.step", "minecraft:block.stem.step",
                     "minecraft:block.cherry_wood.step", "minecraft:block.bamboo_wood.step" ->
                        placeSound.equals(stepSound.substring(0, stepSound.length() - 4) + "place");
                default -> false;
            };
        }
    }

    private static volatile BlockSounds instance;

    private final SoundType[] byBlock;
    /** 상태가 달라 소리 종류가 바뀌는 블록: 블록 번호 → 속성(값이 true 일 때) · 그때의 종류. */
    private final java.util.Map<Integer, StateOverride> overrides;

    private record StateOverride(Prop prop, SoundType type) {}

    private BlockSounds(SoundType[] byBlock, java.util.Map<Integer, StateOverride> overrides) {
        this.byBlock = byBlock;
        this.overrides = overrides;
    }

    public static BlockSounds get() {
        BlockSounds s = instance;
        if (s == null) {
            synchronized (BlockSounds.class) {
                if (instance == null) instance = load();
                s = instance;
            }
        }
        return s;
    }

    private static BlockSounds load() {
        Blocks blocks = Blocks.get();
        List<SoundType> types = new ArrayList<>();
        SoundType[] byBlock = new SoundType[blocks.blockCount()];
        java.util.Map<Integer, StateOverride> overrides = new java.util.HashMap<>();
        try (var in = BlockSounds.class.getClassLoader().getResourceAsStream("blocks/soundtypes_1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("blocks/soundtypes_1.21.1.txt 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean inBlocks = false, inOverrides = false;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.equals("[types]")) { inBlocks = false; inOverrides = false; continue; }
                if (line.equals("[blocks]")) { inBlocks = true; inOverrides = false; continue; }
                if (line.equals("[state-overrides]")) { inBlocks = false; inOverrides = true; continue; }
                String[] p = line.split(" ");
                if (inOverrides) {
                    int b = blocks.block("minecraft:" + p[0]);
                    String[] kv = p[1].split("=");
                    if (b >= 0 && kv[1].equals("true")) overrides.put(b, new StateOverride(B.prop(kv[0]), types.get(Integer.parseInt(p[2]))));
                } else if (!inBlocks) {
                    types.add(new SoundType(Float.parseFloat(p[1]), Float.parseFloat(p[2]), "minecraft:" + p[3], "minecraft:" + p[4],
                            "minecraft:" + p[5], "minecraft:" + p[6], "minecraft:" + p[7]));
                } else {
                    int b = blocks.block("minecraft:" + p[0]);
                    if (b >= 0) byBlock[b] = types.get(Integer.parseInt(p[1]));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BlockSounds(byBlock, overrides);
    }

    /** 블록 번호의 SoundType. 표에 없는 블록(없어야 한다)은 null. */
    public SoundType ofBlock(int block) { return block < 0 || block >= byBlock.length ? null : byBlock[block]; }

    /** BlockState.getSoundType(). */
    public SoundType of(int state) {
        int block = Blocks.get().blockOf(state);
        StateOverride o = overrides.isEmpty() ? null : overrides.get(block);
        return o != null && o.prop().is(state) ? o.type() : byBlock[block];
    }
}
