package org.jetbrains.plugins.template.device

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Simple device poller using IntelliJ's AppExecutorUtil instead of raw ScheduledExecutorService.
 * This is more reliable and integrates better with IntelliJ's lifecycle.
 */
class SimpleDevicePoller(
    private val hdcExecutor: HdcCommandExecutor,
    private val onDevicesChanged: (List<HarmonyDevice>) -> Unit,
    private val pollingIntervalSeconds: Long = 10L  //10 秒轮询一次
) {
    private val logger = Logger.getInstance(SimpleDevicePoller::class.java)
    
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var previousDevices: List<HarmonyDevice> = emptyList()
    
    @Volatile
    private var running = false
    
    @Volatile
    private var isFirstPoll = true  // 标记是否是第一次轮询
    
    fun start() {
        println("=== SimpleDevicePoller.start() CALLED (NEW VERSION) ===")
        println("Thread: ${Thread.currentThread().name}")
        
        if (running) {
            println("!!! Already running, returning")
            logger.info("SimpleDevicePoller already running")
            return
        }
        
        println("Starting SimpleDevicePoller with interval ${pollingIntervalSeconds}s")
        logger.info("Starting SimpleDevicePoller with interval ${pollingIntervalSeconds}s")
        running = true
        
        // 使用 IntelliJ 的 AppExecutorUtil - 更可靠
        println("Using AppExecutorUtil.getAppScheduledExecutorService()...")
        val executor = AppExecutorUtil.getAppScheduledExecutorService()
        println("Executor obtained: $executor")
        
        // 调度定期任务
        println("Scheduling polling task with scheduleWithFixedDelay...")
        scheduledFuture = executor.scheduleWithFixedDelay(
            {
                println("=== POLLING TASK EXECUTING (AppExecutorUtil callback) ===")
                try {
                    pollDevices()
                } catch (e: Exception) {
                    println("!!! Exception in polling task: ${e.message}")
                    e.printStackTrace()
                    logger.error("Exception in polling task", e)
                }
            },
            0, // Start immediately
            pollingIntervalSeconds,
            TimeUnit.SECONDS
        )
        
        println("Polling task scheduled successfully")
        println("ScheduledFuture: $scheduledFuture")
        println("Future cancelled: ${scheduledFuture?.isCancelled}, done: ${scheduledFuture?.isDone}")
        logger.info("SimpleDevicePoller started successfully with AppExecutorUtil")
        
        // 立即执行一次测试
        println("Executing immediate test poll...")
        try {
            pollDevices()
        } catch (e: Exception) {
            println("!!! Exception in immediate test poll: ${e.message}")
            e.printStackTrace()
            logger.error("Exception in immediate test poll", e)
        }
        
        println("=== SimpleDevicePoller.start() COMPLETE ===")
    }
    
    fun stop() {
        logger.info("Stopping SimpleDevicePoller")
        running = false
        scheduledFuture?.cancel(false)
        scheduledFuture = null
    }
    
    private fun pollDevices() {
        val startTime = System.currentTimeMillis()
        println("=== SimpleDevicePoller.pollDevices() CALLED (NEW VERSION) ===")
        println("Time: ${java.util.Date()}")
        println("Thread: ${Thread.currentThread().name}")
        println("Thread ID: ${Thread.currentThread().id}")
        println("Running: $running")
        println("ScheduledFuture: $scheduledFuture")
        println("Future cancelled: ${scheduledFuture?.isCancelled}")
        println("Future done: ${scheduledFuture?.isDone}")
        
        if (!running) {
            println("!!! Not running, returning")
            return
        }
        
        try {
            println("Calling hdcExecutor.listDevices()...")
            logger.info("Polling for devices...")
            val currentDevices = hdcExecutor.listDevices()
            
            println("Poll result: ${currentDevices.size} devices found")
            logger.info("Poll result: ${currentDevices.size} devices found")
            currentDevices.forEach { device ->
                println("  - ${device.displayName} (${device.deviceId})")
                logger.info("  - ${device.displayName} (${device.deviceId})")
            }
            
            val changed = hasDeviceListChanged(previousDevices, currentDevices)
            println("Device list changed: $changed")
            println("Is first poll: $isFirstPoll")
            
            // 第一次轮询或设备列表变化时，都触发回调
            if (changed || isFirstPoll) {
                println("Triggering device change notification (changed=$changed, firstPoll=$isFirstPoll)")
                println("Device list: ${previousDevices.size} -> ${currentDevices.size}")
                logger.info("Device list changed: ${previousDevices.size} -> ${currentDevices.size}")
                
                previousDevices = currentDevices
                isFirstPoll = false  // 标记第一次轮询已完成
                
                // Notify on EDT
                println("Invoking onDevicesChanged on EDT...")
                ApplicationManager.getApplication().invokeLater {
                    println("EDT invokeLater executing, calling onDevicesChanged...")
                    logger.info("Notifying listeners of device change")
                    onDevicesChanged(currentDevices)
                    println("onDevicesChanged callback complete")
                }
            } else {
                println("Device list unchanged, skipping notification")
                logger.info("Device list unchanged")
            }
        } catch (e: Exception) {
            println("!!! ERROR polling devices: ${e.message}")
            e.printStackTrace()
            logger.error("Error polling devices", e)
        }
        
        val duration = System.currentTimeMillis() - startTime
        println("Poll completed in ${duration}ms")
        println("=== SimpleDevicePoller.pollDevices() COMPLETE ===")
    }
    
    private fun hasDeviceListChanged(
        previous: List<HarmonyDevice>,
        current: List<HarmonyDevice>
    ): Boolean {
        println("=== hasDeviceListChanged() CALLED ===")
        println("previous.size=${previous.size}, current.size=${current.size}")
        
        // 如果大小不同，肯定变化了
        if (previous.size != current.size) {
            println("Size changed: ${previous.size} -> ${current.size}, returning TRUE")
            return true
        }
        
        // 如果都是空列表，没有变化
        if (previous.isEmpty() && current.isEmpty()) {
            println("Both empty, returning FALSE")
            return false
        }
        
        // 比较设备 ID
        val previousIds = previous.map { it.deviceId }.toSet()
        val currentIds = current.map { it.deviceId }.toSet()
        
        println("previousIds: $previousIds")
        println("currentIds: $currentIds")
        
        val changed = previousIds != currentIds
        println("IDs comparison result: $changed")
        println("=== hasDeviceListChanged() RETURNING: $changed ===")
        
        return changed
    }
    
    fun isRunning(): Boolean = running
}
