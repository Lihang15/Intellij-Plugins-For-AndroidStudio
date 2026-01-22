package org.jetbrains.plugins.template.device

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Simple device poller using Java's ScheduledExecutorService instead of Kotlin coroutines.
 * This is a fallback implementation that doesn't rely on coroutines.
 */
class SimpleDevicePoller(
    private val hdcExecutor: HdcCommandExecutor,
    private val onDevicesChanged: (List<HarmonyDevice>) -> Unit,
    private val pollingIntervalSeconds: Long = 10L
) {
    private val logger = Logger.getInstance(SimpleDevicePoller::class.java)
    
    private var scheduler: ScheduledExecutorService? = null
    private var previousDevices: List<HarmonyDevice> = emptyList()
    
    @Volatile
    private var running = false
    
    fun start() {
        println("=== SimpleDevicePoller.start() CALLED ===")
        
        if (running) {
            println("!!! Already running, returning")
            logger.info("SimpleDevicePoller already running")
            return
        }
        
        println("Starting SimpleDevicePoller with interval ${pollingIntervalSeconds}s")
        logger.info("Starting SimpleDevicePoller with interval ${pollingIntervalSeconds}s")
        running = true
        
        println("Creating ScheduledExecutorService...")
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "HarmonyOS-Device-Poller").apply {
                isDaemon = true
            }
        }
        println("ScheduledExecutorService created: ${scheduler != null}")
        
        // Schedule polling task
        println("Scheduling polling task...")
        scheduler?.scheduleAtFixedRate(
            { pollDevices() },
            0, // Start immediately
            pollingIntervalSeconds,
            TimeUnit.SECONDS
        )
        
        println("Polling task scheduled")
        logger.info("SimpleDevicePoller started successfully")
        println("=== SimpleDevicePoller.start() COMPLETE ===")
    }
    
    fun stop() {
        logger.info("Stopping SimpleDevicePoller")
        running = false
        scheduler?.shutdown()
        scheduler = null
    }
    
    private fun pollDevices() {
        println("=== SimpleDevicePoller.pollDevices() CALLED ===")
        println("Thread: ${Thread.currentThread().name}")
        println("Running: $running")
        
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
            
            if (changed) {
                println("Device list changed: ${previousDevices.size} -> ${currentDevices.size}")
                logger.info("Device list changed: ${previousDevices.size} -> ${currentDevices.size}")
                previousDevices = currentDevices
                
                // Notify on EDT
                println("Invoking onDevicesChanged on EDT...")
                ApplicationManager.getApplication().invokeLater {
                    println("EDT invokeLater executing, calling onDevicesChanged...")
                    logger.info("Notifying listeners of device change")
                    onDevicesChanged(currentDevices)
                    println("onDevicesChanged callback complete")
                }
            } else {
                println("Device list unchanged")
                logger.info("Device list unchanged")
            }
        } catch (e: Exception) {
            println("!!! ERROR polling devices: ${e.message}")
            e.printStackTrace()
            logger.error("Error polling devices", e)
        }
        
        println("=== SimpleDevicePoller.pollDevices() COMPLETE ===")
    }
    
    private fun hasDeviceListChanged(
        previous: List<HarmonyDevice>,
        current: List<HarmonyDevice>
    ): Boolean {
        println("hasDeviceListChanged: previous.size=${previous.size}, current.size=${current.size}")
        
        if (previous.size != current.size) {
            println("Size changed, returning true")
            return true
        }
        
        val previousIds = previous.map { it.deviceId }.toSet()
        val currentIds = current.map { it.deviceId }.toSet()
        
        println("previousIds: $previousIds")
        println("currentIds: $currentIds")
        
        val changed = previousIds != currentIds
        println("IDs changed: $changed")
        
        return changed
    }
    
    fun isRunning(): Boolean = running
}
