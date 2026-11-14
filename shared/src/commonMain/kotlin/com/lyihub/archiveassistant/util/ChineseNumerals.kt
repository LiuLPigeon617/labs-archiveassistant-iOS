package com.lyihub.archiveassistant.util

internal fun Int.toChineseCount(): String {
  if (this == 0) return "零"
  val digits = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
  if (this < 0) return "负${(-this).toChineseCount()}"
  if (this == 2) return "两"
  if (this < 10) return digits[this]
  if (this < 20) return "十" + if (this % 10 == 0) "" else digits[this % 10]
  if (this < 100) {
    val tens = this / 10
    val ones = this % 10
    return digits[tens] + "十" + if (ones == 0) "" else digits[ones]
  }
  return toString().map { digits[it.digitToInt()] }.joinToString("")
}
