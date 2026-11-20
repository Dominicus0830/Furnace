package net.minecraft.world;

/** 상호작용 결과(Mojang 이름 shim, T-212). 상수·판정은 바닐라 1.21.1 바이트코드와 같다. */
public enum InteractionResult {
    SUCCESS,
    SUCCESS_NO_ITEM_USED,
    CONSUME,
    CONSUME_PARTIAL,
    PASS,
    FAIL;

    public boolean consumesAction() { return this == SUCCESS || this == CONSUME || this == CONSUME_PARTIAL || this == SUCCESS_NO_ITEM_USED; }

    public boolean shouldSwing() { return this == SUCCESS || this == SUCCESS_NO_ITEM_USED; }

    public boolean indicateItemUse() { return this == SUCCESS || this == CONSUME; }

    /** 서버(clientSide 거짓)에서는 CONSUME. */
    public static InteractionResult sidedSuccess(boolean clientSide) { return clientSide ? SUCCESS : CONSUME; }
}
