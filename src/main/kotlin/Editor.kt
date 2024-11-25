package org.poach3r

import io.github.jwharm.javagi.gobject.types.Types
import org.gnome.gio.ApplicationFlags
import org.gnome.glib.Type
import org.gnome.gtk.Application
import java.lang.foreign.MemorySegment

class Editor(
    address: MemorySegment? = null,
): Application(address) {
    override fun activate() {
        this.activeWindow ?: EditorWindow.create(this).apply {
            setDefaultSize(600, 400)
        }.also {
            it.present()
        }
    }

    companion object {
        val gtype: Type = Types.register<Editor, ObjectClass>(Editor::class.java)

        fun create(): Editor {
            return newInstance<Editor>(
                gtype,
                "application-id", "org.poacher.GtkEditor",
                "flags", ApplicationFlags.DEFAULT_FLAGS,
                null
            )
        }
    }
}