// 바닐라 Display: 블록·아이템·글자를 변환(이동·크기·회전)해 보여 주는 보이지 않는 엔티티의 공통 부분. 보간은 클라이언트가 하므로 서버는 데이터만 가진다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Display extends Entity {

    /** getPistonPushReaction = IGNORE. */
    @Override
    public boolean ignoresPistonPush() { return true; }

    public static final int DATA_START_DELTA = 8, DATA_INTERPOLATION = 9, DATA_TELEPORT = 10, DATA_TRANSLATION = 11, DATA_SCALE = 12, DATA_LEFT_ROT = 13, DATA_RIGHT_ROT = 14,
            DATA_BILLBOARD = 15, DATA_BRIGHTNESS = 16, DATA_VIEW_RANGE = 17, DATA_SHADOW_RADIUS = 18, DATA_SHADOW_STRENGTH = 19, DATA_WIDTH = 20, DATA_HEIGHT = 21, DATA_GLOW = 22;
    private static final String[] BILLBOARDS = {"fixed", "vertical", "horizontal", "center"};

    protected Display(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_START_DELTA, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_INTERPOLATION, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_TELEPORT, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_TRANSLATION, SetEntityData.SERIALIZER_VECTOR3, new float[] {0, 0, 0});
        entityData.define(DATA_SCALE, SetEntityData.SERIALIZER_VECTOR3, new float[] {1, 1, 1});
        entityData.define(DATA_LEFT_ROT, SetEntityData.SERIALIZER_QUATERNION, new float[] {0, 0, 0, 1});
        entityData.define(DATA_RIGHT_ROT, SetEntityData.SERIALIZER_QUATERNION, new float[] {0, 0, 0, 1});
        entityData.define(DATA_BILLBOARD, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_BRIGHTNESS, SetEntityData.SERIALIZER_INT, -1);
        entityData.define(DATA_VIEW_RANGE, SetEntityData.SERIALIZER_FLOAT, 1.0F);
        entityData.define(DATA_SHADOW_RADIUS, SetEntityData.SERIALIZER_FLOAT, 0.0F);
        entityData.define(DATA_SHADOW_STRENGTH, SetEntityData.SERIALIZER_FLOAT, 1.0F);
        entityData.define(DATA_WIDTH, SetEntityData.SERIALIZER_FLOAT, 0.0F);
        entityData.define(DATA_HEIGHT, SetEntityData.SERIALIZER_FLOAT, 0.0F);
        entityData.define(DATA_GLOW, SetEntityData.SERIALIZER_INT, -1);
    }

    @Override public void tick() {}

    @Override public boolean broadcastToPlayer(Player p) { return true; }

    public void setBillboard(int b) { entityData.set(DATA_BILLBOARD, (byte) b); }

    public void setTransformation(float[] translation, float[] leftRot, float[] scale, float[] rightRot) {
        entityData.set(DATA_TRANSLATION, translation);
        entityData.set(DATA_LEFT_ROT, leftRot);
        entityData.set(DATA_SCALE, scale);
        entityData.set(DATA_RIGHT_ROT, rightRot);
    }

    public void setSize(float w, float h) {
        entityData.set(DATA_WIDTH, w);
        entityData.set(DATA_HEIGHT, h);
        setDimensions(w, h, dimEye);
    }

    private static NbtList floats(float[] v) {
        NbtList l = new NbtList(NbtIo.TAG_FLOAT);
        for (float f : v) l.add(f);
        return l;
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        NbtCompound t = new NbtCompound();
        t.putList("translation", floats((float[]) entityData.get(DATA_TRANSLATION)));
        t.putList("left_rotation", floats((float[]) entityData.get(DATA_LEFT_ROT)));
        t.putList("scale", floats((float[]) entityData.get(DATA_SCALE)));
        t.putList("right_rotation", floats((float[]) entityData.get(DATA_RIGHT_ROT)));
        tag.putCompound("transformation", t);
        tag.putString("billboard", BILLBOARDS[((Number) entityData.get(DATA_BILLBOARD)).intValue() & 3]);
        tag.putInt("interpolation_duration", (Integer) entityData.get(DATA_INTERPOLATION));
        tag.putInt("teleport_duration", (Integer) entityData.get(DATA_TELEPORT));
        tag.putFloat("view_range", (Float) entityData.get(DATA_VIEW_RANGE));
        tag.putFloat("shadow_radius", (Float) entityData.get(DATA_SHADOW_RADIUS));
        tag.putFloat("shadow_strength", (Float) entityData.get(DATA_SHADOW_STRENGTH));
        tag.putFloat("width", (Float) entityData.get(DATA_WIDTH));
        tag.putFloat("height", (Float) entityData.get(DATA_HEIGHT));
        tag.putInt("glow_color_override", (Integer) entityData.get(DATA_GLOW));
        int packed = (Integer) entityData.get(DATA_BRIGHTNESS);
        if (packed != -1) {
            NbtCompound b = new NbtCompound();
            b.putInt("block", packed >> 4 & 15);
            b.putInt("sky", packed >> 20 & 15);
            tag.putCompound("brightness", b);
        }
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        // 바닐라 Transformation.EXTENDED_CODEC: 복합 태그(회전은 [x,y,z,w] 또는 {angle, axis}) 또는 16개 행렬. 코덱이 실패하면 transformation 은 그대로.
        if (tag.tags().containsKey("transformation")) {
            DisplayTransform.Decomposed d = DisplayTransform.decode(tag.tags().get("transformation"));
            if (d != null) setTransformation(d.translation(), d.leftRotation(), d.scale(), d.rightRotation());
        }
        if (tag.tags().get("billboard") instanceof String s) {
            for (int i = 0; i < BILLBOARDS.length; i++) if (BILLBOARDS[i].equals(s)) setBillboard(i);
        }
        if (tag.containsNumber("interpolation_duration")) entityData.set(DATA_INTERPOLATION, tag.getIntOr0("interpolation_duration"));
        if (tag.containsNumber("start_interpolation")) entityData.set(DATA_START_DELTA, tag.getIntOr0("start_interpolation"));
        if (tag.containsNumber("teleport_duration")) entityData.set(DATA_TELEPORT, Math.max(0, Math.min(59, tag.getIntOr0("teleport_duration"))));
        if (tag.containsNumber("view_range")) entityData.set(DATA_VIEW_RANGE, tag.getFloat("view_range"));
        if (tag.containsNumber("shadow_radius")) entityData.set(DATA_SHADOW_RADIUS, tag.getFloat("shadow_radius"));
        if (tag.containsNumber("shadow_strength")) entityData.set(DATA_SHADOW_STRENGTH, tag.getFloat("shadow_strength"));
        if (tag.containsNumber("glow_color_override")) entityData.set(DATA_GLOW, tag.getIntOr0("glow_color_override"));
        if (tag.containsNumber("width") || tag.containsNumber("height")) {
            setSize(tag.containsNumber("width") ? tag.getFloat("width") : (Float) entityData.get(DATA_WIDTH),
                    tag.containsNumber("height") ? tag.getFloat("height") : (Float) entityData.get(DATA_HEIGHT));
        }
        if (tag.tags().get("brightness") instanceof NbtCompound b) entityData.set(DATA_BRIGHTNESS, b.getIntOr0("block") << 4 | b.getIntOr0("sky") << 20);
        else entityData.set(DATA_BRIGHTNESS, -1);
    }

    /** 블록 표시. */
    public static class BlockDisplay extends Display {
        public static final int DATA_BLOCK_STATE = 23;

        public BlockDisplay(ChunkLevel level, int id) { super(EntityType.BLOCK_DISPLAY, level, id); }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            entityData.define(DATA_BLOCK_STATE, SetEntityData.SERIALIZER_BLOCK_STATE, org.domi.furnace.server.block.B.defaultState("air"));
        }

        public void setBlockState(int state) { entityData.set(DATA_BLOCK_STATE, state); }

        @Override
        protected void addAdditionalSaveData(NbtCompound tag) {
            super.addAdditionalSaveData(tag);
            tag.putCompound("block_state", org.domi.furnace.server.block.entity.PistonMovingBlockEntity.writeBlockState((Integer) entityData.get(DATA_BLOCK_STATE)));
        }

        @Override
        protected void readAdditionalSaveData(NbtCompound tag) {
            super.readAdditionalSaveData(tag);
            if (tag.tags().get("block_state") instanceof NbtCompound c) setBlockState(org.domi.furnace.server.block.entity.PistonMovingBlockEntity.readBlockState(c));
        }
    }

    /** 아이템 표시. 표시 방식(none, thirdperson_lefthand, ... gui, ground, fixed)은 번호로 가진다. */
    public static class ItemDisplay extends Display {
        public static final int DATA_ITEM = 23, DATA_ITEM_DISPLAY = 24;
        private static final String[] MODES = {"none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand", "head", "gui", "ground", "fixed"};

        public ItemDisplay(ChunkLevel level, int id) { super(EntityType.ITEM_DISPLAY, level, id); }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            entityData.define(DATA_ITEM, SetEntityData.SERIALIZER_ITEM, ItemStack.EMPTY);
            entityData.define(DATA_ITEM_DISPLAY, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        }

        public ItemStack getItem() { return (ItemStack) entityData.get(DATA_ITEM); }

        public void setItem(ItemStack s) { entityData.set(DATA_ITEM, s); }

        @Override
        protected void addAdditionalSaveData(NbtCompound tag) {
            super.addAdditionalSaveData(tag);
            ItemStack s = (ItemStack) entityData.get(DATA_ITEM);
            if (!s.isEmpty()) tag.putCompound("item", ItemStackNbt.write(s));
            tag.putString("item_display", MODES[((Number) entityData.get(DATA_ITEM_DISPLAY)).intValue() % MODES.length]);
        }

        @Override
        protected void readAdditionalSaveData(NbtCompound tag) {
            super.readAdditionalSaveData(tag);
            setItem(tag.tags().get("item") instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY);
            if (tag.tags().get("item_display") instanceof String m) {
                for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(m)) entityData.set(DATA_ITEM_DISPLAY, (byte) i);
            }
        }
    }

    /**
     * 글자 표시. 플래그(DATA_FLAGS): 1 그림자, 2 투과, 4 기본 배경, 8 왼쪽 정렬, 16 오른쪽 정렬(둘 다 아니면 가운데).
     * NBT text 는 JSON 컴포넌트 문자열. TODO(선택자): 바닐라는 읽을 때 ComponentUtils.updateForEntity 로 선택자·점수를 풀지만
     * 엔티티가 서버 코어에 닿는 길이 없어 풀지 않고 그대로 둔다.
     */
    public static class TextDisplay extends Display {
        public static final int DATA_TEXT = 23, DATA_LINE_WIDTH = 24, DATA_BACKGROUND = 25, DATA_TEXT_OPACITY = 26, DATA_FLAGS = 27;
        public static final byte FLAG_SHADOW = 1, FLAG_SEE_THROUGH = 2, FLAG_USE_DEFAULT_BACKGROUND = 4, FLAG_ALIGN_LEFT = 8, FLAG_ALIGN_RIGHT = 16;
        private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TextDisplay.class);

        public TextDisplay(ChunkLevel level, int id) { super(EntityType.TEXT_DISPLAY, level, id); }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            entityData.define(DATA_TEXT, SetEntityData.SERIALIZER_COMPONENT, org.domi.furnace.api.text.Text.empty());
            entityData.define(DATA_LINE_WIDTH, SetEntityData.SERIALIZER_INT, 200);
            entityData.define(DATA_BACKGROUND, SetEntityData.SERIALIZER_INT, 0x40000000);
            entityData.define(DATA_TEXT_OPACITY, SetEntityData.SERIALIZER_BYTE, (byte) -1);
            entityData.define(DATA_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        }

        public void setText(org.domi.furnace.api.text.Text t) { entityData.set(DATA_TEXT, t); }

        public org.domi.furnace.api.text.Text text() { return (org.domi.furnace.api.text.Text) entityData.get(DATA_TEXT); }

        public byte flags() { return ((Number) entityData.get(DATA_FLAGS)).byteValue(); }

        public void setFlags(byte f) { entityData.set(DATA_FLAGS, f); }

        public void setLineWidth(int w) { entityData.set(DATA_LINE_WIDTH, w); }

        public void setBackground(int argb) { entityData.set(DATA_BACKGROUND, argb); }

        public void setTextOpacity(byte o) { entityData.set(DATA_TEXT_OPACITY, o); }

        private static final String[] ALIGNMENTS = {"center", "left", "right"};

        private static String alignmentOf(byte flags) {
            if ((flags & FLAG_ALIGN_LEFT) != 0) return "left";
            return (flags & FLAG_ALIGN_RIGHT) != 0 ? "right" : "center";
        }

        @Override
        protected void addAdditionalSaveData(NbtCompound tag) {
            super.addAdditionalSaveData(tag);
            tag.putString("text", text().toJson());
            tag.putInt("line_width", (Integer) entityData.get(DATA_LINE_WIDTH));
            tag.putInt("background", (Integer) entityData.get(DATA_BACKGROUND));
            tag.putByte("text_opacity", ((Number) entityData.get(DATA_TEXT_OPACITY)).byteValue());
            byte f = flags();
            tag.putBoolean("shadow", (f & FLAG_SHADOW) != 0);
            tag.putBoolean("see_through", (f & FLAG_SEE_THROUGH) != 0);
            tag.putBoolean("default_background", (f & FLAG_USE_DEFAULT_BACKGROUND) != 0);
            tag.putString("alignment", alignmentOf(f));
        }

        @Override
        protected void readAdditionalSaveData(NbtCompound tag) {
            super.readAdditionalSaveData(tag);
            if (tag.containsNumber("line_width")) entityData.set(DATA_LINE_WIDTH, tag.getIntOr0("line_width"));
            if (tag.containsNumber("text_opacity")) entityData.set(DATA_TEXT_OPACITY, (byte) tag.getIntOr0("text_opacity"));
            if (tag.containsNumber("background")) entityData.set(DATA_BACKGROUND, tag.getIntOr0("background"));
            // 바닐라: 플래그를 0 에서 다시 쌓는다(NBT 에 없는 비트는 꺼진다).
            byte f = 0;
            if (tag.getBoolean("shadow")) f |= FLAG_SHADOW;
            if (tag.getBoolean("see_through")) f |= FLAG_SEE_THROUGH;
            if (tag.getBoolean("default_background")) f |= FLAG_USE_DEFAULT_BACKGROUND;
            if (tag.tags().get("alignment") instanceof String a) {
                if (ALIGNMENTS[1].equals(a)) f |= FLAG_ALIGN_LEFT;
                else if (ALIGNMENTS[2].equals(a)) f |= FLAG_ALIGN_RIGHT;
            }
            setFlags(f);
            if (tag.tags().get("text") instanceof String json) {
                org.domi.furnace.api.text.Text parsed = null;
                boolean failed = false;
                try {
                    parsed = org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json);
                } catch (RuntimeException e) {
                    // 바닐라: 예외면 경고만 하고 기존 글자를 그대로 둔다(fromJson 이 null 이면 빈 글자).
                    failed = true;
                    LOG.warn("Failed to parse display entity text {}", json);
                }
                if (!failed) setText(parsed != null ? parsed : org.domi.furnace.api.text.Text.empty());
            }
        }
    }
}
