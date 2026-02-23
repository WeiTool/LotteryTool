package com.lotterytool.data.models

import com.google.gson.annotations.SerializedName

data class DynamicIdResponse(
    //顶层字段
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: DynamicData?
)

data class DynamicData(
    //data层
    @SerializedName("content") val content: String?,
    @SerializedName("opus") val opus: DynamicOpus?,
)

data class DynamicOpus(
    //opus层
    @SerializedName("content") val content: Paragraphs,
)

data class Paragraphs(
    @SerializedName("paragraphs") val paragraphs: List<ParagraphsText>,
)

data class ParagraphsText(
    @SerializedName("text") val text: Nodes,
)

data class Nodes(
    @SerializedName("nodes") val nodes: List<Node>,
)

data class Node(
    @SerializedName("node_type") val nodeType: Int,
    @SerializedName("link") val link: Link,
)

data class Link(
    @SerializedName("link") val link: String,
)
