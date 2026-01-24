package org.jetbrains.plugins.template.device

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Executes HDC (HarmonyOS Device Connector) commands and parses the output.
 */
class HdcCommandExecutor(private val hdcPath: String) {
    
    private val logger = Logger.getInstance(HdcCommandExecutor::class.java)
    
    companion object {
        private const val COMMAND_TIMEOUT_SECONDS = 5L
        private const val EMPTY_OUTPUT = "[Empty]"
    }
    
    /**
     * Checks if the HDC executable exists at the configured path.
     *
     * @return true if HDC exists and is executable, false otherwise
     */
    fun isHdcAvailable(): Boolean {
        val hdcFile = File(hdcPath)
        return hdcFile.exists() && hdcFile.canExecute()
    }
    
    /**
     * Lists all connected HarmonyOS devices by executing 'hdc list targets'.
     *
     * @return List of HarmonyDevice objects, empty list if no devices or on error
     */
    fun listDevices(): List<HarmonyDevice> {
        println("=== HdcCommandExecutor.listDevices() CALLED ===")
        
        if (!isHdcAvailable()) {
            println("!!! HDC not available at path: $hdcPath")
            logger.warn("HDC not found at path: $hdcPath")
            return emptyList()
        }
        
        return try {
            println("Executing HDC command: $hdcPath list targets")
            logger.info("Executing HDC command: $hdcPath list targets")
            val output = executeCommand(arrayOf(hdcPath, "list", "targets"))
            println("HDC output: [$output]")
            logger.info("HDC output: '$output'")
            val devices = parseDeviceOutput(output)
            println("Parsed ${devices.size} devices")
            logger.info("Parsed ${devices.size} devices")
            devices.forEach { device ->
                println("  Device: ${device.displayName} (${device.deviceId})")
            }
            println("=== HdcCommandExecutor.listDevices() COMPLETE ===")
            devices
        } catch (e: Exception) {
            println("!!! EXCEPTION in listDevices: ${e.message}")
            e.printStackTrace()
            logger.warn("Failed to list devices", e)
            emptyList()
        }
    }
    
    /**
     * Executes a command and returns its output.
     *
     * @param command The command array to execute
     * @return The command output as a string
     * @throws Exception if command execution fails or times out
     */
    private fun executeCommand(command: Array<String>): String {
        println("=== executeCommand() CALLED ===")
        println("Command: ${command.joinToString(" ")}")
        
        val processBuilder = ProcessBuilder(*command)
        processBuilder.redirectErrorStream(true)
        
        println("Starting process...")
        val process = processBuilder.start()
        
        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        
        println("Reading output...")
        reader.use { r ->
            r.forEachLine { line ->
                println("  Output line: [$line]")
                output.append(line).append("\n")
            }
        }
        
        println("Waiting for process to complete...")
        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        
        if (!completed) {
            println("!!! Process timed out")
            process.destroyForcibly()
            throw Exception("HDC command timed out after $COMMAND_TIMEOUT_SECONDS seconds")
        }
        
        val exitCode = process.exitValue()
        println("Process exit code: $exitCode")
        
        if (exitCode != 0) {
            println("!!! Non-zero exit code")
            logger.warn("HDC command exited with code $exitCode: ${output.toString().trim()}")
        }
        
        val result = output.toString().trim()
        println("Returning output: [$result]")
        println("=== executeCommand() COMPLETE ===")
        return result
    }
    
    /**
     * Parses the output from 'hdc list targets' command.
     *
     * Expected formats:
     * - "[Empty]" when no devices are connected
     * - Device IDs may be on separate lines OR concatenated without newlines
     * - Device ID format: "127.0.0.1:PORT" or other formats
     *
     * @param output The raw output from HDC command
     * @return List of parsed HarmonyDevice objects
     */
    private fun parseDeviceOutput(output: String): List<HarmonyDevice> {
        if (output.isBlank() || output == EMPTY_OUTPUT) {
            return emptyList()
        }
        
        println("=== Raw HDC output: [$output] ===")
        
        // HDC may output device IDs without newlines, e.g., "127.0.0.1:5555127.0.0.1:5557"
        // We need to split by the pattern "127.0.0.1:" or "localhost:"
        val devicePattern = Regex("(127\\.0\\.0\\.1:\\d+|localhost:\\d+|[a-zA-Z0-9]+)")
        val matches = devicePattern.findAll(output)
        
        val devices = matches.map { match ->
            val deviceId = match.value.trim()
            println("=== Found device ID: [$deviceId] ===")
            try {
                val device = HarmonyDevice.fromDeviceId(deviceId)
                println("=== Created device: ${device.displayName} (${device.deviceId}) ===")
                device
            } catch (e: Exception) {
                logger.warn("Failed to parse device ID: $deviceId", e)
                println("!!! Failed to parse device ID: $deviceId - ${e.message}")
                null
            }
        }.filterNotNull().toList()
        
        println("=== Total devices parsed: ${devices.size} ===")
        return devices
    }
}
