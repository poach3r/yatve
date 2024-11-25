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
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.CssProvider
import org.gnome.gtk.EventControllerKey
import org.gnome.gtk.FileDialog
import org.gnome.gtk.Gtk
import org.gnome.gtk.HeaderBar
import org.gnome.gtk.Orientation
import org.gnome.gtk.ScrolledWindow
import org.gnome.gtk.StyleContext
import org.gnome.gtk.TextBuffer
import org.gnome.gtk.TextIter
import org.gnome.gtk.TextView
import java.lang.foreign.MemorySegment

class EditorWindow(
    address: MemorySegment? = null
): ApplicationWindow(address) {
    private var _currentBuffer = 0
    val currentBuffer
        get() = _currentBuffer
    private val buffers = mutableListOf(TextBuffer())
    private var file: File? = null
    private val inputHandler = InputHandler()
    val textview: TextView = TextView.builder()
        .setMonospace(true)
        .setTopMargin(8)
        .setBottomMargin(8)
        .setLeftMargin(8)
        .setRightMargin(8)
        .setBuffer(buffers[0])
        .build().apply {
            buffer.onModifiedChanged {
                updateWindowTitle()
            }

            addController(EventControllerKey().apply {
                onKeyPressed(this@EditorWindow::keyPressed)
                onKeyReleased(this@EditorWindow::keyReleased)
            })
        }

    @InstanceInit
    fun init() {
        loadCSS("src/main/resources/Editor.css")

        child = ScrolledWindow.builder()
            .setChild(textview)
            .setVexpand(true)
            .build()

        titlebar = HeaderBar().apply {
            showTitleButtons = false

            // tab buttons
            packStart(Box(Orientation.HORIZONTAL, 0).apply {
                append(Button.fromIconName("go-previous-symbolic").apply {
                    addCssClass("LeftButton")
                    onClicked {
                        changeBufferBy(-1)
                    }
                })

                append(Button.fromIconName("go-next-symbolic").apply {
                    addCssClass("RightButton")
                    onClicked {
                        changeBufferBy(1)
                    }
                })
            })

            packStart(Button.fromIconName("list-add-symbolic").apply {
                onClicked(this@EditorWindow::createBuffer)
            })

            packStart(Button.fromIconName("list-remove-symbolic").apply {
                onClicked(this@EditorWindow::removeBuffer)
            })

            // document buttons
            packEnd(Button.fromIconName("document-open-symbolic").apply {
                onClicked(this@EditorWindow::open)
            })

            packEnd(Button.fromIconName("document-save-symbolic").apply {
                onClicked(this@EditorWindow::save)
            })

            onCloseRequest(CloseRequestCallback {
                whenSure(this@EditorWindow::destroy)
                true
            })

            updateWindowTitle()
        }
    }

    private fun keyPressed(keyval: Int, keycode: Int, state: Set<ModifierType>): Boolean {
        inputHandler.keyPressed(Gdk.keyvalName(keyval)).invoke(this)

        return false
    }

    private fun keyReleased(keyval: Int, keycode: Int, state: Set<ModifierType>): Boolean {
        inputHandler.keyReleased(Gdk.keyvalName(keyval)).invoke(this)

        return false
    }

    private fun updateWindowTitle() {
        title = (if(textview.buffer.modified) "• " else "") +
                (if(file == null) "Unnamed" else file!!.basename)
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
        buffers.add(currentBuffer + 1, TextBuffer())
        changeBufferBy(1)
    }

    fun removeBuffer() {
        whenSure {
            if(buffers.size == 1) {
                _clear()
                return@whenSure
            }

            changeBufferBy(-1)
            buffers.removeAt(currentBuffer)
        }
    }

    fun clear() {
        whenSure(this::_clear)
    }

    /**
     * Clears the editor buffer.
     */
    private fun _clear() {
        file = null
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
                file = dialog.openFinish(result)
            } catch(_: GErrorException) {} // user clicked cancel

            file?.let {
                try {
                    val contents = Out<ByteArray>()
                    it.loadContents(null, contents, null)
                    textview.buffer.setText(String(contents.get()), contents.get().size)
                    textview.buffer.modified = false
                    textview.grabFocus()
                } catch(e: GErrorException) {
                    AlertDialog.builder()
                        .setModal(true)
                        .setMessage("Error reading from file")
                        .setDetail(e.message)
                        .build()
                        .show(this)
                }
            }

            return@AsyncReadyCallback
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

                file!!.replaceContents(contents, "", false, FileCreateFlags.NONE, null, null)
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
        file?.let {
            finishSave()
            return
        }

        val dialog = FileDialog()
        dialog.save(this, null, AsyncReadyCallback { _: GObject?, result: AsyncResult?, _: MemorySegment? ->
            try {
                file = dialog.saveFinish(result)
                file?.let { finishSave() }

                return@AsyncReadyCallback
            } catch(_: GErrorException) {
                textview.buffer.modified = true
            }
        })
    }

    fun changeBufferBy(i: Int) {
        buffers[currentBuffer] = textview.buffer

        _currentBuffer += i

        if(currentBuffer >= buffers.size)
            _currentBuffer = 0

        else if(currentBuffer < 0)
            _currentBuffer = buffers.size - 1

        textview.buffer = buffers[currentBuffer]

        textview.grabFocus()
    }

    fun changeBufferTo(i: Int) {
        if(i >= buffers.size || i < 0)
            return

        buffers[currentBuffer] = textview.buffer

        _currentBuffer = i

        textview.buffer = buffers[currentBuffer]

        textview.grabFocus()
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