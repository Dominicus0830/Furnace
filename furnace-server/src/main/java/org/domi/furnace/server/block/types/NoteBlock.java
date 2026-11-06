// 바닐라 NoteBlock: 소리 블록(instrument·note 0..24·powered). 악기는 아래 블록(위가 몹 머리면 그 소리), 신호가 켜지거나 누르면 블록 이벤트로 소리.
// 블록별 악기는 바닐라 BlockState.instrument() 를 뽑은 표(blocks/instruments_1.21.1.txt).
package org.domi.furnace.server.block.types;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class NoteBlock extends BlockBehavior {

    static final Prop INSTRUMENT = B.prop("instrument"), NOTE = B.prop("note"), POWERED = B.prop("powered");

    /** NoteBlockInstrument: 이름·소리·종류(0 기본 블록, 1 몹 머리, 2 사용자 머리). */
    enum Instrument {
        HARP("harp", 0), BASEDRUM("basedrum", 0), SNARE("snare", 0), HAT("hat", 0), BASS("bass", 0), FLUTE("flute", 0), BELL("bell", 0),
        GUITAR("guitar", 0), CHIME("chime", 0), XYLOPHONE("xylophone", 0), IRON_XYLOPHONE("iron_xylophone", 0), COW_BELL("cow_bell", 0),
        DIDGERIDOO("didgeridoo", 0), BIT("bit", 0), BANJO("banjo", 0), PLING("pling", 0), ZOMBIE("zombie", 1), SKELETON("skeleton", 1),
        CREEPER("creeper", 1), DRAGON("dragon", 1), WITHER_SKELETON("wither_skeleton", 1), PIGLIN("piglin", 1), CUSTOM_HEAD("custom_head", 2);

        final String name;
        final int type;

        Instrument(String name, int type) {
            this.name = name;
            this.type = type;
        }

        boolean isTunable() { return type == 0; }

        boolean hasCustomSound() { return type == 2; }

        boolean worksAboveNoteBlock() { return type != 0; }

        String sound() {
            return switch (this) {
                case ZOMBIE, SKELETON, CREEPER, WITHER_SKELETON, PIGLIN -> "minecraft:block.note_block.imitate." + name;
                case DRAGON -> "minecraft:block.note_block.imitate.ender_dragon";
                default -> "minecraft:block.note_block." + name;
            };
        }

        static Instrument byName(String n) {
            for (Instrument i : values()) if (i.name.equals(n)) return i;
            throw new IllegalArgumentException(n);
        }
    }

    private static final Instrument[] BY_BLOCK = new Instrument[B.BLOCKS.blockCount()];
    static {
        java.util.Arrays.fill(BY_BLOCK, Instrument.HARP);
        try (var in = NoteBlock.class.getClassLoader().getResourceAsStream("blocks/instruments_1.21.1.txt");
             var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(" ");
                Instrument inst = Instrument.byName(p[0]);
                for (int i = 1; i < p.length; i++) {
                    int b = B.id(p[i]);
                    if (b >= 0) BY_BLOCK[b] = inst;
                }
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** BlockState.instrument(). */
    static Instrument instrumentOf(int state) { return BY_BLOCK[B.block(state)]; }

    public NoteBlock(int block) { super(block); }

    private static Instrument instrument(int state) { return Instrument.byName(INSTRUMENT.valueName(state)); }

    private int setInstrument(BlockLevel level, Pos pos, int state) {
        Instrument above = instrumentOf(level.getBlockState(pos.above()));
        if (above.worksAboveNoteBlock()) return INSTRUMENT.with(state, above.name);
        Instrument below = instrumentOf(level.getBlockState(pos.below()));
        return INSTRUMENT.with(state, (below.worksAboveNoteBlock() ? Instrument.HARP : below).name);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return setInstrument(ctx.level(), ctx.clickedPos(), defaultState()); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.axis(dir) == Dir.AXIS_Y ? setInstrument(level, pos, state) : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != POWERED.is(state)) {
            if (signal) playNote(null, state, level, pos);
            level.setBlock(pos, POWERED.with(state, signal), 3);
        }
    }

    private void playNote(BlockActor entity, int state, BlockLevel level, Pos pos) {
        if (!instrument(state).worksAboveNoteBlock() || B.isAir(level.getBlockState(pos.above()))) {
            level.blockEvent(pos, block, 0, 0);
            level.gameEvent("minecraft:note_block_play", pos, entity, -1);
        }
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        return B.holdsTag(stack, "minecraft:noteblock_top_instruments") && hit.face() == Dir.UP
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        int s = NOTE.cycle(state);
        level.setBlock(pos, s, 3);
        playNote(player, s, level, pos);
        player.awardStat("minecraft:tune_noteblock");
        return InteractionResult.CONSUME;
    }

    @Override
    public void attack(int state, BlockLevel level, Pos pos, BlockActor player) {
        playNote(player, state, level, pos);
        player.awardStat("minecraft:play_noteblock");
    }

    /** getPitchFromNote: 2^((note - 12) / 12). */
    public static float getPitchFromNote(int note) { return (float) Math.pow(2.0, (note - 12) / 12.0); }

    @Override
    public boolean triggerEvent(int state, BlockLevel level, Pos pos, int type, int data) {
        Instrument inst = instrument(state);
        // 소리 높이(조율되는 악기는 note, 아니면 1). 음표 파티클은 서버에서 보내지 않는다(클라가 블록 이벤트로).
        float pitch = inst.isTunable() ? getPitchFromNote(NOTE.intValue(state)) : 1.0F;
        String sound;
        if (inst.hasCustomSound()) {
            // TODO(블록 엔티티): 위 머리 블록 엔티티의 note_block_sound — 없으면 바닐라도 false.
            return false;
        }
        sound = inst.sound();
        long seed = level.random().nextLong();   // playSeededSound 의 시드(TODO(소리): 패킷에 시드 실어 보내기)
        level.playSound(null, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, sound, "record", 3.0F, pitch);
        return true;
    }
}
