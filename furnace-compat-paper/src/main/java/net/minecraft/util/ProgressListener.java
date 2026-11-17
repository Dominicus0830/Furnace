package net.minecraft.util;

import net.minecraft.network.chat.Component;

/** 바닐라 저장 진행 듣기(Mojang 이름, T-203 — {@code ServerLevel#save} 의 인자 타입). */
public interface ProgressListener {

    void progressStartNoAbort(Component title);

    void progressStart(Component title);

    void progressStage(Component stage);

    void progressStagePercentage(int percentage);

    void stop();
}
