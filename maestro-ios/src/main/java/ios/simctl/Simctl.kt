package ios.simctl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import maestro.utils.MaestroTimer
import util.CommandLineUtils.runCommand
import java.io.File

object Simctl {

    data class SimctlError(override val message: String): Throwable(message)
    private val homedir = System.getProperty("user.home")

    fun list(): SimctlList {
        val command = listOf("xcrun", "simctl", "list", "-j")

        val process = ProcessBuilder(command).start()
        val json = String(process.inputStream.readBytes())

        return jacksonObjectMapper().readValue(json)
    }

    fun awaitLaunch(deviceId: String) {
        MaestroTimer.withTimeout(30000) {
            if (list()
                    .devices
                    .values
                    .flatten()
                    .find { it.udid == deviceId }
                    ?.state == "Booted"
            ) true else null
        } ?: throw SimctlError("Device $deviceId did not boot in time")
    }

    fun awaitShutdown(deviceId: String) {
        MaestroTimer.withTimeout(30000) {
            if (list()
                    .devices
                    .values
                    .flatten()
                    .find { it.udid == deviceId }
                    ?.state != "Booted"
            ) true else null
        } ?: throw SimctlError("Device $deviceId did not boot in time")
    }

    fun launchSimulator(deviceId: String) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "boot",
                deviceId
            )
        )

        var exceptionToThrow: Exception? = null

        // Up to 10 iterations => max wait time of 1 second
        repeat(10) {
            try {
                runCommand(
                    listOf(
                        "open",
                        "-a",
                        "/Applications/Xcode.app/Contents/Developer/Applications/Simulator.app",
                        "--args",
                        "-CurrentDeviceUDID",
                        deviceId
                    )
                )
                return
            } catch (e: Exception) {
                exceptionToThrow = e
                Thread.sleep(100)
            }
        }

        exceptionToThrow?.let { throw it }
    }

    fun reboot(
        deviceId: String,
    ) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "shutdown",
                deviceId
            ),
            waitForCompletion = true
        )
        awaitShutdown(deviceId)

        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "boot",
                deviceId
            ),
            waitForCompletion = true
        )
        awaitLaunch(deviceId)
    }

    fun addTrustedCertificate(
        deviceId: String,
        certificate: File,
    ) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "keychain",
                deviceId,
                "add-root-cert",
                certificate.absolutePath,
            ),
            waitForCompletion = true
        )

        reboot(deviceId)
    }

    fun terminate(deviceId: String, bundleId: String) {
        // Ignore error return: terminate will fail if the app is not running
        ProcessBuilder(
            listOf(
                "xcrun",
                "simctl",
                "terminate",
                deviceId,
                bundleId
            )
        )
            .start()
            .waitFor()
    }

    fun clearAppState(deviceId: String, bundleId: String) {
        // Stop the app before clearing the file system
        // This prevents the app from saving its state after it has been cleared
        terminate(deviceId, bundleId)

        // Wait for the app to be stopped
        Thread.sleep(1500)

        // deletes app data, including container folder
        val appDataDirectory = getApplicationDataDirectory(deviceId, bundleId)
        ProcessBuilder(listOf("rm", "-rf", appDataDirectory)).start().waitFor()

        // forces app container folder to be re-created
        val paths = listOf(
            "Documents",
            "Library",
            "Library/Caches",
            "Library/Preferences",
            "SystemData",
            "tmp"
        )

        val command = listOf("mkdir", appDataDirectory) + paths.map { "$appDataDirectory/$it" }
        ProcessBuilder(command).start().waitFor()
    }

    private fun getApplicationDataDirectory(deviceId: String, bundleId: String): String {
        val process = ProcessBuilder(
            listOf(
                "xcrun",
                "simctl",
                "get_app_container",
                deviceId,
                bundleId,
                "data"
            )
        ).start()

        return String(process.inputStream.readBytes()).trimEnd()
    }


    fun launch(deviceId: String, bundleId: String) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "launch",
                deviceId,
                bundleId,
            )
        )
    }

    fun setLocation(deviceId: String, latitude: Double, longitude: Double) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "location",
                deviceId,
                "set",
                "$latitude,$longitude",
            )
        )
    }

    fun openURL(deviceId: String, url: String) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "openurl",
                deviceId,
                url,
            )
        )
    }

    fun uninstall(deviceId: String, bundleId: String) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "uninstall",
                deviceId,
                bundleId
            )
        )
    }

    fun clearKeychain(deviceId: String) {
        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "spawn",
                deviceId,
                "launchctl",
                "stop",
                "com.apple.securityd",
            )
        )

        runCommand(
            listOf(
                "rm", "-rf",
                "$homedir/Library/Developer/CoreSimulator/Devices/$deviceId/data/Library/Keychains"
            )
        )

        runCommand(
            listOf(
                "xcrun",
                "simctl",
                "spawn",
                deviceId,
                "launchctl",
                "start",
                "com.apple.securityd",
            )
        )
    }

    fun grantPermissions(deviceId: String, bundleId: String) {
        val permissions = listOf(
            "calendar=YES",
            "camera=YES",
            "contacts=YES",
            "faceid=YES",
            "health=YES",
            "homekit=YES",
            "location=always",
            "medialibrary=YES",
            "microphone=YES",
            "motion=YES",
            "notifications=YES",
            "photos=YES",
            "reminders=YES",
            "siri=YES",
            "speech=YES",
            "userTracking=YES",
        )

        runCommand(
            listOf(
                "$homedir/.maestro/deps/applesimutils",
                "--byId",
                deviceId,
                "--bundle",
                bundleId,
                "--setPermissions",
                permissions.joinToString(", ")
            )
        )
    }
}
