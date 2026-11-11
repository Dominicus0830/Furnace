package org.bukkit.event.block;

import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 소리 블록이 울린다. Furnace 는 아직 알리지 않는다. */
public class NotePlayEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private Instrument instrument;
    private Note note;

    public NotePlayEvent(Block block, Instrument instrument, Note note) {
        super(block);
        this.instrument = instrument;
        this.note = note;
    }

    public Instrument getInstrument() { return instrument; }

    public Note getNote() { return note; }

    @Deprecated
    public void setInstrument(Instrument instrument) { if (instrument != null) this.instrument = instrument; }

    @Deprecated
    public void setNote(Note note) { if (note != null) this.note = note; }
}
