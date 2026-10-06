package com.eva_cli.core.widgets.plug

import kotlinx.serialization.Serializable

/**
 * .eva 包根目录中 plugin.json 的数据模型，包内路径均相对于解压后的根目录。
 * 每个插件使用相同的清单格式，宿主无需在此类型中登记具体插件。
 *
 * 此模型支持 JSON 编解码；包格式、API 兼容性及文件有效性由安装流程检查。
 * @property formatVersion 发布包格式版本。
 * @property version 插件自身的发布版本。
 * @property apiVersion 插件使用的宿主接口版本。
 * @property resources 公共资源目录，未声明时为 null。
 * @property source 包内源码工程信息，与获取插件包的 [PluginSource] 不同。
 */
@Serializable
data class PluginManifest(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val platforms: Platforms,
    val resources: String? = null,
    val source: Source? = null,
    val builtWith: BuiltWith? = null,
) {
    /** 未声明的平台为 null，表示该清单未提供对应平台的接入信息。 */
    @Serializable
    data class Platforms(
        val jvm: Jvm? = null,
        val android: Android? = null,
        val ios: Ios? = null,
    )

    /** JVM 产物及公开无参入口类；[dependencies] 为插件私有依赖所在目录。 */
    @Serializable
    data class Jvm(
        val artifact: String,
        val entryClass: String,
        val dependencies: String? = null,
        val minJavaVersion: Int? = null,
    )

    /** Android DEX 产物及公开无参入口类。 */
    @Serializable
    data class Android(
        val artifact: String,
        val entryClass: String,
        val minSdk: Int? = null,
    )

    /** iOS 构建期接入信息；[factory] 用于生成静态注册代码。 */
    @Serializable
    data class Ios(
        val repository: String,
        val coordinate: String,
        val factory: String,
    )

    /** 包内源码工程的位置及模块坐标，默认使用包根目录中的 Gradle 根项目。 */
    @Serializable
    data class Source(
        val coordinate: String,
        val directory: String = ".",
        val gradleProject: String = ":",
    )

    /** 发布该插件时使用的工具链版本。 */
    @Serializable
    data class BuiltWith(
        val kotlin: String,
    )
}
