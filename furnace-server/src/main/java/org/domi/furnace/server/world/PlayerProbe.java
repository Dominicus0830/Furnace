// 플레이어 주변 블록 판정(소유 region 스레드 전용) — 물에 닿음·눈이 물속·매달림·발밑 블록.
// 바닐라 Entity.updateFluidHeightAndDoFluidPushing / updateFluidOnEyes / LivingEntity.onClimbable / getOnPosLegacy 를 따른다.
package org.domi.furnace.server.world;

import java.util.Set;
import org.domi.furnace.server.world.gen.feature.BlockTags;

public final class PlayerProbe {

    /** (x,y,z) 의 블록 state id. 청크가 없으면 -1. */
    @FunctionalInterface
    public interface Blocks {
        int stateAt(int x, int y, int z);
    }

    public static final double WIDTH = 0.6;
    public static final double HEIGHT_STANDING = 1.8, HEIGHT_CROUCHING = 1.5;
    public static final double EYE_STANDING = 1.62, EYE_CROUCHING = 1.27;

    private final BlockRegistry registry;
    private final Set<String> climbable, fences, walls, fenceGates;
    /** state id → 물 양(0 = 물 없음, 1..8 = FluidState.getAmount). 처음 볼 때 채운다(-1 = 아직 모름). */
    private final byte[] waterAmount;

    public PlayerProbe(BlockRegistry registry) {
        this.registry = registry;
        BlockTags tags = BlockTags.get();
        this.climbable = Set.copyOf(tags.blocksOf("minecraft:climbable"));
        this.fences = Set.copyOf(tags.blocksOf("minecraft:fences"));
        this.walls = Set.copyOf(tags.blocksOf("minecraft:walls"));
        this.fenceGates = Set.copyOf(tags.blocksOf("minecraft:fence_gates"));
        this.waterAmount = new byte[Math.max(1, registry.totalStateCount())];
        java.util.Arrays.fill(waterAmount, (byte) -1);
    }

    public String nameOf(int stateId) { return registry.nameOf(stateId); }

    /** 바닐라 LiquidBlock/워터로그 블록의 물 FluidState 양. */
    int waterAmount(int stateId) {
        if (stateId < 0) return 0;
        if (stateId < waterAmount.length && waterAmount[stateId] >= 0) return waterAmount[stateId];
        int amount = computeWaterAmount(stateId);
        if (stateId < waterAmount.length) waterAmount[stateId] = (byte) amount;
        return amount;
    }

    private int computeWaterAmount(int stateId) {
        String name = registry.nameOf(stateId);
        switch (name) {
            case "minecraft:bubble_column", "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass" -> {
                return 8;
            }
            default -> { }
        }
        String key = registry.canonicalKeyOf(stateId);
        if (key == null) return 0;
        if (name.equals("minecraft:water")) {
            int level = intProp(key, "level");
            return level == 0 || level >= 8 ? 8 : 8 - level;
        }
        return key.contains("waterlogged=true") ? 8 : 0;
    }

    private static int intProp(String key, String prop) {
        int i = key.indexOf(prop + "=");
        if (i < 0) return 0;
        int s = i + prop.length() + 1, e = s;
        while (e < key.length() && Character.isDigit(key.charAt(e))) e++;
        return e > s ? Integer.parseInt(key.substring(s, e)) : 0;
    }

    /** FlowingFluid.getHeight: 위가 같은 액체면 1, 아니면 양/9. 물이 없으면 0. */
    double waterHeight(Blocks b, int x, int y, int z) {
        int amount = waterAmount(b.stateAt(x, y, z));
        if (amount == 0) return 0;
        if (waterAmount(b.stateAt(x, y + 1, z)) > 0) return 1.0;
        return amount / 9.0f;
    }

    /** Entity.updateFluidHeightAndDoFluidPushing(WATER) 의 "닿았는가": 몸 상자(0.001 줄임)와 물 높이가 겹치는가. */
    public boolean inWater(Blocks b, double x, double y, double z, double height) {
        double minX = x - WIDTH / 2 + 0.001, maxX = x + WIDTH / 2 - 0.001;
        double minY = y + 0.001, maxY = y + height - 0.001;
        double minZ = z - WIDTH / 2 + 0.001, maxZ = z + WIDTH / 2 - 0.001;
        int x0 = floor(minX), x1 = (int) Math.ceil(maxX), y0 = floor(minY), y1 = (int) Math.ceil(maxY), z0 = floor(minZ), z1 = (int) Math.ceil(maxZ);
        for (int bx = x0; bx < x1; bx++)
            for (int by = y0; by < y1; by++)
                for (int bz = z0; bz < z1; bz++) {
                    double h = waterHeight(b, bx, by, bz);
                    if (h > 0 && by + h >= minY) return true;
                }
        return false;
    }

    /** Entity.updateFluidOnEyes: 눈 높이 - 0.11111111 칸의 물 높이가 그 위인가. */
    public boolean eyeInWater(Blocks b, double x, double y, double z, double eyeHeight) {
        double eyeY = y + eyeHeight - 0.11111111f;
        int bx = floor(x), by = floor(eyeY), bz = floor(z);
        double h = waterHeight(b, bx, by, bz);
        return h > 0 && by + h > eyeY;
    }

    /** 눈 위치 블록이 거품 기둥인가(바닐라: 거품 기둥 안에서는 숨이 줄지 않는다). */
    public boolean eyeInBubbleColumn(Blocks b, double x, double y, double z, double eyeHeight) {
        int s = b.stateAt(floor(x), floor(y + eyeHeight), floor(z));
        return s >= 0 && registry.nameOf(s).equals("minecraft:bubble_column");
    }

    /** LivingEntity.onClimbable + FallLocation.blockToFallLocation: 매달린 블록의 낙하 위치 이름, 아니면 null. */
    public String climbable(Blocks b, double x, double y, double z) {
        int s = b.stateAt(floor(x), floor(y), floor(z));
        if (s < 0) return null;
        String n = registry.nameOf(s);
        if (!climbable.contains(n)) return null;
        return switch (n) {
            case "minecraft:ladder" -> "ladder";
            case "minecraft:vine" -> "vines";
            case "minecraft:weeping_vines", "minecraft:weeping_vines_plant" -> "weeping_vines";
            case "minecraft:twisting_vines", "minecraft:twisting_vines_plant" -> "twisting_vines";
            case "minecraft:scaffolding" -> "scaffolding";
            default -> "other_climbable";
        };
    }

    /** 몸이 거미줄·가루눈·달콤한 열매 덤불 안에 있는가(makeStuckInBlock → 낙하 거리 초기화). */
    public boolean stuckInBlock(Blocks b, double x, double y, double z, double height) {
        int x0 = floor(x - WIDTH / 2 + 1e-7), x1 = floor(x + WIDTH / 2 - 1e-7);
        int y0 = floor(y + 1e-7), y1 = floor(y + height - 1e-7);
        int z0 = floor(z - WIDTH / 2 + 1e-7), z1 = floor(z + WIDTH / 2 - 1e-7);
        for (int bx = x0; bx <= x1; bx++)
            for (int by = y0; by <= y1; by++)
                for (int bz = z0; bz <= z1; bz++) {
                    int s = b.stateAt(bx, by, bz);
                    if (s < 0) continue;
                    switch (registry.nameOf(s)) {
                        case "minecraft:cobweb", "minecraft:powder_snow", "minecraft:sweet_berry_bush" -> { return true; }
                        default -> { }
                    }
                }
        return false;
    }

    /**
     * Entity.getOnPosLegacy(= getOnPos(0.2)) 의 블록 이름. 지지 블록 탐색은 발 아래 판(0.6×0.6)에서 충돌 모양이 있는 블록 중
     * 플레이어 중심에 가장 가까운 것(checkSupportingBlock)으로 하고, 울타리·담·울타리 문이면 그 블록, 아니면 그 x·z 의 floor(y-0.2).
     * 청크가 없으면 null.
     */
    public String onPosLegacy(Blocks b, double x, double y, double z, boolean onGround) {
        int[] support = onGround ? supportingBlock(b, x, y, z) : null;
        int bx, by, bz;
        if (support != null) {
            int s = b.stateAt(support[0], support[1], support[2]);
            String n = s < 0 ? null : registry.nameOf(s);
            if (n != null && (fences.contains(n) || walls.contains(n) || fenceGates.contains(n))) return n;
            bx = support[0];
            by = floor(y - 0.2);
            bz = support[2];
        } else {
            bx = floor(x);
            by = floor(y - 0.2);
            bz = floor(z);
        }
        int s = b.stateAt(bx, by, bz);
        return s < 0 ? null : registry.nameOf(s);
    }

    private int[] supportingBlock(Blocks b, double x, double y, double z) {
        int by = floor(y - 1.0e-6);
        int x0 = floor(x - WIDTH / 2), x1 = floor(x + WIDTH / 2 - 1e-7);
        int z0 = floor(z - WIDTH / 2), z1 = floor(z + WIDTH / 2 - 1e-7);
        BlockAttrs attrs = BlockAttrs.get();
        int[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (int bx = x0; bx <= x1; bx++)
            for (int bz = z0; bz <= z1; bz++) {
                int s = b.stateAt(bx, by, bz);
                if (s < 0 || attrs.collisionEmpty(s)) continue;
                double dx = bx + 0.5 - x, dy = by + 0.5 - y, dz = bz + 0.5 - z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[]{bx, by, bz};
                }
            }
        return best;
    }

    /** 이전 위치에서 새 위치까지 수직 경로(새 x·z 기준)에 물이 있는가 — Entity.move 의 낙하 중 물 레이캐스트 근사. */
    public boolean waterOnPath(Blocks b, double x, double fromY, double toY, double z) {
        int bx = floor(x), bz = floor(z);
        int lo = floor(Math.min(fromY, toY)), hi = floor(Math.max(fromY, toY));
        for (int by = lo; by <= hi; by++) if (waterAmount(b.stateAt(bx, by, bz)) > 0) return true;
        return false;
    }

    private static int floor(double v) { return (int) Math.floor(v); }
}
