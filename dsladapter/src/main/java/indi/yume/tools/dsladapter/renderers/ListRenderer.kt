package indi.yume.tools.dsladapter.renderers

import androidx.recyclerview.widget.RecyclerView
import indi.yume.tools.dsladapter.*
import indi.yume.tools.dsladapter.datatype.*
import indi.yume.tools.dsladapter.typeclass.BaseRenderer
import indi.yume.tools.dsladapter.typeclass.ViewData

/**
 * Created by xuemaotang on 2017/11/16.
 */

class ListRenderer<T, I, IV : ViewData<I>, UP : Updatable<I, IV>>(
        val converter: (T) -> List<I>,
        val demapper: (oldData: T, newMapData: List<I>) -> T,
        val subs: BaseRenderer<I, IV, UP>,
        val keyGetter: KeyGetter<I>?,
        val itemIsSingle: Boolean = false
) : BaseRenderer<T, ListViewData<T, I, IV>, ListUpdater<T, I, IV, UP>>() {
    override val updater: ListUpdater<T, I, IV, UP> = ListUpdater(this, subs.updater)

    override fun getData(content: T): ListViewData<T, I, IV> {
        val oriData = converter(content)
        return ListViewData(content, oriData, oriData.map { subs.getData(it) })
    }

    override fun getItemId(data: ListViewData<T, I, IV>, index: Int): Long {
        val (resolvedRepositoryIndex, resolvedItemIndex) = resolveIndices(index, data.endsPoint)
        return subs.getItemId(data[resolvedRepositoryIndex], resolvedItemIndex)
    }

    override fun getLayoutResId(data: ListViewData<T, I, IV>, index: Int): Int {
        val (resolvedRepositoryIndex, resolvedItemIndex) = resolveIndices(index, data.endsPoint)
        return subs.getLayoutResId(data[resolvedRepositoryIndex], resolvedItemIndex)
    }

    override fun bind(data: ListViewData<T, I, IV>, index: Int, holder: RecyclerView.ViewHolder) {
        val (resolvedRepositoryIndex, resolvedItemIndex) = resolveIndices(index, data.endsPoint)
        subs.bind(data[resolvedRepositoryIndex], resolvedItemIndex, holder)
    }

    override fun recycle(holder: RecyclerView.ViewHolder) {
        subs.recycle(holder)
    }
}

data class ListViewData<T, I, VD : ViewData<I>>(override val originData: T, val data: List<I>, val list: List<VD>) : ViewData<T>, List<VD> by list {
    init {
        assert(list.size == data.size)
    }

    val endsPoint: IntArray = list.getEndsPoints()

    override val count: Int
        get() = endsPoint.getEndPoint()
}

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T, I, IV : ViewData<I>, UP : Updatable<I, IV>> BaseRenderer<T, ListViewData<T, I, IV>, ListUpdater<T, I, IV, UP>>.fix(): ListRenderer<T, I, IV, UP> =
        this as ListRenderer<T, I, IV, UP>

class ListUpdater<T, I, IV : ViewData<I>, U : Updatable<I, IV>>(
        val renderer: ListRenderer<T, I, IV, U>,
        val subsUpdater: U) : ListChangeable<T, ListViewData<T, I, IV>, I> {
    fun update(data: T, payload: Any? = null): ActionU<ListViewData<T, I, IV>> = { oldVD ->
        val newVD = renderer.getData(data)

        if (renderer.keyGetter != null)
            updateAuto(data, diffUtilCheck(renderer.keyGetter))(oldVD)
        else
            updateVD(oldVD, newVD, payload) to newVD
    }

    fun reduce(f: (oldData: T) -> ChangedData<T>): ActionU<ListViewData<T, I, IV>> = { oldVD ->
        val (newData, payload) = f(oldVD.originData)
        update(newData, payload)(oldVD)
    }

    fun updateDiffUtil(data: T, keyGetter: KeyGetter<I> = renderer.keyGetter ?: keyMe()): ActionU<ListViewData<T, I, IV>> =
            { oldVD ->
                val newVD = renderer.getData(data)
                val actionList = diffUtil(oldVD.data, newVD.data, keyGetter)

                val realActions = if (renderer.itemIsSingle
                        && oldVD.count == oldVD.list.size && newVD.count == newVD.list.size) {
                    actionList.toUpdateActions()
                } else {
                    actionList.toActionsWithRealIndex(oldVD.list, newVD.list)
                }

                ActionComposite(0, realActions) to newVD
            }

    fun updateAuto(data: T,
                   f: (oldSubs: List<I>, newSubs: List<I>) -> List<UpdateActions> = diffUtilCheck(renderer.keyGetter ?: keyMe())): ActionU<ListViewData<T, I, IV>> =
            { oldVD ->
                val newVD = renderer.getData(data)
                val actionList = f(oldVD.data, newVD.data)

                val realActions = if (renderer.itemIsSingle
                        && oldVD.count == oldVD.list.size && newVD.count == newVD.list.size) {
                    actionList
                } else {
                    actionList.toFakeActions<IV>()
                            .toActionsWithRealIndex(oldVD.list, newVD.list) { it.count }
                }

                ActionComposite(0, realActions) to newVD
            }

    fun subs(pos: Int, updater: U.() -> ActionU<IV>): ActionU<ListViewData<T, I, IV>> {
        val subAction = subsUpdater.updater()

        return subsAct@{ oldVD ->
            val subVD = oldVD.list.getOrNull(pos) ?: return@subsAct EmptyAction to oldVD

            val (subActions, subNewVD) = subAction(subVD)
            val newData = oldVD.data.toMutableList().apply { set(pos, subNewVD.originData) }.toList()
            val newVD = oldVD.list.toMutableList().apply { set(pos, subNewVD) }.toList()

            val newOriData = renderer.demapper(oldVD.originData, newData)

            val realPos = oldVD.endsPoint.getTargetStartPoint(pos)

            ActionComposite(realPos, listOf(subActions)) to ListViewData(newOriData, newData, newVD)
        }
    }

    override fun insert(pos: Int, insertedItems: List<I>): ActionU<ListViewData<T, I, IV>> =
            result@{ oldVD ->
                if (pos !in 0 until oldVD.data.size)
                    return@result EmptyAction to oldVD

                val newData = oldVD.data.toMutableList().apply {
                    addAll(pos, insertedItems)
                }.toList()
                val insertVD = insertedItems.map { renderer.subs.getData(it) }
                val newVD = oldVD.list.toMutableList().apply {
                    addAll(pos, insertVD)
                }.toList()
                val newOriData = renderer.demapper(oldVD.originData, newData)

                val realPos = oldVD.endsPoint.getTargetStartPoint(pos)
                val realCount = insertVD.sumBy { it.count }

                OnInserted(realPos, realCount) to ListViewData(newOriData, newData, newVD)
            }

    override fun remove(pos: Int, count: Int): ActionU<ListViewData<T, I, IV>> =
            result@{ oldVD ->
                if (pos !in 0 until oldVD.data.size
                        || pos + count - 1 !in 0 until oldVD.data.size)
                    return@result EmptyAction to oldVD

                val newData = oldVD.data.toMutableList().apply {
                    for (i in 1..count) removeAt(pos)
                }.toList()
                val newVD = oldVD.list.toMutableList().apply {
                    for (i in 1..count) removeAt(pos)
                }.toList()
                val newOriData = renderer.demapper(oldVD.originData, newData)

                val realPos = oldVD.endsPoint.getTargetStartPoint(pos)
                val realCount = oldVD.list.subList(pos, pos + count).sumBy { it.count }

                OnRemoved(realPos, realCount) to ListViewData(newOriData, newData, newVD)
            }

    override fun move(fromPosition: Int, toPosition: Int): ActionU<ListViewData<T, I, IV>> =
            result@{ oldVD ->
                val emptyResult = EmptyAction to oldVD
                if (fromPosition !in 0 until oldVD.data.size
                        || toPosition !in 0 until oldVD.data.size)
                    return@result emptyResult

                if (fromPosition == toPosition)
                    return@result OnChanged(fromPosition, 0, null) to oldVD

                val targetVD = oldVD.list.get(fromPosition)
                val newData = oldVD.data.move(fromPosition, toPosition) ?: return@result emptyResult
                val newVD = oldVD.list.move(fromPosition, toPosition) ?: return@result emptyResult
                val newOriData = renderer.demapper(oldVD.originData, newData)

                val realFromPos = oldVD.endsPoint.getTargetStartPoint(fromPosition)
                val realToPos = oldVD.endsPoint.getTargetStartPoint(toPosition)
                val realCount = targetVD.count

                (if (realCount == 1)
                    OnMoved(realFromPos, realToPos)
                else
                    ActionComposite(0, listOf(
                            OnRemoved(realFromPos, realCount),
                            OnInserted(realToPos, realCount)
                    ))) to ListViewData(newOriData, newData, newVD)
            }

    override fun change(pos: Int, newItems: List<I>, payload: Any?): ActionU<ListViewData<T, I, IV>> =
            result@{ oldVD ->
                val count = newItems.size
                if (pos !in 0 until oldVD.data.size
                        || pos + count - 1 !in 0 until oldVD.data.size)
                    return@result EmptyAction to oldVD

                val oldSubData = oldVD.list.subList(pos, pos + count)
                val newSubVD = newItems.map { renderer.subs.getData(it) }

                val newData = oldVD.data.toMutableList().apply {
                    for ((index, i) in newItems.withIndex()) set(pos + index, i)
                }.toList()
                val newVD = oldVD.list.toMutableList().apply {
                    for ((index, i) in newItems.withIndex()) set(pos + index, renderer.subs.getData(i))
                }.toList()
                val newOriData = renderer.demapper(oldVD.originData, newData)

                val realOldCount = oldSubData.sumBy { it.count }
                val realNewCount = newSubVD.sumBy { it.count }

                val realPos = oldVD.endsPoint.getTargetStartPoint(pos)

                (if (realOldCount == realNewCount)
                    OnChanged(pos, count, payload)
                else
                    ActionComposite(0, listOf(
                            OnRemoved(realPos, realOldCount),
                            OnInserted(realPos, realNewCount)
                    ))) to ListViewData(newOriData, newData, newVD)
            }
}