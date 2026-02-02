package org.jetbrains.plugins.template.device

import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.template.runconfig.HarmonyRunConfiguration
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level service that manages HarmonyOS device discovery and selection.
 * This service automatically starts polling for devices when the project opens
 * and stops when the project closes.
 */
@Service(Service.Level.PROJECT)
class DeviceService(private val project: Project) : Disposable {
    
    private val logger = Logger.getInstance(DeviceService::class.java)
    
    // Thread-safe storage for devices and selection
    private val devices = AtomicReference<List<HarmonyDevice>>(emptyList())
    private val selectedDevice = AtomicReference<HarmonyDevice?>(null)
    
    // Synchronized list of listeners
    private val listeners = mutableListOf<() -> Unit>()
    
    // Device poller - using simple Java-based poller instead of coroutines
    private var poller: SimpleDevicePoller? = null
    
    @Volatile
    private var state: State = State.INACTIVE
    
    // For debugging
    @Volatile
    private var lastPollTime: Long = 0
    @Volatile
    private var pollCount: Int = 0
    
    enum class State {
        INACTIVE,  // Service not started
        LOADING,   // Starting up
        READY      // Running and ready
    }
    
    init {
        println("=== DeviceService INIT START for project: ${project.name} ===")
        logger.info("DeviceService initialized for project: ${project.name}")
        startPolling()
        println("=== DeviceService INIT END ===")
    }
    
    /**
     * Adds a listener that will be notified when device list or selection changes.
     *
     * @param listener Callback to invoke on changes
     */
    fun addListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
        logger.debug("Listener added, total listeners: ${listeners.size}")
    }
    
    /**
     * Removes a previously registered listener.
     *
     * @param listener The listener to remove
     */
    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
        logger.debug("Listener removed, total listeners: ${listeners.size}")
    }
    
    /**
     * Returns the list of currently connected devices.
     *
     * @return Immutable list of connected devices
     */
    fun getConnectedDevices(): List<HarmonyDevice> {
        return devices.get()
    }
    
    /**
     * Returns the currently selected device, or null if none selected.
     *
     * @return The selected device or null
     */
    fun getSelectedDevice(): HarmonyDevice? {
        return selectedDevice.get()
    }
    
    /**
     * Sets the selected device and notifies listeners.
     *
     * @param device The device to select, or null to clear selection
     */
    fun setSelectedDevice(device: HarmonyDevice?) {
        val previous = selectedDevice.getAndSet(device)
        if (previous != device) {
            logger.info("Device selection changed: ${device?.deviceId ?: "none"}")
            fireChangeEvent()
        }
    }
    
    /**
     * Returns the current service state.
     *
     * @return Current state (INACTIVE, LOADING, or READY)
     */
    fun getStatus(): State {
        return state
    }
    
    /**
     * Manually triggers a device refresh.
     * Useful for testing or when user wants to force a refresh.
     */
    fun refresh() {
        logger.info("Manual refresh requested")
        // The poller will pick up changes on next poll cycle
        // We could also trigger an immediate poll here if needed
    }
    
    /**
     * Gets debug information about the service.
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("Poller running: ${poller?.isRunning()}")
            appendLine("Last poll time: ${if (lastPollTime > 0) java.util.Date(lastPollTime) else "Never"}")
            appendLine("Poll count: $pollCount")
            appendLine("Listener count: ${synchronized(listeners) { listeners.size }}")
        }
    }
    
    /**
     * Starts the device polling process.
     */
    private fun startPolling() {
        println("=== DeviceService.startPolling() CALLED ===")
        
        if (poller?.isRunning() == true) {
            println("!!! Polling already started, returning")
            logger.warn("Polling already started")
            return
        }
        
        state = State.LOADING
        println("State set to LOADING")
        logger.info("Starting device polling")
        
        val hdcPath = "/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc"
        println("HDC path: $hdcPath")
        val hdcExecutor = HdcCommandExecutor(hdcPath)
        
        // Check if HDC is available
        val hdcAvailable = hdcExecutor.isHdcAvailable()
        println("HDC available: $hdcAvailable")
        
        if (!hdcAvailable) {
            println("!!! HDC not found, showing notification")
            logger.error("HDC not found at: $hdcPath")
            state = State.INACTIVE
            showHdcNotFoundNotification(hdcPath)
            return
        }
        
        println("Creating SimpleDevicePoller...")
        poller = SimpleDevicePoller(hdcExecutor, ::onDevicesChanged)
        println("SimpleDevicePoller created: ${poller != null}")
        
        println("Starting poller...")
        poller?.start()
        println("Poller started, isRunning: ${poller?.isRunning()}")
        
        state = State.READY
        println("State set to READY")
        logger.info("Device polling started successfully")
        println("=== DeviceService.startPolling() COMPLETE ===")
    }
    
    /**
     * Shows a notification when HDC is not found.
     */
    private fun showHdcNotFoundNotification(hdcPath: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("HarmonyOS Device")
                    .createNotification(
                        "HarmonyOS Device Connector (HDC) Not Found",
                        "HDC was not found at: $hdcPath\n\n" +
                        "Please ensure DevEco Studio is installed at the expected location.\n" +
                        "Device monitoring will be disabled until HDC is available.",
                        NotificationType.WARNING
                    )
                    .notify(project)
            } catch (e: Exception) {
                logger.error("Failed to show HDC not found notification", e)
            }
        }
    }
    
    /**
     * Callback invoked by DevicePoller when device list changes.
     * This method runs on the main thread.
     *
     * @param newDevices The updated list of devices
     */
    private fun onDevicesChanged(newDevices: List<HarmonyDevice>) {
        println("=== DeviceService.onDevicesChanged() CALLED ===")
        println("New devices count: ${newDevices.size}")
        
        lastPollTime = System.currentTimeMillis()
        pollCount++
        
        val oldDevices = devices.getAndSet(newDevices)
        
        println("Poll #$pollCount: ${oldDevices.size} -> ${newDevices.size} devices")
        logger.info("Devices changed (poll #$pollCount): ${oldDevices.size} -> ${newDevices.size}")
        newDevices.forEach { device ->
            println("  Device: ${device.displayName} (${device.deviceId})")
            logger.info("  Device: ${device.displayName} (${device.deviceId})")
        }
        
        // Handle device selection
        val current = selectedDevice.get()
        
        when {
            // Auto-select first device if none selected
            current == null && newDevices.isNotEmpty() -> {
                selectedDevice.set(newDevices.first())
                println("Auto-selected first device: ${newDevices.first().deviceId}")
                logger.info("Auto-selected first device: ${newDevices.first().deviceId}")
            }
            // Clear selection if selected device disconnected
            current != null && !newDevices.contains(current) -> {
                val newSelection = newDevices.firstOrNull()
                selectedDevice.set(newSelection)
                println("Selected device disconnected, new selection: ${newSelection?.deviceId ?: "none"}")
                logger.info("Selected device disconnected, new selection: ${newSelection?.deviceId ?: "none"}")
            }
        }
        
        println("Firing change event to listeners...")
        fireChangeEvent()
        println("=== DeviceService.onDevicesChanged() COMPLETE ===")
    }
    
    /**
     * Notifies all registered listeners of a change.
     * This method ensures notifications happen on the EDT.
     */
    private fun fireChangeEvent() {
        println("=== DeviceService.fireChangeEvent() CALLED ===")
        ApplicationManager.getApplication().invokeLater {
            println("fireChangeEvent: EDT invokeLater executing")
            
            if (project.isDisposed) {
                println("Project disposed, skipping listener notification")
                logger.debug("Project disposed, skipping listener notification")
                return@invokeLater
            }
            
            val listenersCopy = synchronized(listeners) {
                listeners.toList()
            }
            
            println("Notifying ${listenersCopy.size} listeners")
            logger.debug("Notifying ${listenersCopy.size} listeners")
            
            listenersCopy.forEachIndexed { index, listener ->
                try {
                    println("Calling listener #$index")
                    listener()
                    println("Listener #$index completed")
                } catch (e: Exception) {
                    println("!!! Listener #$index threw exception: ${e.message}")
                    logger.error("Listener threw exception", e)
                }
            }
            
            println("All listeners notified")
        }
        println("=== DeviceService.fireChangeEvent() COMPLETE ===")
    }
    
    /**
     * Stops polling and releases resources.
     */
    override fun dispose() {
        logger.info("Disposing DeviceService for project: ${project.name}")
        poller?.stop()
        poller = null
        synchronized(listeners) {
            listeners.clear()
        }
        state = State.INACTIVE
    }
    
    companion object {
        /**
         * Gets the DeviceService instance for a project.
         *
         * @param project The project
         * @return The DeviceService instance
         */
        fun getInstance(project: Project): DeviceService {
            return project.service()
        }
    }
}
