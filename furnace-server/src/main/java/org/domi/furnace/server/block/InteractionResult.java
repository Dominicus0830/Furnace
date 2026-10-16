// 바닐라 InteractionResult.
package org.domi.furnace.server.block;

public enum InteractionResult {
    SUCCESS, SUCCESS_NO_ITEM_USED, CONSUME, CONSUME_PARTIAL, PASS, FAIL;

    public boolean consumesAction() { return this == SUCCESS || this == CONSUME || this == CONSUME_PARTIAL || this == SUCCESS_NO_ITEM_USED; }

    public boolean shouldSwing() { return this == SUCCESS || this == SUCCESS_NO_ITEM_USED; }

    public boolean shouldAwardStats() { return this == SUCCESS || this == CONSUME; }

    /** 서버에서는 CONSUME. */
    public static InteractionResult sidedSuccess(boolean clientSide) { return clientSide ? SUCCESS : CONSUME; }
}
