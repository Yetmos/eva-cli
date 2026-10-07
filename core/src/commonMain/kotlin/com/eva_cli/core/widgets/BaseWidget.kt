package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.context.WidgetContext

/** 保存宿主绑定；组件构造期间不能访问 context，也不能切换到其他插件的 context。 */
abstract class BaseWidget : Widget {
    private var boundContext: WidgetContext? = null

    final override val context: WidgetContext
        get() = checkNotNull(boundContext) { "Widget 尚未绑定宿主 Context" }

    final override fun attach(context: WidgetContext) {
        check(boundContext == null || boundContext === context) { "Widget 已绑定其他 Context" }
        boundContext = context
    }
}
