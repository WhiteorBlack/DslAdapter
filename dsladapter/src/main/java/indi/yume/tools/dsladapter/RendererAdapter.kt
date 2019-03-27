package indi.yume.tools.dsladapter

import android.view.ViewGroup
import androidx.annotation.CheckResult
import androidx.annotation.MainThread
import androidx.recyclerview.widget.RecyclerView
import indi.yume.tools.dsladapter.datatype.*
import indi.yume.tools.dsladapter.renderers.*
import indi.yume.tools.dsladapter.typeclass.BaseRenderer
import indi.yume.tools.dsladapter.typeclass.Renderer
import indi.yume.tools.dsladapter.typeclass.ViewData


class RendererAdapter<T, VD : ViewData<T>, BR : BaseRenderer<T, VD>>(
        val initData: T,
        val renderer: BR
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val dataLock = Any()

    private var adapterViewData: VD = renderer.getData(initData)

    fun getOriData(): T = adapterViewData.originData

    fun getViewData(): VD = adapterViewData

    @MainThread
    fun setData(newData: T) {
        adapterViewData = renderer.getData(newData)
        notifyDataSetChanged()
    }

    @MainThread
    fun reduceData(f: (T) -> T) {
        val newData = f(adapterViewData.originData)
        setData(newData)
    }

    @MainThread
    fun updateData(actions: ActionU<VD>) {
        val data = adapterViewData
        val (update, newVD) = actions(data)

        synchronized(dataLock) {
            adapterViewData = newVD
        }

        listOf(update).filterUselessAction().dispatchUpdatesTo(this)
    }

    @MainThread
    fun updateData(updates: UpdateResult<T, VD>) {
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
            updates.actions.filterUselessAction().dispatchUpdatesTo(this)
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
        fun <T, VD : ViewData<T>, BR : BaseRenderer<T, VD>>
                singleRenderer(initData: T, renderer: BR): RendererAdapter<T, VD, BR> =
                RendererAdapter(initData, renderer)

        fun <DL : HListK<ForIdT, DL>, IL : HListK<ForComposeItem, IL>, VDL : HListK<ForComposeItemData, VDL>>
                multiple(initData: DL,
                         f: ComposeBuilder<HNilK<ForIdT>, HNilK<ForComposeItem>, HNilK<ForComposeItemData>>.() -> ComposeBuilder<DL, IL, VDL>)
                : RendererAdapter<DL, ComposeViewData<DL, VDL>, ComposeRenderer<DL, IL, VDL>> =
                RendererAdapter(initData, ComposeRenderer.startBuild.f().build())

        fun multipleBuild(): AdapterBuilder<HNilK<ForIdT>, HNilK<ForComposeItem>, HNilK<ForComposeItemData>> =
                AdapterBuilder(HListK.nil(), ComposeRenderer.startBuild)
    }
}


data class UpdateResult<T, VD : ViewData<T>>(val oldData: VD,
                                             val newData: VD,
                                             val actions: List<UpdateActions>) {
    fun <BR : BaseRenderer<T, VD>> dispatchUpdatesTo(adapter: RendererAdapter<T, VD, BR>) =
            adapter.updateData(this)
}


class AdapterBuilder<DL : HListK<ForIdT, DL>, IL : HListK<ForComposeItem, IL>, VDL : HListK<ForComposeItemData, VDL>>(
        val initSumData: DL,
        val composeBuilder: ComposeBuilder<DL, IL, VDL>
) {
    fun <T, VD : ViewData<T>, BR : BaseRenderer<T, VD>>
            add(initData: T, renderer: BR)
            : AdapterBuilder<HConsK<ForIdT, T, DL>, HConsK<ForComposeItem, Pair<T, BR>, IL>, HConsK<ForComposeItemData, Pair<T, BR>, VDL>> =
            AdapterBuilder(initSumData.extend(IdT(initData)), composeBuilder.add(renderer))

    fun <VD : ViewData<Unit>, BR : BaseRenderer<Unit, VD>>
            add(renderer: BR)
            : AdapterBuilder<HConsK<ForIdT, Unit, DL>, HConsK<ForComposeItem, Pair<Unit, BR>, IL>, HConsK<ForComposeItemData, Pair<Unit, BR>, VDL>> =
            AdapterBuilder(initSumData.extend(IdT(Unit)), composeBuilder.add(renderer))

    fun build(): RendererAdapter<DL, ComposeViewData<DL, VDL>, ComposeRenderer<DL, IL, VDL>> =
            RendererAdapter(initSumData, composeBuilder.build())
}