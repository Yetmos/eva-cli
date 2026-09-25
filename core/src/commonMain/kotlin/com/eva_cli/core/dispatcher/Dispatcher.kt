package com.eva_cli.core.dispatcher

import kotlin.uuid.Uuid

// INFO: 负责Provider调度
interface Dispatcher {
    val id: Uuid
    val name: String
    val desc: String
    val topic: String
    var enable: Boolean


}