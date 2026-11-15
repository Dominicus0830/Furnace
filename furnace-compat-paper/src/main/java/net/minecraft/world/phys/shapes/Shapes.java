package net.minecraft.world.phys.shapes;

/** 모양 도우미(Mojang 이름 shim, N2b-6) — 빈 모양·꽉 찬 블록만 둔다. */
public final class Shapes {

    private static final VoxelShape EMPTY = VoxelShape.furnaceOf(new double[0]);
    private static final VoxelShape BLOCK = VoxelShape.furnaceOf(new double[] {0, 0, 0, 1, 1, 1});

    private Shapes() {}

    public static VoxelShape empty() { return EMPTY; }

    public static VoxelShape block() { return BLOCK; }

    /** 바닐라 isShapeFullBlock: 모양이 한 칸 블록 전체를 덮는가. */
    public static boolean furnaceIsFullBlock(VoxelShape shape) { return shape.furnaceIsFullBlock(); }
}
