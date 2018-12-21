package indi.yume.tools.dsladapter.datatype

import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import java.util.*


sealed class UpdateActions

object EmptyAction : UpdateActions()

data class OnInserted(val pos: Int, val count: Int) : UpdateActions()

data class OnRemoved(val pos: Int, val count: Int) : UpdateActions()

data class OnMoved(val fromPosition: Int, val toPosition: Int) : UpdateActions()

data class OnChanged(val pos: Int, val count: Int, val payload: Any?) : UpdateActions()

data class ActionComposite(val offset: Int, val actions: List<UpdateActions>) : UpdateActions()

fun DiffUtil.DiffResult.toUpdateActions(): List<UpdateActions> {
    val recorder = LinkedList<UpdateActions>()
    dispatchUpdatesTo(object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            recorder += OnInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            recorder += OnRemoved(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            recorder += OnMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            recorder += OnChanged(position, count, payload)
        }
    })

    return recorder
}

fun List<UpdateActions>.dispatchUpdatesTo(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
    dispatchUpdatesTo(AdapterListUpdateCallback(adapter))
}

fun List<UpdateActions>.dispatchUpdatesTo(callback: ListUpdateCallback) {
    for (act in this)
        dispatch(0, act, callback)
}

fun dispatch(offset: Int, action: UpdateActions, callback: ListUpdateCallback) {
    when (action) {
        is EmptyAction -> {}
        is OnInserted -> callback.onInserted(offset + action.pos, action.count)
        is OnRemoved -> callback.onRemoved(offset + action.pos, action.count)
        is OnMoved -> callback.onMoved(offset + action.fromPosition, offset + action.toPosition)
        is OnChanged -> callback.onChanged(offset + action.pos, action.count, action.payload)
        is ActionComposite -> {
            for(subAct in action.actions)
                dispatch(offset + action.offset, subAct, callback)
        }
    }
}