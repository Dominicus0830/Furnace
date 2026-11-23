package net.minecraft.core;

/** 상자 안을 x → y → z 순서로 훑는 커서(Mojang 이름 shim, 영역 9). 면·모서리·꼭짓점 구분(getNextType)은 바닐라 1.21.1 과 같다. */
public class Cursor3D {

    public static final int TYPE_INSIDE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_EDGE = 2;
    public static final int TYPE_CORNER = 3;

    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int total;
    private final boolean positive;
    private int taken;
    private int cx;
    private int cy;
    private int cz;

    public Cursor3D(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.baseX = minX;
        this.baseY = minY;
        this.baseZ = minZ;
        this.sizeX = maxX - minX + 1;
        this.sizeY = maxY - minY + 1;
        this.sizeZ = maxZ - minZ + 1;
        this.total = this.sizeX * this.sizeY * this.sizeZ;
        this.positive = this.sizeX > 0 && this.sizeY > 0 && this.sizeZ > 0;
    }

    public boolean advance() {
        if (this.taken == this.total) return false;
        if (this.positive) {
            // 나눗셈 없이 증분(크기가 0 이하인 뒤집힌 상자만 나눗셈 길로 바닐라와 같은 값을 낸다)
            if (this.taken > 0 && ++this.cx == this.sizeX) {
                this.cx = 0;
                if (++this.cy == this.sizeY) {
                    this.cy = 0;
                    this.cz++;
                }
            }
        } else {
            this.cx = this.taken % this.sizeX;
            int rest = this.taken / this.sizeX;
            this.cy = rest % this.sizeY;
            this.cz = rest / this.sizeY;
        }
        this.taken++;
        return true;
    }

    public int nextX() { return this.baseX + this.cx; }

    public int nextY() { return this.baseY + this.cy; }

    public int nextZ() { return this.baseZ + this.cz; }

    public int getNextType() {
        int i = 0;
        if (this.cx == 0 || this.cx == this.sizeX - 1) i++;
        if (this.cy == 0 || this.cy == this.sizeY - 1) i++;
        if (this.cz == 0 || this.cz == this.sizeZ - 1) i++;
        return i;
    }
}
