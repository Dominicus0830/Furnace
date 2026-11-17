package net.minecraft.world.phys;

/** 맞은 자리 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 모양 — 블록에 대고 쓰기 패킷(use_item_on)의 BlockHitResult 부모. */
public abstract class HitResult {

    protected final Vec3 location;

    protected HitResult(Vec3 location) { this.location = location; }

    public abstract Type getType();

    public Vec3 getLocation() { return location; }

    public enum Type {
        MISS, BLOCK, ENTITY
    }
}
