// 바닐라 SignBlockEntity(+ HangingSignBlockEntity): 앞·뒤 면 글(SignText), 밀랍 칠, 편집 중인 플레이어.
// 저장은 front_text·back_text·is_waxed. 갱신 태그는 저장 내용 그대로(블록 갱신 때 block_entity_data 로 보낸다).
// 틱: 편집을 허락한 플레이어가 사라졌거나 상호작용 거리 + 4 밖이면 허락을 지운다.
// 레벨에 놓인 뒤 읽으면(/data·/setblock·/clone·아이템 block_entity_data·구조물 배치) 읽은 면의 줄마다 선택자·점수를 푼다(loadLines → loadLine).
// 청크를 읽을 때는 레벨이 없어 풀지 않는다(바닐라와 같다).
package org.domi.furnace.server.block.entity;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class SignBlockEntity extends BlockEntity {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SignBlockEntity.class);

    private UUID playerWhoMayEdit;
    private SignText frontText = new SignText();
    private SignText backText = new SignText();
    private boolean isWaxed;
    private final boolean hanging;
    /** 마지막 load 가 그 면을 읽었는가 — afterLoad 가 읽은 면만 푼다(바닐라는 코덱이 성공한 면만 loadLines). */
    private boolean loadedFront, loadedBack;

    public SignBlockEntity(Pos pos, int state) { this(pos, state, false); }

    public static SignBlockEntity hanging(Pos pos, int state) { return new SignBlockEntity(pos, state, true); }

    public SignBlockEntity(Pos pos, int state, boolean hanging) {
        super(hanging ? "minecraft:hanging_sign" : "minecraft:sign", pos, state);
        this.hanging = hanging;
    }

    @Override
    public boolean isValid(int state) {
        var b = B.behavior(state);
        boolean h = b instanceof org.domi.furnace.server.block.types.CeilingHangingSignBlock || b instanceof org.domi.furnace.server.block.types.WallHangingSignBlock;
        return b instanceof org.domi.furnace.server.block.types.SignBlock && h == hanging;
    }

    /** getTextLineHeight / getMaxTextLineWidth(클라이언트 표시용, 서버 동작에는 쓰지 않는다). */
    public int textLineHeight() { return hanging ? 9 : 10; }

    public int maxTextLineWidth() { return hanging ? 60 : 90; }

    /** 밀랍 칠한 표지판을 건드릴 때 소리. */
    public String signInteractionFailedSoundEvent() {
        return hanging ? "minecraft:block.hanging_sign.waxed_interact_fail" : "minecraft:block.sign.waxed_interact_fail";
    }

    /**
     * isFacingFrontText: 블록의 글 회전과 (표지판 판 가운데 → 플레이어 발) 방향의 각도 차가 90 이하.
     * yRot·center 는 블록이 정한다(SignBlock.getYRotationDegrees·getSignHitboxCenterPosition).
     */
    public boolean isFacingFrontText(double playerX, double playerZ, float yRot, double centerX, double centerZ) {
        double dx = playerX - (pos.x() + centerX);
        double dz = playerZ - (pos.z() + centerZ);
        float f = (float) (org.domi.furnace.server.entity.ai.control.Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
        return org.domi.furnace.server.entity.ai.control.Mth.degreesDifferenceAbs(yRot, f) <= 90.0F;
    }

    public SignText text(boolean front) { return front ? frontText : backText; }

    public SignText frontText() { return frontText; }

    public SignText backText() { return backText; }

    /** updateSignText: 밀랍이 아니고 편집 허락을 받은 플레이어만. 줄마다 기존 스타일을 지키고 글자를 바꾼다. */
    public void updateSignText(UUID player, boolean filtering, boolean front, List<String> lines, BlockLevel lv) {
        if (!isWaxed && player.equals(playerWhoMayEdit) && lv != null) {
            updateText(t -> setMessages(filtering, lines, t), front, lv);
            setAllowedPlayerEditor(null);
            lv.sendBlockUpdated(pos);
        } else {
            LOG.warn("Player {} just tried to change non-editable sign", player);
        }
    }

    public boolean updateText(UnaryOperator<SignText> f, boolean front, BlockLevel lv) { return setText(f.apply(text(front)), front, lv); }

    private static SignText setMessages(boolean filtering, List<String> lines, SignText t) {
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            var style = t.message(i, filtering).style();
            if (filtering) {
                t = t.setMessage(i, Text.text(raw, style));
            } else {
                t = t.setMessage(i, Text.text(raw, style), Text.text(raw, style));   // 거르지 않으면 filteredOrEmpty == raw
            }
        }
        return t;
    }

    public boolean setText(SignText t, boolean front, BlockLevel lv) {
        if (front) {
            if (t == frontText) return false;
            frontText = t;
        } else {
            if (t == backText) return false;
            backText = t;
        }
        markUpdated(lv);
        return true;
    }

    /** canExecuteClickCommands: 밀랍 칠이고 그 면에 클릭 명령이 있다. */
    public boolean canExecuteClickCommands(boolean front, boolean filtering) { return isWaxed && text(front).hasAnyClickCommands(filtering); }

    /** executeClickCommandsIfPresent: 줄마다 run_command 클릭이면 명령을 실행한다. 하나라도 있었으면 true. */
    public boolean executeClickCommandsIfPresent(BlockActor player, BlockLevel lv, boolean front, boolean filtering) {
        boolean any = false;
        for (Text t : text(front).messages(filtering)) {
            ClickEvent e = t.clickEvent();
            if (e != null && e.action() == ClickEvent.Action.RUN_COMMAND) {
                lv.runSignCommand(player, pos, e.value());
                any = true;
            }
        }
        return any;
    }

    public void setAllowedPlayerEditor(UUID uuid) { playerWhoMayEdit = uuid; }

    public UUID playerWhoMayEdit() { return playerWhoMayEdit; }

    public boolean isWaxed() { return isWaxed; }

    public boolean setWaxed(boolean v, BlockLevel lv) {
        if (isWaxed != v) {
            isWaxed = v;
            markUpdated(lv);
            return true;
        }
        return false;
    }

    /** markUpdated: setChanged + sendBlockUpdated(3). */
    private void markUpdated(BlockLevel lv) {
        if (lv == null) return;
        setChanged(lv);
        lv.sendBlockUpdated(pos);
    }

    /** playerIsTooFarAwayToEdit. */
    public boolean playerIsTooFarAwayToEdit(UUID uuid, BlockLevel lv) {
        var p = lv.playerByUuid(uuid);
        return p == null || !new PlayerActor(p).canInteractWithBlock(pos, 4.0);
    }

    @Override public boolean ticking() { return true; }

    @Override
    public void tick(BlockLevel lv) {
        UUID u = playerWhoMayEdit;
        if (u != null && playerIsTooFarAwayToEdit(u, lv)) setAllowedPlayerEditor(null);
    }

    @Override
    public void load(NbtCompound tag) {
        loadedFront = loadedBack = false;
        if (tag.tags().get("front_text") instanceof NbtCompound c) {
            SignText t = SignText.load(c);
            if (t != null) {
                frontText = t;
                loadedFront = true;
            }
        }
        if (tag.tags().get("back_text") instanceof NbtCompound c) {
            SignText t = SignText.load(c);
            if (t != null) {
                backText = t;
                loadedBack = true;
            }
        }
        isWaxed = tag.getBoolean("is_waxed");
    }

    /** loadLines(레벨 있음): 방금 읽은 면(앞 → 뒤)의 줄마다 걸러지지 않은 것 → 걸러진 것 순서로 푼다. */
    @Override
    public void afterLoad(BlockLevel level) {
        if (loadedFront) loadLines(level, true);
        if (loadedBack) loadLines(level, false);
        loadedFront = loadedBack = false;
    }

    private void loadLines(BlockLevel level, boolean front) {
        SignText base = front ? frontText : backText;
        java.util.List<Text> lines = new java.util.ArrayList<>(SignText.LINES * 2);
        for (int i = 0; i < SignText.LINES; i++) {
            lines.add(base.message(i, false));
            lines.add(base.message(i, true));
        }
        level.resolveSignLines(pos, lines, (lv, out) -> {
            // 늦게 끝난 경우(다른 region 의 엔티티를 찾음): 그새 표지판이 바뀌었으면 버린다
            if (lv.getBlockEntity(pos) != this || (front ? frontText : backText) != base) return;
            SignText t = base;
            for (int i = 0; i < SignText.LINES; i++) t = t.setMessage(i, out.get(2 * i), out.get(2 * i + 1));
            if (front) frontText = t;
            else backText = t;
            if (lv != level) markUpdated(lv);
        });
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putCompound("front_text", frontText.save());
        tag.putCompound("back_text", backText.save());
        tag.putBoolean("is_waxed", isWaxed);
    }

    @Override public NbtCompound updateTag() { return saveCustomOnly(); }

    @Override public boolean hasUpdatePacket() { return true; }

    /** onlyOpCanSetNbt. */
    public boolean onlyOpCanSetNbt() { return true; }
}
