package indi.yume.tools.dsladapter

import android.support.annotation.CheckResult
import android.support.annotation.MainThread
import android.support.v7.widget.RecyclerView
import android.util.SparseIntArray
import android.view.ViewGroup
import arrow.Kind
import indi.yume.tools.dsladapter.datatype.*
import indi.yume.tools.dsladapter.renderers.*
import indi.yume.tools.dsladapter.typeclass.*
import java.util.*


class RendererAdapter<T, VD : ViewData<T>, UP : Updatable<T, VD>>(
        val initData: T,
        val renderer: Renderer<T, VD, UP>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val dataLock = Any()

    private var adapterViewData: VD = renderer.getData(initData)

    fun getOriData(): T = adapterViewData.originData

    fun getViewData(): VD = adapterViewData

    @MainThread
    fun setData(newData: T) {
        adapterViewData = renderer.getData(initData)
        notifyDataSetChanged()
    }

    @MainThread
    fun reduceData(f: (T) -> T) {
        val newData = f(adapterViewData.originData)
        setData(newData)
    }

    fun getUpdater(): UP = renderer.updater

    @CheckResult
    fun update(f: UP.() -> Action<VD>): UpdateData<T, VD> {
        val data = adapterViewData
        val (actions, newVD) = getUpdater().f()(data)

        return UpdateData(data, newVD, listOf(actions))
    }

    fun updateNow(f: UP.() -> Action<VD>) {
        update(f).dispatchUpdatesTo(this)
    }

    @MainThread
    fun updateData(actions: Action<VD>) {
        val data = adapterViewData
        val (update, newVD) = actions(data)

        synchronized(dataLock) {
            adapterViewData = newVD
        }

        listOf(update).dispatchUpdatesTo(this)
    }

    @MainThread
    fun updateData(updates: UpdateData<T, VD>) {
        val dataHasChanged = synchronized(dataLock) {
            if (adapterViewData != updates.oldData) {
                true
            } else {
                adapterViewData = updates.newData
                false
            }
        }

        if (dataHasChanged)
            notifyDataSetChanged()
        else
            updates.actions.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = adapterViewData.count

    override fun getItemViewType(position: Int): Int =
            renderer.getItemViewType(adapterViewData, position)

    override fun getItemId(position: Int): Long =
            renderer.getItemId(adapterViewData, position)

    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): RecyclerView.ViewHolder =
            renderer.onCreateViewHolder(parent, viewType)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
            renderer.bind(adapterViewData, position, holder)

    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean {
        renderer.recycle(holder)
        return super.onFailedToRecycleView(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        renderer.recycle(holder)
    }

    companion object {
        fun <T, VD : ViewData<T>, UP : Updatable<T, VD>, BR : BaseRenderer<T, VD, UP>>
                singleRenderer(initData: T, renderer: BR): RendererAdapter<T, VD, UP> =
                RendererAdapter(initData, renderer)

        fun <DL : HListK<ForIdT, DL>, IL : HListK<ForComposeItem, IL>, VDL : HListK<ForComposeItemData, VDL>>
                multiple(initData: DL,
                         f: ComposeBuilder<HNilK<ForIdT>, HNilK<ForComposeItem>, HNilK<ForComposeItemData>>.() -> ComposeBuilder<DL, IL, VDL>)
                : RendererAdapter<DL, ComposeViewData<DL, VDL>, ComposeUpdater<DL, IL, VDL>> =
                RendererAdapter(initData, ComposeRenderer.startBuild.f().build())

        fun multipleBuild(): AdapterBuilder<HNilK<ForIdT>, HNilK<ForComposeItem>, HNilK<ForComposeItemData>> =
                AdapterBuilder(HListK.nil(), ComposeRenderer.startBuild)
    }
}


data class UpdateData<T, VD : ViewData<T>>(val oldData: VD,
                                           val newData: VD,
                                           val actions: List<UpdateActions>) {
    fun <UP : Updatable<T, VD>> dispatchUpdatesTo(adapter: RendererAdapter<T, VD, UP>) =
            adapter.updateData(this)
}


class AdapterBuilder<DL : HListK<ForIdT, DL>, IL : HListK<ForComposeItem, IL>, VDL : HListK<ForComposeItemData, VDL>>(
        val initSumData: DL,
        val composeBuilder: ComposeBuilder<DL, IL, VDL>
) {
    fun <T, VD : ViewData<T>, UP : Updatable<T, VD>, BR : BaseRenderer<T, VD, UP>>
            add(initData: T, renderer: BR)
            : AdapterBuilder<HConsK<ForIdT, T, DL>, HConsK<ForComposeItem, Pair<T, BR>, IL>, HConsK<ForComposeItemData, Pair<T, BR>, VDL>> =
            AdapterBuilder(initSumData.extend(IdT(initData)), composeBuilder.add(renderer))

    fun <VD : ViewData<Unit>, UP : Updatable<Unit, VD>, BR : BaseRenderer<Unit, VD, UP>>
            add(renderer: BR)
            : AdapterBuilder<HConsK<ForIdT, Unit, DL>, HConsK<ForComposeItem, Pair<Unit, BR>, IL>, HConsK<ForComposeItemData, Pair<Unit, BR>, VDL>> =
            AdapterBuilder(initSumData.extend(IdT(Unit)), composeBuilder.add(renderer))

    fun build(): RendererAdapter<DL, ComposeViewData<DL, VDL>, ComposeUpdater<DL, IL, VDL>> =
            RendererAdapter(initSumData, composeBuilder.build())
}