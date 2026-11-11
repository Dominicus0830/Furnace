package org.bukkit;

/** 소리 블록 음(0~24, note_block note 속성). 음이름(Tone)은 아직 없다. */
public class Note {

    private final byte note;

    public Note(int note) {
        if (note < 0 || note > 24) throw new IllegalArgumentException("The note value has to be between 0 and 24.");
        this.note = (byte) note;
    }

    public byte getId() { return note; }

    public int getOctave() { return note / 12; }

    @Override
    public boolean equals(Object o) { return o instanceof Note n && n.note == note; }

    @Override
    public int hashCode() { return note; }

    @Override
    public String toString() { return "Note{" + note + "}"; }
}
