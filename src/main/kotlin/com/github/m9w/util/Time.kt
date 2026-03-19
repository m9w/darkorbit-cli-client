package com.github.m9w.util

fun ms(from: Long = 0,
       days: Int = 0,
       hours: Int = 0,
       min: Int = 0,
       sec: Int = 0,
       ms: Int = 0
): Long = from +
    days.toLong()*24*60*60*1000 +
    hours.toLong()*60*60*1000 +
    min.toLong()*60*1000 +
    sec.toLong()*1000 + ms

fun isTimeout(from: Long = 0,
       days: Int = 0,
       hours: Int = 0,
       min: Int = 0,
       sec: Int = 0,
       ms: Int = 0
): Boolean = ms(from, days, hours, min, sec, ms) < System.currentTimeMillis()
