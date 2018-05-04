package indi.yume.tools.dsladapter3

import android.support.v7.util.ListUpdateCallback
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.some
import indi.yume.tools.dsladapter3.datatype.*
import indi.yume.tools.dsladapter3.renderers.*
import indi.yume.tools.dsladapter3.typeclass.BaseRenderer
import indi.yume.tools.dsladapter3.typeclass.Renderer
import indi.yume.tools.dsladapter3.typeclass.ViewData
import org.junit.Test

class UpdatesTest {

    @Test
    fun testUpdate() {
        val oldData: List<Pair<String, List<String>>> =
                listOf(
                        "sub1" to listOf("Sub 11", "Sub 12", "Sub 13"),
                        "sub2" to listOf("Sub 21", "Sub 22", "Sub 23"),
                        "sub3" to listOf("Sub 31", "Sub 32", "Sub 33"),
                        "sub4" to listOf("Sub 41", "Sub 42", "Sub 43")
                )
        val newData: List<Pair<String, List<String>>> =
                listOf(
                        "sub1" to listOf("Sub 11", "Sub 22", "Sub 13"),
                        "sub2" to listOf("Sub 21", "Sub 22", "Sub 23"),
                        "sub3" to listOf("Sub 31", "Sub 32", "Sub 33"),
                        "sub4" to listOf("Sub 41", "Sub 42", "Sub 43")
                )

        val renderer = optionRenderer(
                noneItemRenderer = ConstantItemRenderer<Unit>(count = 3, layout = 1),
                itemRenderer = ListRenderer<List<Pair<String, List<String>>>,
                        Pair<String, List<String>>,
                        GroupViewData<ConstantViewData<String>, ConstantViewData<String>>>(
                        { it },
                        GroupItemRenderer(
                                { it.first },
                                { it.second },
                                ConstantItemRenderer<String>(count = 1, layout = 3),
                                ConstantItemRenderer<String>(count = 2, layout = 4)
                        ),
                        keyGetter = { it.titleItem.data }
                )
        )

        val result = renderer.getUpdates(renderer.getData(oldData.some()), renderer.getData(newData.some())).filterUselessAction()

        result.flatten().forEach { println(it) }

        println("================")

        result.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                println("onInserted: position=$position, count=$count")
            }

            override fun onRemoved(position: Int, count: Int) {
                println("onRemoved: position=$position, count=$count")
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                println("onMoved: fromPosition=$fromPosition, toPosition=$toPosition")
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                println("onChanged: position=$position, count=$count, payload=$payload")
            }
        })


        val oldList = renderer.typeList(oldData.some())
        val newList = renderer.typeList(newData.some())

        println("oldList: ${oldList.joinToString()}")
        println("newList: ${newList.joinToString()}")
    }
}

fun <T, VD : ViewData> Renderer<T, VD>.typeList(data: T): List<Int> {
    val vd = getData(data)
    return (0 until vd.count).map { getItemViewType(vd, it) }
}

fun List<UpdateActions>.flatten(): List<String> =
        flatMap { it.string() }

fun UpdateActions.string(): List<String> =
        when(this) {
            is OnInserted -> listOf("onInserted: position=$pos, count=$count")
            is OnRemoved -> listOf("onRemoved: position=$pos, count=$count")
            is OnMoved -> listOf("onMoved: fromPosition=$fromPosition, toPosition=$toPosition")
            is OnChanged -> listOf("onChanged: position=$pos, count=$count, payload=$payload")
            is ActionComposite -> listOf("Composite: offset:$offset") + actions.flatMap { it.string() }.map { "  |--$it" }
        }


fun <T, NVD : ViewData, SVD : ViewData> optionRenderer(noneItemRenderer: BaseRenderer<Unit, NVD>,
                                                       itemRenderer: BaseRenderer<T, SVD>): BaseRenderer<Option<T>, SealedViewData<Option<T>>> =
        SealedItemRenderer<Option<T>>(listOf(
                item({ it is None }, { Unit }, noneItemRenderer),

                item({ it is Some }, { it.orNull()!! }, itemRenderer))
        )