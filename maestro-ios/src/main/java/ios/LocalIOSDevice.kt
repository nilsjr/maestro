package ios

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.recoverIf
import hierarchy.XCUIElement
import ios.device.DeviceInfo
import ios.idb.IdbIOSDevice
import ios.simctl.SimctlIOSDevice
import ios.xctest.XCTestIOSDevice
import okio.Sink
import java.io.File
import java.io.InputStream

class LocalIOSDevice(
    override val deviceId: String?,
    private val idbIOSDevice: IdbIOSDevice,
    private val xcTestDevice: XCTestIOSDevice,
    private val simctlIOSDevice: SimctlIOSDevice,
) : IOSDevice {

    override fun open() {
        idbIOSDevice.open()
        xcTestDevice.open()
    }

    override fun deviceInfo(): Result<DeviceInfo, Throwable> {
        return idbIOSDevice.deviceInfo()
    }

    override fun contentDescriptor(): Result<XCUIElement, Throwable> {
        return xcTestDevice.contentDescriptor()
            .recoverIf(
                { it is XCTestIOSDevice.IllegalArgumentSnapshotFailure },
                {
                    idbIOSDevice.contentDescriptor()
                        .getOrThrow()
                }
            )
    }

    override fun tap(x: Int, y: Int): Result<Unit, Throwable> {
        return xcTestDevice.tap(x, y)
    }

    override fun longPress(x: Int, y: Int): Result<Unit, Throwable> {
        return idbIOSDevice.longPress(x, y)
    }

    override fun pressKey(code: Int): Result<Unit, Throwable> {
        return idbIOSDevice.pressKey(code)
    }

    override fun pressButton(code: Int): Result<Unit, Throwable> {
        return idbIOSDevice.pressButton(code)
    }

    override fun scroll(
        xStart: Double,
        yStart: Double,
        xEnd: Double,
        yEnd: Double,
        duration: Double
    ): Result<Unit, Throwable> {
        return xcTestDevice.scroll(xStart, yStart, xEnd, yEnd, duration)
    }

    override fun input(text: String): Result<Unit, Throwable> {
        return xcTestDevice.input(text)
    }

    override fun install(stream: InputStream): Result<Unit, Throwable> {
        return idbIOSDevice.install(stream)
    }

    override fun uninstall(id: String): Result<Unit, Throwable> {
        return simctlIOSDevice.uninstall(id)
    }

    override fun pullAppState(id: String, file: File): Result<Unit, Throwable> {
        return idbIOSDevice.pullAppState(id, file)
    }

    override fun pushAppState(id: String, file: File): Result<Unit, Throwable> {
        return idbIOSDevice.pushAppState(id, file)
    }

    override fun clearAppState(id: String): Result<Unit, Throwable> {
        return simctlIOSDevice.clearAppState(id)
    }

    override fun clearKeychain(): Result<Unit, Throwable> {
        return simctlIOSDevice.clearKeychain()
    }

    override fun launch(id: String): Result<Unit, Throwable> {
        return simctlIOSDevice.launch(id)
    }

    override fun stop(id: String): Result<Unit, Throwable> {
        return simctlIOSDevice.stop(id)
    }

    override fun openLink(link: String): Result<Unit, Throwable> {
        return simctlIOSDevice.openLink(link)
    }

    override fun takeScreenshot(out: Sink, compressed: Boolean): Result<Unit, Throwable> {
        return xcTestDevice.takeScreenshot(out, compressed)
    }

    override fun startScreenRecording(out: Sink): Result<IOSScreenRecording, Throwable> {
        return idbIOSDevice.startScreenRecording(out)
    }

    override fun setLocation(latitude: Double, longitude: Double): Result<Unit, Throwable> {
        return simctlIOSDevice.setLocation(latitude, longitude)
    }

    override fun isShutdown(): Boolean {
        return idbIOSDevice.isShutdown() && xcTestDevice.isShutdown()
    }

    override fun close() {
        idbIOSDevice.close()
        xcTestDevice.close()
    }

    override fun isScreenStatic(): Result<Boolean, Throwable> {
        return xcTestDevice.isScreenStatic()
    }
}
