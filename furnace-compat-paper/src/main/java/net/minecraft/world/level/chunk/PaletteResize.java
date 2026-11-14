package net.minecraft.world.level.chunk;

/** 팔레트가 꽉 찼을 때 컨테이너에 더 큰 팔레트를 청하는 길(Mojang 이름, 바닐라처럼 패키지 전용, N2b-1). 새 팔레트에서의 번호를 돌려준다. */
interface PaletteResize<T> {

    int onResize(int bits, T value);
}
