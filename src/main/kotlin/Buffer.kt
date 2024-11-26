package org.poach3r

import org.gnome.gio.File
import org.gnome.gtk.TextBuffer

data class Buffer(
    var textBuffer: TextBuffer = TextBuffer(),
    var file: File? = null
)