package org.poach3r

class InputHandler {
    private var ctrlHeld = false

    fun keyPressed(keyname: String): (editorWindow: EditorWindow) -> Unit {
        return when(keyname) {
            "Control_L", "Control_R" -> normFun { ctrlHeld = true }

            "n" -> ctrlHeldFun { it.createBuffer() }
            "r" -> ctrlHeldFun { it.removeBuffer() }
            "o" -> ctrlHeldFun { it.open() }
            "s" -> ctrlHeldFun { it.save() }

            "1" -> ctrlHeldFun { it.changeBufferTo(0) }
            "2" -> ctrlHeldFun { it.changeBufferTo(1) }
            "3" -> ctrlHeldFun { it.changeBufferTo(2) }
            "4" -> ctrlHeldFun { it.changeBufferTo(3) }
            "5" -> ctrlHeldFun { it.changeBufferTo(4) }
            "6" -> ctrlHeldFun { it.changeBufferTo(5) }
            "7" -> ctrlHeldFun { it.changeBufferTo(6) }
            "8" -> ctrlHeldFun { it.changeBufferTo(7) }
            "9" -> ctrlHeldFun { it.changeBufferTo(8) }

            else -> noneFun()
        }
    }

    fun keyReleased(keyname: String): (editorWindow: EditorWindow) -> Unit {
        return when(keyname) {
            "Control_L", "Control_R" -> normFun {
                ctrlHeld = false
            }

            else -> noneFun()
        }
    }

    private fun normFun(unit: (editorWindow: EditorWindow) -> Unit): (editorWindow: EditorWindow) -> Unit {
        return unit
    }

    private fun ctrlHeldFun(unit: (editorWindow: EditorWindow) -> Unit): (editorWindow: EditorWindow) -> Unit {
        if(ctrlHeld) {
            return unit
        }

        return {}
    }

    private fun noneFun(): (editorWindow: EditorWindow) -> Unit {
        return {}
    }
}