package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class DynamicIdResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: DynamicData?
)

data class DynamicData(
    @SerializedName("content") val content: String?,
    @SerializedName("opus") val opus: DynamicOpus?,
)

data class DynamicOpus(
    @SerializedName("content") val content: Paragraphs?,
)

data class Paragraphs(
    @SerializedName("paragraphs") val paragraphs: List<ParagraphsText>?,
)

data class ParagraphsText(
    // ⚠️ 核心修复：图片段/视频段等 para_type != 1 的段落在 JSON 里没有 text 字段，
    // Gson 会直接写 null（无视 Kotlin 非空声明）。
    // 原来声明为非空 Nodes，导致 paraText.text.nodes 触发 NPE，
    // forEach 中途崩溃，只有崩溃前已收集的少量节点进入列表，
    // 外层 catch 捕获异常后 insertIds 根本没有被调用。
    @SerializedName("text") val text: Nodes?,
)

data class Nodes(
    @SerializedName("nodes") val nodes: List<Node>?,
)

data class Node(
    @SerializedName("node_type") val nodeType: Int,
    // node_type != 4 的节点没有 link 字段，Gson 同样写 null
    @SerializedName("link") val link: Link?,
)

data class Link(
    @SerializedName("link") val link: String?,
)