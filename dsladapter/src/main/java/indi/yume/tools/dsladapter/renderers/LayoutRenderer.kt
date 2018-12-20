package indi.yume.tools.dsladapter.renderers

import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.View
import indi.yume.tools.dsladapter.Action
import indi.yume.tools.dsladapter.Updatable
import indi.yume.tools.dsladapter.datatype.OnChanged
import indi.yume.tools.dsladapter.datatype.OnInserted
import indi.yume.tools.dsladapter.datatype.OnRemoved
import indi.yume.tools.dsladapter.datatype.UpdateActions
import indi.yume.tools.dsladapter.typeclass.BaseRenderer
import indi.yume.tools.dsladapter.typeclass.ViewData
import indi.yume.tools.dsladapter.updateVD

class LayoutRenderer<T>(
        @LayoutRes val layout: Int,
        val count: Int = 1,
        val binder: (View, T, Int) -> Unit = { _, _, _ -> },
        val recycleFun: (View) -> Unit = { },
        val stableIdForItem: (T, Int) -> Long = { _, _ -> -1L },
        val keyGetter: (T) -> Any? = { it }
) : BaseRenderer<T, LayoutViewData<T>, LayoutUpdater<T>>() {
    override val updater: LayoutUpdater<T> = LayoutUpdater(this)

    override fun getData(content: T): LayoutViewData<T> = LayoutViewData(count, content)

    override fun getItemId(data: LayoutViewData<T>, index: Int): Long = stableIdForItem(data.originData, index)

    override fun getLayoutResId(data: LayoutViewData<T>, index: Int): Int = layout

    override fun bind(data: LayoutViewData<T>, index: Int, holder: RecyclerView.ViewHolder) {
        binder(holder.itemView, data.originData, index)
    }

    override fun recycle(holder: RecyclerView.ViewHolder) {
        recycleFun(holder.itemView)
    }

    companion object
}

data class LayoutViewData<T>(override val count: Int, override val originData: T) : ViewData<T>


@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T> BaseRenderer<T, LayoutViewData<T>, LayoutUpdater<T>>.fix(): LayoutRenderer<T> =
        this as LayoutRenderer<T>

class LayoutUpdater<T>(val renderer: LayoutRenderer<T>) : Updatable<T, LayoutViewData<T>> {
    fun update(newData: T, payload: Any? = null): Action<LayoutViewData<T>> {
        val newVD = renderer.getData(newData)

        return { oldVD -> updateVD(oldVD, newVD, payload) to newVD }
    }
}
