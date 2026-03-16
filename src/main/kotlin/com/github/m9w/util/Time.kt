package com.github.m9w.util

fun ms(days: Int = 0,
       hours: Int = 0,
       minutes: Int = 0,
       seconds: Int = 0,
       ms: Int = 0
): Long =
    days.toLong()*24*60*60*1000 +
    hours.toLong()*60*60*1000 +
    minutes.toLong()*60*1000 +
    seconds.toLong()*1000 + ms
