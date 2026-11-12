package org.bukkit;

import java.util.HashMap;
import java.util.Map;

/** 소리 블록 음(0~24 = F#0~F#2, note_block note 속성). */
public class Note {

    /** 음이름. 번호는 한 옥타브(12) 안의 자리 — G 가 1, F# 이 0(12)이다. */
    public enum Tone {
        G(0x1, true), A(0x3, true), B(0x5, false), C(0x6, true), D(0x8, true), E(0xA, false), F(0xB, true);

        /** 한 옥타브의 반음 수. */
        public static final byte TONES_COUNT = 12;

        private static final Map<Byte, Tone> BY_DATA = new HashMap<>();

        static {
            for (Tone t : values()) {
                int id = t.id % TONES_COUNT;
                BY_DATA.put((byte) id, t);
                if (t.sharpable) BY_DATA.put((byte) ((id + 1) % TONES_COUNT), t);
            }
        }

        private final boolean sharpable;
        private final byte id;

        Tone(int id, boolean sharpable) {
            this.id = (byte) (id % TONES_COUNT);
            this.sharpable = sharpable;
        }

        /** @deprecated 반올림 없는 자리 번호. */
        @Deprecated
        public byte getId() { return getId(false); }

        /** @deprecated 반올림(#)이면 한 칸 위 자리(옥타브를 넘으면 0). 올릴 수 없는 음(B·E)은 올리지 않는다. */
        @Deprecated
        public byte getId(boolean sharped) {
            byte i = (byte) (sharped && sharpable ? id + 1 : id);
            return (byte) (i % TONES_COUNT);
        }

        /** #을 붙일 수 있는 음인가(B·E 는 아니다). */
        public boolean isSharpable() { return sharpable; }

        /** @deprecated 그 자리 번호가 이 음의 # 인가. 이 음의 자리가 아니면 IllegalArgumentException. */
        @Deprecated
        public boolean isSharped(byte id) {
            if (id == getId(false)) return false;
            if (id == getId(true)) return true;
            throw new IllegalArgumentException("The id isn't matching to the tone.");
        }

        /** @deprecated 자리 번호의 음(없으면 null). */
        @Deprecated
        public static Tone getById(byte id) { return BY_DATA.get(id); }
    }

    private final byte note;

    public Note(int note) {
        if (note < 0 || note > 24) throw new IllegalArgumentException("The note value has to be between 0 and 24.");
        this.note = (byte) note;
    }

    /** 옥타브(0~2)·음·# 으로. # 을 못 붙이는 음(B·E)의 # 은 다음 음으로 바뀐다. F#0~F#2 밖이면 IllegalArgumentException. */
    public Note(int octave, Tone tone, boolean sharped) {
        if (sharped && !tone.isSharpable()) {
            tone = Tone.values()[tone.ordinal() + 1];
            sharped = false;
        }
        if (octave < 0 || octave > 2 || (octave == 2 && !(tone == Tone.F && sharped))) {
            throw new IllegalArgumentException("Tone and octave have to be between F#0 and F#2");
        }
        this.note = (byte) (octave * Tone.TONES_COUNT + tone.getId(sharped));
    }

    /** 내림음(♭) — 바로 아래 음의 # 이나 제음. 옥타브 2 는 안 된다. */
    public static Note flat(int octave, Tone tone) {
        if (octave == 2) throw new IllegalArgumentException("Octave cannot be 2 for flats");
        tone = tone == Tone.G ? Tone.F : Tone.values()[tone.ordinal() - 1];
        return new Note(octave, tone, tone.isSharpable());
    }

    public static Note sharp(int octave, Tone tone) { return new Note(octave, tone, true); }

    /** 제음. 옥타브 2 는 안 된다. */
    public static Note natural(int octave, Tone tone) {
        if (octave == 2) throw new IllegalArgumentException("Octave cannot be 2 for naturals");
        return new Note(octave, tone, false);
    }

    /** 반음 위(가장 높은 음이면 IllegalArgumentException). */
    public Note sharped() {
        if (note >= 24) throw new IllegalArgumentException("This note cannot be sharped because it is the highest known note!");
        return new Note(note + 1);
    }

    /** 반음 아래(가장 낮은 음이면 IllegalArgumentException). */
    public Note flattened() {
        if (note <= 0) throw new IllegalArgumentException("This note cannot be flattened because it is the lowest known note!");
        return new Note(note - 1);
    }

    /** @deprecated 0~24. */
    @Deprecated
    public byte getId() { return note; }

    public int getOctave() { return note / Tone.TONES_COUNT; }

    private byte getToneByte() { return (byte) (note % Tone.TONES_COUNT); }

    public Tone getTone() { return Tone.getById(getToneByte()); }

    public boolean isSharped() {
        byte b = getToneByte();
        return Tone.getById(b).isSharped(b);
    }

    /** 소리 높이(바닐라 NoteBlock.getPitchFromNote: 2^((음-12)/12)). */
    public float getPitch() { return (float) Math.pow(2.0, (note - 12) / 12.0); }

    @Override
    public boolean equals(Object o) { return o instanceof Note n && n.note == note; }

    @Override
    public int hashCode() { return 31 + note; }

    @Override
    public String toString() { return "Note{" + getTone().toString() + (isSharped() ? "#" : "") + "}"; }
}
