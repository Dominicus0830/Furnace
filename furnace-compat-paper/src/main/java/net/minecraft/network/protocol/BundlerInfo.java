package net.minecraft.network.protocol;

/** 묶음 패킷 처리 정보 shim(Mojang 이름, N3 1단계). 묶음을 안 쓰는 프로토콜은 {@link #NO_BUNDLER}. */
public interface BundlerInfo {

    int BUNDLE_SIZE_LIMIT = 4096;

    /** 바닐라 {@code BundlerInfo.EMPTY} 와 같은 자리: 묶음이 없는 프로토콜. */
    BundlerInfo EMPTY = new BundlerInfo() {};
}
