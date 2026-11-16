package net.minecraft.world.phys.shapes;

/**
 * 블록 모양(Mojang 이름 shim, N2b-6). 바닐라는 복셀 격자지만 여기서는 서버가 바닐라에서 뽑은 상자 목록(겹치지 않는 최적화된 꼴)을 그대로 든다.
 * 모양 연산(충돌·합치기)은 두지 않았다 — 서버 안에서 한다.
 */
public abstract class VoxelShape {

    /** 상자마다 minX,minY,minZ,maxX,maxY,maxZ. */
    private final double[] boxes;

    VoxelShape(double[] boxes) { this.boxes = boxes; }

    public boolean isEmpty() { return boxes.length == 0; }

    /** 호환 모듈 전용 — 상자 목록(복사본). */
    public double[] furnaceBoxes() { return boxes.clone(); }

    /** 호환 모듈 전용 — 상자 부피 합이 1 이고 모두 [0,1] 안이면 꽉 찬 블록(상자가 겹치지 않는 꼴에서 바닐라 isShapeFullBlock 과 같다). */
    boolean furnaceIsFullBlock() {
        if (boxes.length == 0) return false;
        double volume = 0;
        for (int i = 0; i < boxes.length; i += 6) {
            for (int k = 0; k < 3; k++) if (boxes[i + k] < 0 || boxes[i + 3 + k] > 1) return false;
            volume += (boxes[i + 3] - boxes[i]) * (boxes[i + 4] - boxes[i + 1]) * (boxes[i + 5] - boxes[i + 2]);
        }
        return Math.abs(volume - 1.0) < 1.0E-9;
    }

    /** 호환 모듈 전용 — 서버 상자 목록에서 만든다. */
    public static VoxelShape furnaceOf(double[] boxes) {
        return new VoxelShape(boxes) {};
    }
}
