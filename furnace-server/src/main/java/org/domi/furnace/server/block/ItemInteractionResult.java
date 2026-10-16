// 바닐라 ItemInteractionResult(블록에 아이템을 쓴 결과).
package org.domi.furnace.server.block;

public enum ItemInteractionResult {
    SUCCESS, CONSUME, CONSUME_PARTIAL, PASS_TO_DEFAULT_BLOCK_INTERACTION, SKIP_DEFAULT_BLOCK_INTERACTION, FAIL;

    public boolean consumesAction() { return this == SUCCESS || this == CONSUME || this == CONSUME_PARTIAL; }

    public InteractionResult result() {
        return switch (this) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case CONSUME -> InteractionResult.CONSUME;
            case CONSUME_PARTIAL -> InteractionResult.CONSUME_PARTIAL;
            case PASS_TO_DEFAULT_BLOCK_INTERACTION, SKIP_DEFAULT_BLOCK_INTERACTION -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
        };
    }

    public static ItemInteractionResult sidedSuccess(boolean clientSide) { return clientSide ? SUCCESS : CONSUME; }
}
