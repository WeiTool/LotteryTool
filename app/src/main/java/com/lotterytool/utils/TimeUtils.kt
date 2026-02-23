package com.lotterytool.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val publishTimeFormatter = SimpleDateFormat("yy年MM月dd日 HH时mm分ss秒", Locale.CHINESE)

fun formatPublishTime(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    return publishTimeFormatter.format(date)
}