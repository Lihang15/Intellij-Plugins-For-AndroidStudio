package org.jetbrains.plugins.template.device

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.pow

/**
 * Polls HDC for device list changes in the background using Kotlin coroutines.
 *
 * @property hdcExecutor The HDC command executor to use for polling
 * @property onDevicesChanged Callback invoked when device list changes
 * @property pollingIntervalMs Polling interval in milliseconds (default: 2000ms)
 */
class DevicePoller(
    private val hdcExecutor: HdcCommandExecutor,
    private val onDevicesChanged: (List<HarmonyDevice>) -> Unit,
    private val pollingIntervalMs: Long = 2000L
) {
    private val logger = Logger.getInstance(DevicePoller::class.java)
    
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var previousDevices: List<HarmonyDevice> = emptyList()
    private var consecutiveFailures = 0
    
    companion object {
        private const val MAX_BACKOFF_SECONDS = 30
        private const val BACKOFF_BASE = 2.0
    }
    
    /**
     * Starts the device polling loop.
     * This method is idempotent - calling it multiple times has no effect if already running.
     */
    fun start() {
        if (pollingJob?.isActive == true) {
            logger.info("DevicePoller already running")
            return
        }
        
        logger.info("Starting DevicePoller with interval ${pollingIntervalMs}ms")
        
        pollingJob = scope.launch {
            logger.info("DevicePoller coroutine started")
            
            // Do an immediate poll on startup
            try {
                logger.info("Performing initial poll...")
                pollDevices()
                consecutiveFailures = 0
            } catch (e: Exception) {
                logger.error("Initial poll failed", e)
                consecutiveFailures++
            }
            
            while (isActive) {
                try {
                    logger.info("Waiting ${pollingIntervalMs}ms before next poll...")
                    delay(pollingIntervalMs)
                    logger.info("Delay complete, polling now...")
                    pollDevices()
                    consecutiveFailures = 0
                } catch (e: CancellationException) {
                    logger.info("DevicePoller cancelled")
                    throw e
                } catch (e: Exception) {
                    consecutiveFailures++
                    logger.warn("Error polling devices (failure #$consecutiveFailures)", e)
                    
                    // Exponential backoff on repeated failures
                    val backoffDelay = calculateBackoffDelay()
                    logger.info("Retrying in ${backoffDelay}ms")
                    delay(backoffDelay)
                }
            }
        }
        
        logger.info("DevicePoller start() method completed, job active: ${pollingJob?.isActive}")
    }
    
    /**
     * Stops the device polling loop and cancels all coroutines.
     */
    fun stop() {
        logger.info("Stopping DevicePoller")
        pollingJob?.cancel()
        pollingJob = null
        scope.cancel()
    }
    
    /**
     * Polls for devices and notifies listeners if the device list has changed.
     */
    private suspend fun pollDevices() {
        logger.info("Polling for devices...")
        
        val currentDevices = withContext(Dispatchers.IO) {
            hdcExecutor.listDevices()
        }
        
        logger.info("Poll result: ${currentDevices.size} devices found")
        currentDevices.forEach { device ->
            logger.info("  - ${device.displayName} (${device.deviceId})")
        }
        
        if (hasDeviceListChanged(previousDevices, currentDevices)) {
            logger.info("Device list changed: ${previousDevices.size} -> ${currentDevices.size}")
            previousDevices = currentDevices
            
            // Notify on EDT using invokeLater instead of Dispatchers.Main
            logger.info("Notifying listeners of device change")
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                onDevicesChanged(currentDevices)
            }
        } else {
            logger.debug("Device list unchanged")
        }
    }
    
    /**
     * Checks if the device list has changed.
     *
     * @param previous Previous device list
     * @param current Current device list
     * @return true if the lists differ, false otherwise
     */
    private fun hasDeviceListChanged(
        previous: List<HarmonyDevice>,
        current: List<HarmonyDevice>
    ): Boolean {
        if (previous.size != current.size) return true
        
        val previousIds = previous.map { it.deviceId }.toSet()
        val currentIds = current.map { it.deviceId }.toSet()
        
        return previousIds != currentIds
    }
    
    /**
     * Calculates exponential backoff delay based on consecutive failures.
     *
     * @return Delay in milliseconds
     */
    private fun calculateBackoffDelay(): Long {
        val backoffSeconds = min(
            BACKOFF_BASE.pow(consecutiveFailures.toDouble()).toLong(),
            MAX_BACKOFF_SECONDS.toLong()
        )
        return backoffSeconds * 1000
    }
    
    /**
     * Checks if the poller is currently running.
     *
     * @return true if polling is active, false otherwise
     */
    fun isRunning(): Boolean {
        return pollingJob?.isActive == true
    }
}
