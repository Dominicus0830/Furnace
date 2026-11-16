package net.minecraft.network.syncher;

/** 메타데이터 칸 하나의 열쇠: 번호와 값 종류(Mojang 이름 shim, 바닐라와 같은 레코드). */
public record EntityDataAccessor<T>(int id, EntityDataSerializer<T> serializer) {

    @Override
    public boolean equals(Object o) { return o instanceof EntityDataAccessor<?> a && id == a.id; }

    @Override
    public int hashCode() { return id; }

    @Override
    public String toString() { return "<entity data: " + id + ">"; }
}
