package indi.yume.tools.sample

import arrow.Kind
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import indi.yume.tools.dsladapter.datatype.HConsK
import indi.yume.tools.dsladapter.datatype.HNilK
import indi.yume.tools.dsladapter.datatype.hlistKOf
import indi.yume.tools.dsladapter.renderers.ForSealedItem
import indi.yume.tools.dsladapter.renderers.SealedItemRenderer
import indi.yume.tools.dsladapter.renderers.item
import indi.yume.tools.dsladapter.type
import indi.yume.tools.dsladapter.typeclass.BaseRenderer
import indi.yume.tools.dsladapter.typeclass.ViewData


fun <T, NVD : ViewData<Unit>,
        SVD : ViewData<T>> optionRenderer(noneItemRenderer: BaseRenderer<Unit, NVD>,
                                                                   itemRenderer: BaseRenderer<T, SVD>)
        : SealedItemRenderer<Option<T>, HConsK<Kind<ForSealedItem, Option<T>>, Pair<T, SVD>, HConsK<Kind<ForSealedItem, Option<T>>, Pair<Unit, NVD>, HNilK<Kind<ForSealedItem, Option<T>>>>>> =
        SealedItemRenderer(hlistKOf(
                item(type = type<Option<T>>(),
                        checker = { it is None },
                        mapper = { Unit },
                        renderer = noneItemRenderer
                ),
                item(type = type<Option<T>>(),
                        checker = { it is Some },
                        mapper = { it.orNull()!! },
                        renderer = itemRenderer
                )
        ))