package net.minecraft.commands;

import net.minecraft.network.chat.Component;

/** 명령 출력 대상(Mojang 이름 shim) — 바닐라 1.21.1 과 같은 네 추상 메서드와 {@code alwaysAccepts}. {@code NULL} 상수와 {@code CommandSourceStack} 연결은 두지 않았다. */
interface CommandSource {

    default boolean alwaysAccepts() { return false; }
}
