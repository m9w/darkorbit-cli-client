package com.github.m9w.feature

import com.github.m9w.Scheduler
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

abstract class SchedulerEntity(private val scheduler: Scheduler, private val method: KFunction<*>, private val instance: Any) : Runnable {
    var status: String = ""
    override fun run() {
        if (method.isSuspend)
            FeatureController.runCoroutine(scheduler) {
                status = try {
                    method.callSuspend(instance)?.toString() ?: ""
                } catch (e: Exception) {
                    StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
                } finally { postAction() }
            }
        else {
            status = try {
                method.call( instance)?.toString() ?: ""
            } catch (e: Exception) {
                StringWriter().let { e.printStackTrace(PrintWriter(it)) }.toString()
            } finally { postAction() }
        }
    }

    open fun postAction() {}

    abstract fun schedule()

    override fun toString() = status
}