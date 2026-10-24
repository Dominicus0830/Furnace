// 한 차원의 빛 엔진 묶음(바닐라 LevelLightEngine): 블록빛 + (하늘이 있으면) 하늘빛. 청크 켜기·블록 변경·갱신 실행.
package org.domi.furnace.server.light;

public final class LevelLight {
    private final LightWorld world;
    private final BlockLightEngine block;
    private final SkyLightEngine sky;

    public LevelLight(LightWorld world, boolean hasSkyLight) {
        this.world = world;
        this.block = new BlockLightEngine(world);
        this.sky = hasSkyLight ? new SkyLightEngine(world) : null;
    }

    /** 이웃 쪽(이 청크에서 본 방향)과 서로 이어지는 가장자리 방향 순서: 북 남 서 동. */
    private static final int[] SIDES = {LightEngine.NORTH, LightEngine.SOUTH, LightEngine.WEST, LightEngine.EAST};
    private static final int[] DCX = {0, 0, -1, 1}, DCZ = {-1, 1, 0, 0};

    /** 이 청크의 빛을 처음 만든다(ChunkLight 는 world 에 이미 올라와 있어야 한다). 이웃의 빛이 이쪽으로 번지게 가장자리도 민다. */
    public void lightChunk(int cx, int cz) {
        ChunkLight cl = world.light(cx, cz);
        cl.lightEnabled = true;
        if (sky != null) {
            sky.computeSources(cx, cz);
            sky.propagateLightSources(cx, cz);
        }
        block.propagateLightSources(cx, cz);
        for (int i = 0; i < 4; i++) {
            int nx = cx + DCX[i], nz = cz + DCZ[i];
            ChunkLight n = world.light(nx, nz);
            if (n == null || !n.lightEnabled) continue;
            int toward = SIDES[i] ^ 1;   // 이웃에서 이 청크 쪽 방향
            block.pushEdge(nx, nz, toward);
            if (sky != null) sky.pushEdge(nx, nz, toward);
        }
        runUpdates();
    }

    /** 블록이 바뀐 뒤(LevelChunk.setBlockState): 하늘빛 원천을 고치고 두 엔진에 검사를 건다. 실제 계산은 runUpdates. */
    public void blockChanged(int x, int y, int z) {
        if (sky != null) sky.updateSource(x, y, z);
        long p = LightEngine.pack(x, y, z);
        block.checkBlock(p);
        if (sky != null) sky.checkBlock(p);
    }

    public boolean hasWork() { return block.hasWork() || sky != null && sky.hasWork(); }

    public void runUpdates() {
        if (sky != null) sky.runUpdates();
        block.runUpdates();
    }

    public int skyLight(int x, int y, int z) {
        ChunkLight cl = world.light(x >> 4, z >> 4);
        if (cl == null || y < ChunkLight.MIN_Y || y >= ChunkLight.MAX_Y) return 0;
        return cl.skyAt(x, y, z);
    }

    public int blockLight(int x, int y, int z) {
        ChunkLight cl = world.light(x >> 4, z >> 4);
        if (cl == null || y < ChunkLight.MIN_Y || y >= ChunkLight.MAX_Y) return 0;
        return cl.blockAt(x, y, z);
    }
}
