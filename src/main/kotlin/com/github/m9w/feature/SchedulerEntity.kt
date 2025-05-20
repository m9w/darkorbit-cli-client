package com.github.m9w.feature

import com.github.m9w.Scheduler
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

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