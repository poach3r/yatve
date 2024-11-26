package org.poach3r

import io.github.jwharm.javagi.base.GErrorException
import io.github.jwharm.javagi.base.Out
import io.github.jwharm.javagi.gobject.annotations.InstanceInit
import io.github.jwharm.javagi.gobject.types.Types
import org.gnome.gdk.Display
import org.gnome.gdk.Gdk
import org.gnome.gdk.ModifierType
import org.gnome.gio.AsyncReadyCallback
import org.gnome.gio.AsyncResult
import org.gnome.gio.File
import org.gnome.gio.FileCreateFlags
import org.gnome.glib.Type
import org.gnome.gobject.GObject
import org.gnome.gtk.AlertDialog
import org.gnome.gtk.Application
import org.gnome.gtk.ApplicationWindow
import org.gnome.gtk.Button
import org.gnome.gtk.CssProvider
import org.gnome.gtk.EventControllerKey
import org.gnome.gtk.FileDialog
import org.gnome.gtk.Gtk
import org.gnome.gtk.HeaderBar
import org.gnome.gtk.Label
import org.gnome.gtk.ScrolledWindow
import org.gnome.gtk.StyleContext
import org.gnome.gtk.TextIter
import org.gnome.gtk.TextView
import java.lang.foreign.MemorySegment

class EditorWindow(
    address: MemorySegment? = null
): ApplicationWindow(address) {
    private var currentBufferI = 0
    private val buffers = mutableListOf(Buffer())
    private val currentBuffer: Buffer
        get() = buffers[currentBufferI]
    private val inputHandler = InputHandler()
    val textview: TextView = TextView.builder()
        .setMonospace(true)
        .setTopMargin(8)
        .setBottomMargin(8)
        .setLeftMargin(8)
        .setRightMargin(8)
        .setBuffer(buffers[0].textBuffer)
        .build().apply {
            addController(EventControllerKey().apply {
                onKeyPressed(this@EditorWindow::keyPressed)
                //onKeyReleased(this@EditorWindow::keyReleased)
            })
        }

    @InstanceInit
    fun init() {
        loadCSS("src/main/resources/Editor.css")

        child = ScrolledWindow.builder()
            .setChild(textview)
            .setVexpand(true)
            .build()

        updateTitlebar()
    }

    private fun keyPressed(keyval: Int, keycode: Int, state: Set<ModifierType>): Boolean {
        inputHandler.setModifier(state)
        inputHandler.keyPressed(Gdk.keyvalName(keyval)).invoke(this)

        return false
    }

    private fun keyReleased(keyval: Int, keycode: Int, state: Set<ModifierType>): Boolean {
        inputHandler.setModifier(state)
        inputHandler.keyReleased(Gdk.keyvalName(keyval)).invoke(this)

        return false
    }

    /**
     * Invokes `action` unless the buffer is modified in which
     * case it asks to save the changes before invoking `action`.
     */
    private fun whenSure(action: () -> Unit) {
        // no changes, does not prompt
        if(!textview.buffer.modified) {
            action.invoke()
            return
        }

        AlertDialog.builder()
            .setModal(true)
            .setMessage("Save changes?")
            .setDetail("Do you want to save your changes?")
            .setButtons(arrayOf("Cancel", "Discard", "Save"))
            .setCancelButton(0)
            .setDefaultButton(2)
            .build()
            .also {
                it.choose(
                    this, null,
                    AsyncReadyCallback { _: GObject?, result: AsyncResult?, _: MemorySegment? ->
                    try {
                        val button = it.chooseFinish(result)

                        if(button == 0)
                            return@AsyncReadyCallback

                        if(button == 2)
                            save()

                        action.invoke()
                    } catch(_: GErrorException) {}
                })
            }
    }

    fun createBuffer() {
        buffers.add(currentBufferI + 1, Buffer())
        changeBufferTo(buffers.size - 1)
    }

    fun removeBuffer() {
        whenSure {
            if(buffers.size == 1) {
                _clear()
                return@whenSure
            }

            changeBufferBy(-1)
            buffers.removeAt(currentBufferI)
            updateTitlebar()
        }
    }

    fun clear() {
        whenSure(this::_clear)
    }

    /**
     * Clears the editor buffer.
     */
    private fun _clear() {
        currentBuffer.file = null
        textview.buffer.setText("", 0)
        textview.buffer.modified = false
        textview.grabFocus()
    }

    fun open() {
        whenSure(this::_open)
    }

    /**
     * Loads a file and displays it's contents.
     */
    private fun _open() {
        val dialog = FileDialog()
        dialog.open(this, null, AsyncReadyCallback { _: GObject?, result: AsyncResult?, _: MemorySegment? ->
            try {
                currentBuffer.file = dialog.openFinish(result)
            } catch(_: GErrorException) {} // user clicked cancel

            currentBuffer.file?.let {
                try {
                    val contents = Out<ByteArray>()
                    it.loadContents(null, contents, null)
                    textview.buffer.setText(String(contents.get()), contents.get().size)
                    textview.buffer.modified = false
                    textview.grabFocus()
                    updateTitlebar()
                } catch(e: GErrorException) {
                    AlertDialog.builder()
                        .setModal(true)
                        .setMessage("Error reading from file")
                        .setDetail(e.message)
                        .build()
                        .show(this)
                }
            }
        })
    }

    fun save() {
        textview.buffer.modified = false
        whenSure(this@EditorWindow::_save)
    }

    /**
     * Shows a file dialog and then writes the file.
     */
    private fun _save() {
        fun finishSave() {
            try {
                val start = TextIter()
                val end = TextIter()
                textview.buffer.getBounds(start, end)
                val contents = textview.buffer.getText(start, end, false).toByteArray()

                currentBuffer.file!!.replaceContents(contents, "", false, FileCreateFlags.NONE, null, null)
            } catch (e: GErrorException) {
                AlertDialog.builder()
                    .setModal(true)
                    .setMessage("Error writing to file")
                    .setDetail(e.message)
                    .build()
                    .show(this)
            }

            textview.grabFocus()
        }

        // file is already opened
        currentBuffer.file?.let {
            finishSave()
            return
        }

        val dialog = FileDialog()
        dialog.save(this, null, AsyncReadyCallback { _: GObject?, result: AsyncResult?, _: MemorySegment? ->
            try {
                currentBuffer.file = dialog.saveFinish(result)
                currentBuffer.file?.let { finishSave() }

                return@AsyncReadyCallback
            } catch(_: GErrorException) {
                textview.buffer.modified = true
            }
        })
    }

    fun changeBufferBy(i: Int) {
        currentBuffer.textBuffer = textview.buffer

        currentBufferI += i

        if(currentBufferI >= buffers.size)
            currentBufferI = 0

        else if(currentBufferI < 0)
            currentBufferI = buffers.size - 1

        textview.buffer = currentBuffer.textBuffer

        textview.grabFocus()
        updateTitlebar()
    }

    fun changeBufferTo(i: Int) {
        if(i >= buffers.size || i < 0)
            return

        currentBuffer.textBuffer = textview.buffer

        currentBufferI = i

        textview.buffer = currentBuffer.textBuffer

        textview.grabFocus()
        updateTitlebar()
    }

    private fun loadCSS(path: String) {
        StyleContext.addProviderForDisplay(
            Display.getDefault(),
            CssProvider().also {
                it.loadFromPath(path)
            },
            Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
        )
    }

    private fun updateTitlebar() {
        titlebar = HeaderBar().apply {
            titleWidget = Label()
            showTitleButtons = true
            buffers.forEachIndexed { i, buffer ->
                packStart(Button.withLabel(buffer.file?.basename ?: "Untitled").apply {
                    if(i == currentBufferI)
                        addCssClass("CurrentBuffer")
                    
                    onClicked {
                        changeBufferTo(i)
                    }
                })
            }

            packStart(Button.fromIconName("list-add-symbolic").apply {
                this.hasFrame = false

                onClicked {
                    createBuffer()
                }
            })
        }
    }

    companion object {
        private val gtype: Type = Types.register<EditorWindow, ObjectClass>(EditorWindow::class.java)

        fun create(application: Application): EditorWindow {
            return GObject.newInstance<EditorWindow>(gtype).apply {
                this.application = application
            }.also {
                it.present()
                it.textview.grabFocus()
            }
        }
    }
}