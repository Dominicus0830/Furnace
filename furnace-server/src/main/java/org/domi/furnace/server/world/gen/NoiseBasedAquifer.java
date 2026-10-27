// 바닐라 NoiseBasedAquifer 비트정확 포팅 — 지하 수체/용암 배치를 노이즈로 결정
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import java.util.Arrays;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.noise.PositionalRandom;

public final class NoiseBasedAquifer implements Aquifer {

    private static final int WAY_BELOW_MIN_Y = -2048;
    // similarity(square(10), square(12)) = similarity(100, 144) = 1.0 - 44/25 = -0.76
    private static final double FLOWING_UPDATE_SIMULARITY = similarity(100, 144);

    private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = {
        {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
    };

    private final NoiseChunk noiseChunk;
    private final DensityFunction barrierNoise;
    private final DensityFunction fluidLevelFloodednessNoise;
    private final DensityFunction fluidLevelSpreadNoise;
    private final DensityFunction lavaNoise;
    private final DensityFunction erosion;
    private final DensityFunction depth;
    private final PositionalRandom positionalRandomFactory;

    private final FluidStatus[] aquiferCache;
    private final long[] aquiferLocationCache;
    private final int[] aquiferLocationXCache;
    private final int[] aquiferLocationYCache;
    private final int[] aquiferLocationZCache;
    /** 후보 12칸 묶음별 최대 수위(computeSubstance 빠른 길). MIN_VALUE = 아직 안 셈. */
    private final int[] maxLevelCache;

    private final int seaLevel;
    private final int lavaFloorY;
    private final int stoneId;
    private final int waterId;
    private final int lavaId;

    private final int minGridX, minGridY, minGridZ;
    private final int gridSizeX, gridSizeZ;
    private final int minSurfaceQuartX, minSurfaceQuartZ;
    private final int surfaceQuartSizeX, surfaceQuartSizeZ;
    private final int[] preliminarySurfaceLevelCache;
    private final FluidStatus globalWaterStatus;
    private final FluidStatus globalLavaStatus;
    private final MutableContext mutableContext = new MutableContext();
    private final BarrierCache barrierCache = new BarrierCache();

    private boolean shouldScheduleFluidUpdate;

    public NoiseBasedAquifer(NoiseChunk noiseChunk,
                             int chunkX, int chunkZ,
                             NoiseRouter router,
                             NoiseSettings settings,
                             PositionalRandom positionalRandom,
                             int stoneId, int waterId, int lavaId) {
        this.noiseChunk = noiseChunk;
        this.barrierNoise = noiseChunk.wrappedForAquifer(router.barrierNoise());
        this.fluidLevelFloodednessNoise = noiseChunk.wrappedForAquifer(router.fluidLevelFloodednessNoise());
        this.fluidLevelSpreadNoise = noiseChunk.wrappedForAquifer(router.fluidLevelSpreadNoise());
        this.lavaNoise = noiseChunk.wrappedForAquifer(router.lavaNoise());
        this.erosion = noiseChunk.wrappedForAquifer(router.erosion());
        this.depth = noiseChunk.wrappedForAquifer(router.depth());
        this.positionalRandomFactory = positionalRandom;

        this.seaLevel = settings.seaLevel();
        this.lavaFloorY = settings.lavaFloorY();
        this.stoneId = stoneId;
        this.waterId = waterId;
        this.lavaId = lavaId;
        this.globalWaterStatus = new FluidStatus(this.seaLevel, this.waterId);
        this.globalLavaStatus = new FluidStatus(this.lavaFloorY, this.lavaId);

        int minBlockX = chunkX * 16;
        int maxBlockX = chunkX * 16 + 15;
        int minBlockZ = chunkZ * 16;
        int maxBlockZ = chunkZ * 16 + 15;
        int minY = settings.minY();
        int height = settings.height();

        this.minGridX = gridX(minBlockX) - 1;
        int maxGridX = gridX(maxBlockX) + 1;
        this.gridSizeX = maxGridX - this.minGridX + 1;

        this.minGridY = gridY(minY) - 1;
        int maxGridY = gridY(minY + height) + 1;
        int gridSizeY = maxGridY - this.minGridY + 1;

        this.minGridZ = gridZ(minBlockZ) - 1;
        int maxGridZ = gridZ(maxBlockZ) + 1;
        this.gridSizeZ = maxGridZ - this.minGridZ + 1;

        int minAquiferX = this.minGridX * 16;
        int maxAquiferX = maxGridX * 16 + 9;
        int minAquiferZ = this.minGridZ * 16;
        int maxAquiferZ = maxGridZ * 16 + 9;
        this.minSurfaceQuartX = Math.floorDiv(minAquiferX - 48, 4);
        int maxSurfaceQuartX = Math.floorDiv(maxAquiferX + 16, 4);
        this.surfaceQuartSizeX = maxSurfaceQuartX - this.minSurfaceQuartX + 1;
        this.minSurfaceQuartZ = Math.floorDiv(minAquiferZ - 48, 4);
        int maxSurfaceQuartZ = Math.floorDiv(maxAquiferZ + 16, 4);
        this.surfaceQuartSizeZ = maxSurfaceQuartZ - this.minSurfaceQuartZ + 1;
        this.preliminarySurfaceLevelCache = new int[this.surfaceQuartSizeX * this.surfaceQuartSizeZ];
        Arrays.fill(this.preliminarySurfaceLevelCache, Integer.MIN_VALUE);

        int total = this.gridSizeX * gridSizeY * this.gridSizeZ;
        this.aquiferCache = new FluidStatus[total];
        this.aquiferLocationCache = new long[total];
        this.aquiferLocationXCache = new int[total];
        this.aquiferLocationYCache = new int[total];
        this.aquiferLocationZCache = new int[total];
        Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
        this.maxLevelCache = new int[total];
        Arrays.fill(this.maxLevelCache, Integer.MIN_VALUE);
    }

    public boolean shouldScheduleFluidUpdate() {
        return this.shouldScheduleFluidUpdate;
    }

    @Override
    public int computeSubstance(DensityFunction.FunctionContext context, double substance) {
        int i = context.blockX();
        int i1 = context.blockY();
        int i2 = context.blockZ();
        if (substance > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return stoneId;
        }
        if (globalFluidPicker(i, i1, i2).at(i1) == lavaId) {
            this.shouldScheduleFluidUpdate = false;
            return lavaId;
        }

        int i3 = Math.floorDiv(i - 5, 16);
        int i4 = Math.floorDiv(i1 + 1, 12);
        int i5 = Math.floorDiv(i2 - 5, 16);
        // 빠른 길(결과 동일): 후보 12칸 상태의 최대 수위 M 보다 5칸 이상 위면 바닐라 식의 결과는 언제나 공기다 —
        // 가장 가까운 상태의 at(y) 가 공기이고, 어떤 두 상태 쌍의 압력도 d11 = (M - y - 0.5)/2.5 < -2 라 장벽 노이즈 없이 음수
        // (2·d11)라서 장벽(NO_CARVE)이 생기지 않는다. 지표 위 공기·물 없는 동굴 대부분이 여기서 끝난다.
        if (AIR_SHORTCUT && i1 >= maxFluidLevelAround(i3, i4, i5) + 5) {
            if (verifyShortcut) verifyShortcutHit(context, i, i1, i2, i3, i4, i5, substance);
            this.shouldScheduleFluidUpdate = false;
            return 0;
        }
        return computeSubstanceSlow(context, i, i1, i2, i3, i4, i5, substance);
    }

    // 테스트 전용 fuzz 게이트(감사 W-10): true 면 빠른 길이 잡은 블록마다 바닐라 식 결과를 다시 계산해 공기가 아닌 수를 센다.
    // (shouldScheduleFluidUpdate 는 공기 블록에선 바닐라가 읽지 않으므로 대조하지 않는다.)
    static boolean verifyShortcut;
    static final java.util.concurrent.atomic.AtomicLong shortcutHits = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong shortcutMismatches = new java.util.concurrent.atomic.AtomicLong();

    private void verifyShortcutHit(DensityFunction.FunctionContext context, int i, int i1, int i2, int i3, int i4, int i5, double substance) {
        shortcutHits.incrementAndGet();
        if (computeSubstanceSlow(context, i, i1, i2, i3, i4, i5, substance) != 0) shortcutMismatches.incrementAndGet();
    }

    private int computeSubstanceSlow(DensityFunction.FunctionContext context, int i, int i1, int i2, int i3, int i4, int i5, double substance) {
        int i6 = Integer.MAX_VALUE, i7 = Integer.MAX_VALUE, i8 = Integer.MAX_VALUE, i9 = Integer.MAX_VALUE;
        int idx0 = -1, idx1 = -1, idx2 = -1, idx3 = -1;
        int x0 = 0, y0 = 0, z0 = 0;
        int x1 = 0, y1 = 0, z1 = 0;
        int x2 = 0, y2 = 0, z2 = 0;
        int x3 = 0, y3 = 0, z3 = 0;

        for (int i10 = 0; i10 <= 1; i10++) {
            for (int i11 = -1; i11 <= 1; i11++) {
                for (int i12 = 0; i12 <= 1; i12++) {
                    int i13 = i3 + i10;
                    int i14 = i4 + i11;
                    int i15 = i5 + i12;
                    int index = getIndex(i13, i14, i15);
                    long l4 = this.aquiferLocationCache[index];
                    long l5;
                    int locationX;
                    int locationY;
                    int locationZ;
                    if (l4 != Long.MAX_VALUE) {
                        l5 = l4;
                        locationX = this.aquiferLocationXCache[index];
                        locationY = this.aquiferLocationYCache[index];
                        locationZ = this.aquiferLocationZCache[index];
                    } else {
                        NoiseRandom random = this.positionalRandomFactory.at(i13, i14, i15);
                        locationX = i13 * 16 + random.nextInt(10);
                        locationY = i14 * 12 + random.nextInt(9);
                        locationZ = i15 * 16 + random.nextInt(10);
                        l5 = packPos(
                            locationX,
                            locationY,
                            locationZ
                        );
                        this.aquiferLocationCache[index] = l5;
                        this.aquiferLocationXCache[index] = locationX;
                        this.aquiferLocationYCache[index] = locationY;
                        this.aquiferLocationZCache[index] = locationZ;
                    }
                    int i16 = locationX - i;
                    int i17 = locationY - i1;
                    int i18 = locationZ - i2;
                    int i19 = i16 * i16 + i17 * i17 + i18 * i18;
                    if (i6 >= i19) {
                        x3 = x2; y3 = y2; z3 = z2; idx3 = idx2;
                        x2 = x1; y2 = y1; z2 = z1; idx2 = idx1;
                        x1 = x0; y1 = y0; z1 = z0; idx1 = idx0;
                        x0 = locationX; y0 = locationY; z0 = locationZ; idx0 = index;
                        i9 = i8; i8 = i7; i7 = i6; i6 = i19;
                    } else if (i7 >= i19) {
                        x3 = x2; y3 = y2; z3 = z2; idx3 = idx2;
                        x2 = x1; y2 = y1; z2 = z1; idx2 = idx1;
                        x1 = locationX; y1 = locationY; z1 = locationZ; idx1 = index;
                        i9 = i8; i8 = i7; i7 = i19;
                    } else if (i8 >= i19) {
                        x3 = x2; y3 = y2; z3 = z2; idx3 = idx2;
                        x2 = locationX; y2 = locationY; z2 = locationZ; idx2 = index;
                        i9 = i8; i8 = i19;
                    } else if (i9 >= i19) {
                        x3 = locationX; y3 = locationY; z3 = locationZ; idx3 = index; i9 = i19;
                    }
                }
            }
        }

        FluidStatus aquiferStatus = getAquiferStatus(idx0, x0, y0, z0);
        double d = similarity(i6, i7);
        int blockState = aquiferStatus.at(i1);

        if (d <= 0.0) {
            if (d >= FLOWING_UPDATE_SIMULARITY) {
                FluidStatus aquiferStatus1 = getAquiferStatus(idx1, x1, y1, z1);
                this.shouldScheduleFluidUpdate = !aquiferStatus.equals(aquiferStatus1);
            } else {
                this.shouldScheduleFluidUpdate = false;
            }
            return blockState;
        } else if (blockState == waterId && i1 - 1 < lavaFloorY) {
            this.shouldScheduleFluidUpdate = true;
            return blockState;
        } else {
            barrierCache.reset();
            FluidStatus aquiferStatus2 = getAquiferStatus(idx1, x1, y1, z1);
            double d1 = d * calculatePressure(context, barrierCache, aquiferStatus, aquiferStatus2);
            if (substance + d1 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return Aquifer.NO_CARVE;
            }
            FluidStatus aquiferStatus3 = getAquiferStatus(idx2, x2, y2, z2);
            double d2 = similarity(i6, i8);
            if (d2 > 0.0) {
                double d3 = d * d2 * calculatePressure(context, barrierCache, aquiferStatus, aquiferStatus3);
                if (substance + d3 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return Aquifer.NO_CARVE;
                }
            }
            double d3 = similarity(i7, i8);
            if (d3 > 0.0) {
                double d4 = d * d3 * calculatePressure(context, barrierCache, aquiferStatus2, aquiferStatus3);
                if (substance + d4 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return Aquifer.NO_CARVE;
                }
            }
            boolean flag = !aquiferStatus.equals(aquiferStatus2);
            boolean flag1 = d3 >= FLOWING_UPDATE_SIMULARITY && !aquiferStatus2.equals(aquiferStatus3);
            boolean flag2 = d2 >= FLOWING_UPDATE_SIMULARITY && !aquiferStatus.equals(aquiferStatus3);
            if (!flag && !flag1 && !flag2) {
                this.shouldScheduleFluidUpdate = d2 >= FLOWING_UPDATE_SIMULARITY
                    && similarity(i6, i9) >= FLOWING_UPDATE_SIMULARITY
                    && !aquiferStatus.equals(getAquiferStatus(idx3, x3, y3, z3));
            } else {
                this.shouldScheduleFluidUpdate = true;
            }
            return blockState;
        }
    }

    /** (i3,i4,i5) 묶음의 후보 12칸(위 탐색 루프와 같은 칸) 상태 중 최대 수위 — 묶음마다 한 번 계산해 둔다. */
    private int maxFluidLevelAround(int gx0, int gy0, int gz0) {
        int key = getIndex(gx0, gy0, gz0);
        int cached = this.maxLevelCache[key];
        if (cached != Integer.MIN_VALUE) return cached;
        int max = Integer.MIN_VALUE + 16;
        for (int i10 = 0; i10 <= 1; i10++) {
            for (int i11 = -1; i11 <= 1; i11++) {
                for (int i12 = 0; i12 <= 1; i12++) {
                    int gx = gx0 + i10, gy = gy0 + i11, gz = gz0 + i12;
                    int index = getIndex(gx, gy, gz);
                    if (this.aquiferLocationCache[index] == Long.MAX_VALUE) {
                        NoiseRandom random = this.positionalRandomFactory.at(gx, gy, gz);
                        int lx = gx * 16 + random.nextInt(10);
                        int ly = gy * 12 + random.nextInt(9);
                        int lz = gz * 16 + random.nextInt(10);
                        this.aquiferLocationCache[index] = packPos(lx, ly, lz);
                        this.aquiferLocationXCache[index] = lx;
                        this.aquiferLocationYCache[index] = ly;
                        this.aquiferLocationZCache[index] = lz;
                    }
                    FluidStatus st = getAquiferStatus(index, this.aquiferLocationXCache[index],
                        this.aquiferLocationYCache[index], this.aquiferLocationZCache[index]);
                    max = Math.max(max, st.fluidLevel());
                }
            }
        }
        this.maxLevelCache[key] = max;
        return max;
    }

    private static double similarity(int firstDistance, int secondDistance) {
        return 1.0 - Math.abs(secondDistance - firstDistance) / 25.0;
    }

    private double calculatePressure(DensityFunction.FunctionContext context, BarrierCache substance,
                                     FluidStatus firstFluid, FluidStatus secondFluid) {
        int i = context.blockY();
        int blockState = firstFluid.at(i);
        int blockState1 = secondFluid.at(i);
        if ((blockState == lavaId && blockState1 == waterId) || (blockState == waterId && blockState1 == lavaId)) {
            return 2.0;
        } else {
            int abs = Math.abs(firstFluid.fluidLevel() - secondFluid.fluidLevel());
            if (abs == 0) {
                return 0.0;
            } else {
                double d = 0.5 * (firstFluid.fluidLevel() + secondFluid.fluidLevel());
                double d1 = i + 0.5 - d;
                double d2 = abs / 2.0;
                double d9 = d2 - Math.abs(d1);
                double d11;
                if (d1 > 0.0) {
                    double d10 = 0.0 + d9;
                    d11 = (d10 > 0.0) ? d10 / 1.5 : d10 / 2.5;
                } else {
                    double d10 = 3.0 + d9;
                    d11 = (d10 > 0.0) ? d10 / 3.0 : d10 / 10.0;
                }
                double d12;
                if (!(d11 < -2.0) && !(d11 > 2.0)) {
                    double value = substance.value;
                    if (Double.isNaN(value)) {
                        double d13 = this.barrierNoise.compute(context);
                        substance.value = d13;
                        d12 = d13;
                    } else {
                        d12 = value;
                    }
                } else {
                    d12 = 0.0;
                }
                return 2.0 * (d12 + d11);
            }
        }
    }

    private int gridX(int x) { return Math.floorDiv(x, 16); }
    private int gridY(int y) { return Math.floorDiv(y, 12); }
    private int gridZ(int z) { return Math.floorDiv(z, 16); }

    private int getIndex(int gridX, int gridY, int gridZ) {
        int i = gridX - this.minGridX;
        int i1 = gridY - this.minGridY;
        int i2 = gridZ - this.minGridZ;
        return (i1 * this.gridSizeZ + i2) * this.gridSizeX + i;
    }

    private FluidStatus getAquiferStatus(int index, int x, int y, int z) {
        FluidStatus fluidStatus = this.aquiferCache[index];
        if (fluidStatus != null) return fluidStatus;
        FluidStatus computed = computeFluid(x, y, z);
        this.aquiferCache[index] = computed;
        return computed;
    }

    private FluidStatus getAquiferStatus(long packedPos) {
        int x = unpackX(packedPos);
        int y = unpackY(packedPos);
        int z = unpackZ(packedPos);
        int i = gridX(x);
        int i1 = gridY(y);
        int i2 = gridZ(z);
        int index = getIndex(i, i1, i2);
        FluidStatus fluidStatus = this.aquiferCache[index];
        if (fluidStatus != null) {
            return fluidStatus;
        } else {
            FluidStatus fluidStatus1 = computeFluid(x, y, z);
            this.aquiferCache[index] = fluidStatus1;
            return fluidStatus1;
        }
    }

    private static final boolean AIR_SHORTCUT = !Boolean.getBoolean("furnace.noAqShortcut");
    private static final boolean AQ_CENTERS = System.getProperty("furnace.aqcenters") != null;

    private FluidStatus computeFluid(int x, int y, int z) {
        FluidStatus r = computeFluid0(x, y, z);
        if (AQ_CENTERS) System.out.println("FAQC " + x + " " + y + " " + z + " level=" + r.fluidLevel() + " fluid=" + r.fluidBlockId());
        return r;
    }

    private FluidStatus computeFluid0(int x, int y, int z) {
        FluidStatus fluidStatus = globalFluidPicker(x, y, z);
        int i = Integer.MAX_VALUE;
        int i1 = y + 12;
        int i2 = y - 12;
        boolean flag = false;

        for (int[] ints : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
            int i3 = x + ints[0] * 16;
            int i4 = z + ints[1] * 16;
            int i5 = getPreliminarySurfaceLevel(Math.floorDiv(i3, 4), Math.floorDiv(i4, 4));
            int i6 = i5 + 8;
            boolean flag1 = ints[0] == 0 && ints[1] == 0;
            if (flag1 && i2 > i6) {
                return fluidStatus;
            }
            boolean flag2 = i1 > i6;
            if (flag2 || flag1) {
                FluidStatus fluidStatus1 = globalFluidPicker(i3, i6, i4);
                if (fluidStatus1.at(i6) != 0) {
                    if (flag1) flag = true;
                    if (flag2) return fluidStatus1;
                }
            }
            i = Math.min(i, i5);
        }

        int i7 = computeSurfaceLevel(x, y, z, fluidStatus, i, flag);
        return new FluidStatus(i7, computeFluidType(x, y, z, fluidStatus, i7));
    }

    private int computeSurfaceLevel(int x, int y, int z, FluidStatus fluidStatus, int maxSurfaceLevel, boolean fluidPresent) {
        MutableContext ctx = this.mutableContext.set(x, y, z);
        double d, d1;
        if (isDeepDarkRegion(ctx)) {
            d = -1.0;
            d1 = -1.0;
        } else {
            int i = maxSurfaceLevel + 8 - y;
            double d2 = fluidPresent ? clampedMap(i, 0.0, 64.0, 1.0, 0.0) : 0.0;
            double d3 = MathHelper.clamp(this.fluidLevelFloodednessNoise.compute(ctx), -1.0, 1.0);
            double d4 = map(d2, 1.0, 0.0, -0.3, 0.8);
            double d5 = map(d2, 1.0, 0.0, -0.8, 0.4);
            d = d3 - d5;
            d1 = d3 - d4;
        }
        int i;
        if (d1 > 0.0) {
            i = fluidStatus.fluidLevel();
        } else if (d > 0.0) {
            i = computeRandomizedFluidSurfaceLevel(x, y, z, maxSurfaceLevel);
        } else {
            i = WAY_BELOW_MIN_Y;
        }
        return i;
    }

    private boolean isDeepDarkRegion(DensityFunction.FunctionContext ctx) {
        return this.erosion.compute(ctx) < -0.225 && this.depth.compute(ctx) > 0.9;
    }

    private int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int maxSurfaceLevel) {
        int i2 = Math.floorDiv(x, 16);
        int i3 = Math.floorDiv(y, 40);
        int i4 = Math.floorDiv(z, 16);
        int i5 = i3 * 40 + 20;
        double d = this.fluidLevelSpreadNoise.compute(this.mutableContext.set(i2, i3, i4)) * 10.0;
        int i7 = quantize(d, 3);
        int i8 = i5 + i7;
        return Math.min(maxSurfaceLevel, i8);
    }

    private int computeFluidType(int x, int y, int z, FluidStatus fluidStatus, int surfaceLevel) {
        int blockState = fluidStatus.fluidBlockId();
        if (surfaceLevel <= -10 && surfaceLevel != WAY_BELOW_MIN_Y && fluidStatus.fluidBlockId() != lavaId) {
            int i2 = Math.floorDiv(x, 64);
            int i3 = Math.floorDiv(y, 40);
            int i4 = Math.floorDiv(z, 64);
            double d = this.lavaNoise.compute(this.mutableContext.set(i2, i3, i4));
            if (Math.abs(d) > 0.3) {
                blockState = lavaId;
            }
        }
        return blockState;
    }

    private FluidStatus globalFluidPicker(int x, int y, int z) {
        return y < this.lavaFloorY ? this.globalLavaStatus : this.globalWaterStatus;
    }

    private int getPreliminarySurfaceLevel(int quartX, int quartZ) {
        int x = quartX - this.minSurfaceQuartX;
        int z = quartZ - this.minSurfaceQuartZ;
        int index = z * this.surfaceQuartSizeX + x;
        int cached = this.preliminarySurfaceLevelCache[index];
        if (cached != Integer.MIN_VALUE) return cached;
        int computed = this.noiseChunk.getPreliminarySurfaceLevel(quartX, quartZ);
        this.preliminarySurfaceLevelCache[index] = computed;
        return computed;
    }

    // ── Mth helpers ──────────────────────────────────────────────────────

    private static int quantize(double d, int step) {
        return (int) Math.floor(d / step) * step;
    }

    private static double clampedMap(double v, double fromMin, double fromMax, double toMin, double toMax) {
        double t = (v - fromMin) / (fromMax - fromMin);
        t = Math.max(0.0, Math.min(1.0, t));
        return toMin + t * (toMax - toMin);
    }

    private static double map(double v, double fromMin, double fromMax, double toMin, double toMax) {
        return toMin + (v - fromMin) * (toMax - toMin) / (fromMax - fromMin);
    }

    // ── BlockPos packing (vanilla BlockPos.asLong/getX/getY/getZ) ────────

    private static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    private static int unpackX(long l) { return (int) (l << 0 >> 38); }
    private static int unpackY(long l) { return (int) (l << 52 >> 52); }
    private static int unpackZ(long l) { return (int) (l << 26 >> 38); }

    // ── FluidStatus (vanilla Aquifer.FluidStatus) ───────────────────────

    record FluidStatus(int fluidLevel, int fluidBlockId) {
        int at(int y) { return y < this.fluidLevel ? this.fluidBlockId : 0; }
    }

    private static final class MutableContext implements DensityFunction.FunctionContext {
        private int blockX;
        private int blockY;
        private int blockZ;

        MutableContext set(int x, int y, int z) {
            this.blockX = x;
            this.blockY = y;
            this.blockZ = z;
            return this;
        }

        @Override public int blockX() { return blockX; }
        @Override public int blockY() { return blockY; }
        @Override public int blockZ() { return blockZ; }
    }

    private static final class BarrierCache {
        private double value = Double.NaN;
        void reset() { this.value = Double.NaN; }
    }
}
