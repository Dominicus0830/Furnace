package net.minecraft.nbt;

/** 바닐라 1.21.1 NBT 오류(Mojang 이름, 같은 상속·생성자). */
public class NbtException extends RuntimeException {

    public NbtException(String message) { super(message); }
}
