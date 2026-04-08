package com.github.m9w.feature

import java.io.PrintWriter
import java.io.StringWriter

abstract class SchedulerEntity(private val scheduler: Scheduler, private val isSuspend: Boolean) {
    var status: String = ""
    fun run(suspendAction: suspend () -> Any?, plainAction: () -> Any?) {
        if (isSuspend)
            FeatureController.runCoroutine(scheduler) {
                status = try {
                    suspendAction.invoke()?.toString() ?: ""
                } catch (e: Exception) {
                    e.printStackTrace()
                    StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
                } finally { postAction() }
            }
        else {
            status = try {
                plainAction.invoke()?.toString() ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
            } finally { postAction() }
        }
    }

    open fun postAction() {}

    abstract fun schedule()

    override fun toString() = status
}