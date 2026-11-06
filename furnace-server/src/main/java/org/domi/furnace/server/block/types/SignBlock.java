// 바닐라 SignBlock: 표지판 바탕. 물에 잠길 수 있다. 글 편집·염료·밀랍 칠은 블록 엔티티(SignBlockEntity) 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.block.entity.SignBlockEntity;
import org.domi.furnace.server.block.entity.SignText;

public abstract class SignBlock extends BaseEntityBlock {

    protected SignBlock(int block) { super(block); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** RotationSegment.convertToSegment(rotation): Math.round(rot * 16/360) & 15. */
    static int segment(float rotation) { return Math.round(rotation * (16.0F / 360.0F)) & 15; }

    /** RotationSegment.convertToSegment(Direction): 수평 2D 값 << 2(세로 방향은 0). */
    static int segment(int dir) { return org.domi.furnace.server.block.Dir.horizontal(dir) ? org.domi.furnace.server.block.Dir.to2D(dir) << 2 : 0; }

    /** RotationSegment.convertToDirection: 0 북, 4 동, 8 남, 12 서, 그 외 없음(-1). */
    static int segmentDirection(int seg) {
        return switch (seg) {
            case 0 -> org.domi.furnace.server.block.Dir.NORTH;
            case 4 -> org.domi.furnace.server.block.Dir.EAST;
            case 8 -> org.domi.furnace.server.block.Dir.SOUTH;
            case 12 -> org.domi.furnace.server.block.Dir.WEST;
            default -> -1;
        };
    }

    static boolean isHangingSignItem(ItemStack s) {
        if (s.isEmpty() || !ItemContext.installed()) return false;
        String n = ItemContext.name("minecraft:item", s.itemId());
        return n != null && n.endsWith("_hanging_sign");
    }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        boolean hanging = this instanceof CeilingHangingSignBlock || this instanceof WallHangingSignBlock;
        return new SignBlockEntity(pos, state, hanging);
    }

    /** getYRotationDegrees: 글 앞면이 보는 각도. */
    public abstract float yRotationDegrees(int state);

    /** getSignHitboxCenterPosition 의 x·z(칸 안 좌표). 기본 가운데. */
    public double[] hitboxCenter(int state) { return new double[] {0.5, 0.5}; }

    /** SignBlockEntity.isFacingFrontText(player). */
    public boolean isFacingFrontText(SignBlockEntity sign, BlockActor player) {
        int state = sign.blockState();
        double[] c = hitboxCenter(state);
        return sign.isFacingFrontText(player.x(), player.z(), yRotationDegrees(state), c[0], c[1]);
    }

    /** SignApplicator 종류: 염료(색 이름) · 발광 먹물 · 먹물 · 벌집. 아니면 null. */
    static String applicator(ItemStack s) {
        if (s.isEmpty() || !ItemContext.installed()) return null;
        String n = ItemContext.name("minecraft:item", s.itemId());
        if (n == null) return null;
        return switch (n) {
            case "minecraft:glow_ink_sac", "minecraft:ink_sac", "minecraft:honeycomb" -> n;
            default -> n.endsWith("_dye") && java.util.Arrays.asList(SignText.COLORS).contains(n.substring(10, n.length() - 4)) ? n : null;
        };
    }

    /** SignApplicator.canApplyToSign: 벌집은 언제나, 나머지는 그 면에 글이 있을 때. */
    private static boolean canApplyToSign(String app, SignText text, BlockActor player) {
        return app.equals("minecraft:honeycomb") || text.hasMessage(player.isTextFilteringEnabled());
    }

    /** SignApplicator.tryApplyToSign. */
    private static boolean tryApplyToSign(String app, BlockLevel level, SignBlockEntity sign, boolean front) {
        switch (app) {
            case "minecraft:honeycomb" -> {
                if (sign.setWaxed(true, level)) {
                    level.levelEvent(null, 3003, sign.pos(), 0);
                    return true;
                }
                return false;
            }
            case "minecraft:glow_ink_sac" -> {
                if (sign.updateText(t -> t.setHasGlowingText(true), front, level)) {
                    level.playSound(null, sign.pos(), "minecraft:item.glow_ink_sac.use", "blocks", 1.0F, 1.0F);
                    return true;
                }
                return false;
            }
            case "minecraft:ink_sac" -> {
                if (sign.updateText(t -> t.setHasGlowingText(false), front, level)) {
                    level.playSound(null, sign.pos(), "minecraft:item.ink_sac.use", "blocks", 1.0F, 1.0F);
                    return true;
                }
                return false;
            }
            default -> {
                String color = app.substring(10, app.length() - 4);
                if (sign.updateText(t -> t.setColor(color), front, level)) {
                    level.playSound(null, sign.pos(), "minecraft:item.dye.use", "blocks", 1.0F, 1.0F);
                    return true;
                }
                return false;
            }
        }
    }

    private static boolean otherPlayerIsEditingSign(BlockActor player, SignBlockEntity sign) {
        java.util.UUID u = sign.playerWhoMayEdit();
        return u != null && !u.equals(player.uuid());
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        String app = applicator(stack);
        boolean canApply = app != null && player.mayBuild();
        if (canApply && !sign.isWaxed() && !otherPlayerIsEditingSign(player, sign)) {
            boolean front = isFacingFrontText(sign, player);
            if (canApplyToSign(app, sign.text(front), player) && tryApplyToSign(app, level, sign, front)) {
                sign.executeClickCommandsIfPresent(player, level, front, player.isTextFilteringEnabled());
                player.awardStat("minecraft:used:" + org.domi.furnace.server.item.ItemTools.itemName(stack));
                level.gameEvent("minecraft:block_change", sign.pos(), player, sign.blockState());
                if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));   // consume(1, player)
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return InteractionResult.PASS;
        boolean front = isFacingFrontText(sign, player);
        boolean executed = sign.executeClickCommandsIfPresent(player, level, front, player.isTextFilteringEnabled());
        if (sign.isWaxed()) {
            level.playSound(null, sign.pos(), sign.signInteractionFailedSoundEvent(), "blocks", 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        } else if (executed) {
            return InteractionResult.SUCCESS;
        } else if (!otherPlayerIsEditingSign(player, sign) && player.mayBuild() && sign.text(front).allPlainText(player.isTextFilteringEnabled())) {
            openTextEdit(player, sign, front);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** openTextEdit: 편집 허락 후 화면을 연다(블록 갱신 + open_sign_editor). */
    public static void openTextEdit(BlockActor player, SignBlockEntity sign, boolean front) {
        sign.setAllowedPlayerEditor(player.uuid());
        player.openTextEdit(sign.pos(), sign.blockState(), front);
    }

    /** 갓 놓은 표지판 아이템(SignItem.updateCustomBlockEntityTag): block_entity_data 가 없으면 앞면 편집을 연다. */
    public static void afterPlaced(BlockLevel level, Pos pos, BlockActor player, ItemStack stack) {
        if (player == null || stack.patch().entries().get("minecraft:block_entity_data") != null) return;
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign && B.behavior(level.getBlockState(pos)) instanceof SignBlock) {
            openTextEdit(player, sign, true);
        }
    }
}
