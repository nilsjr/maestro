package dev.mobile.maestro

import android.app.UiAutomation
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.graphics.Bitmap
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent.KEYCODE_1
import android.view.KeyEvent.KEYCODE_4
import android.view.KeyEvent.KEYCODE_5
import android.view.KeyEvent.KEYCODE_6
import android.view.KeyEvent.KEYCODE_7
import android.view.KeyEvent.KEYCODE_APOSTROPHE
import android.view.KeyEvent.KEYCODE_AT
import android.view.KeyEvent.KEYCODE_BACKSLASH
import android.view.KeyEvent.KEYCODE_COMMA
import android.view.KeyEvent.KEYCODE_EQUALS
import android.view.KeyEvent.KEYCODE_GRAVE
import android.view.KeyEvent.KEYCODE_LEFT_BRACKET
import android.view.KeyEvent.KEYCODE_MINUS
import android.view.KeyEvent.KEYCODE_NUMPAD_ADD
import android.view.KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN
import android.view.KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN
import android.view.KeyEvent.KEYCODE_PERIOD
import android.view.KeyEvent.KEYCODE_POUND
import android.view.KeyEvent.KEYCODE_RIGHT_BRACKET
import android.view.KeyEvent.KEYCODE_SEMICOLON
import android.view.KeyEvent.KEYCODE_SLASH
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.KEYCODE_STAR
import android.view.KeyEvent.META_SHIFT_LEFT_ON
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiDeviceExt.clickExt
import com.google.protobuf.ByteString
import dev.mobile.maestro.sdk.SdkService
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import maestro_android.MaestroAndroid
import maestro_android.MaestroDriverGrpc
import maestro_android.deviceInfo
import maestro_android.eraseAllTextResponse
import maestro_android.inputTextResponse
import maestro_android.screenshotResponse
import maestro_android.setLocationResponse
import maestro_android.tapResponse
import maestro_android.viewHierarchyResponse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MaestroDriverService {

    @Test
    fun grpcServer() {
        // TODO accept session ID from Maestro client instead
        SdkService.initSession(UUID.randomUUID().toString())
        SdkService.startService()

        Configurator.getInstance()
            .setActionAcknowledgmentTimeout(0L)
            .setWaitForIdleTimeout(0L)
            .setWaitForSelectorTimeout(0L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiDevice = UiDevice.getInstance(instrumentation)
        val uiAutomation = instrumentation.uiAutomation

        NettyServerBuilder.forPort(7001)
            .addService(Service(uiDevice, uiAutomation))
            .build()
            .start()

        while (!Thread.interrupted()) {
            Thread.sleep(100)
        }
    }

}

class Service(
    private val uiDevice: UiDevice,
    private val uiAutomation: UiAutomation,
) : MaestroDriverGrpc.MaestroDriverImplBase() {

    override fun deviceInfo(
        request: MaestroAndroid.DeviceInfoRequest,
        responseObserver: StreamObserver<MaestroAndroid.DeviceInfo>
    ) {
        val windowManager = InstrumentationRegistry.getInstrumentation()
            .context
            .getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        responseObserver.onNext(
            deviceInfo {
                widthPixels = displayMetrics.widthPixels
                heightPixels = displayMetrics.heightPixels
            }
        )
        responseObserver.onCompleted()
    }

    override fun viewHierarchy(
        request: MaestroAndroid.ViewHierarchyRequest,
        responseObserver: StreamObserver<MaestroAndroid.ViewHierarchyResponse>
    ) {
        val stream = ByteArrayOutputStream()

        Log.d("Maestro", "Requesting view hierarchy")
        val ms = measureTimeMillis {
            ViewHierarchy.dump(
                uiDevice,
                uiAutomation,
                stream
            )
        }
        Log.d("Maestro", "View hierarchy received in $ms ms")

        responseObserver.onNext(
            viewHierarchyResponse {
                hierarchy = stream.toString(Charsets.UTF_8.name())
            }
        )
        responseObserver.onCompleted()
    }

    override fun tap(
        request: MaestroAndroid.TapRequest,
        responseObserver: StreamObserver<MaestroAndroid.TapResponse>
    ) {
        uiDevice.clickExt(
            request.x,
            request.y
        )

        responseObserver.onNext(tapResponse {})
        responseObserver.onCompleted()
    }

    override fun eraseAllText(
        request: MaestroAndroid.EraseAllTextRequest,
        responseObserver: StreamObserver<MaestroAndroid.EraseAllTextResponse>
    ) {
        val charactersToErase = request.charactersToErase
        Log.d("Maestro", "Erasing text $charactersToErase")

        for (i in 0..charactersToErase) {
            uiDevice.pressDelete()
        }

        responseObserver.onNext(eraseAllTextResponse { })
        responseObserver.onCompleted()
    }

    override fun inputText(
        request: MaestroAndroid.InputTextRequest,
        responseObserver: StreamObserver<MaestroAndroid.InputTextResponse>
    ) {
        Log.d("Maestro", "Inputting text")
        request.text.forEach {
            setText(it.toString())
            Thread.sleep(75)
        }

        responseObserver.onNext(inputTextResponse { })
        responseObserver.onCompleted()
    }

    override fun screenshot(
        request: MaestroAndroid.ScreenshotRequest,
        responseObserver: StreamObserver<MaestroAndroid.ScreenshotResponse>
    ) {
        val outputStream = ByteString.newOutput()
        val bitmap = uiAutomation.takeScreenshot()
        if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
            responseObserver.onNext(screenshotResponse {
                bytes = outputStream.toByteString()
            })
            responseObserver.onCompleted()
        } else {
            Log.e("Maestro", "Failed to compress bitmap")
            responseObserver.onError(Throwable("Failed to compress bitmap"))
        }
    }

    override fun setLocation(
        request: MaestroAndroid.SetLocationRequest,
        responseObserver: StreamObserver<MaestroAndroid.SetLocationResponse>
    ) {
        val latitude = request.latitude
        val longitude = request.longitude
        val accuracy = 0.0F

        val locMgr = InstrumentationRegistry.getInstrumentation()
            .context
            .getSystemService(LOCATION_SERVICE) as LocationManager

        locMgr.addTestProvider(
            LocationManager.GPS_PROVIDER,
            "requiresNetwork" === "",
            "requiresSatellite" === "",
            "requiresCell" === "",
            "hasMonetaryCost" === "",
            "supportsAltitude" === "",
            "supportsSpeed" === "",
            "supportsBearing" === "",
            Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE
        )

        val newLocation = Location(LocationManager.GPS_PROVIDER)

        newLocation.latitude = latitude
        newLocation.longitude = longitude
        newLocation.accuracy = accuracy
        newLocation.altitude = 0.0
        newLocation.accuracy = 500F
        locMgr.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val handler = Handler(Looper.getMainLooper())

        fun postLocation() {
            handler.post {
                newLocation.setTime(System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    newLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                locMgr.setTestProviderStatus(
                    LocationManager.GPS_PROVIDER,
                    LocationProvider.AVAILABLE,
                    null, System.currentTimeMillis()
                )

                locMgr.setTestProviderLocation(LocationManager.GPS_PROVIDER, newLocation)

                postLocation()
            }
        }

        postLocation()

        responseObserver.onNext(setLocationResponse { })
        responseObserver.onCompleted()
    }

    private fun setText(text: String) {
        for (element in text) {
            Log.d("Maestro", element.code.toString())
            when (element.code) {
                in 48..57 -> {
                    /** 0~9 **/
                    uiDevice.pressKeyCode(element.code - 41)
                }
                in 65..90 -> {
                    /** A~Z **/
                    uiDevice.pressKeyCode(element.code - 36, 1)
                }
                in 97..122 -> {
                    /** a~z **/
                    uiDevice.pressKeyCode(element.code - 68)
                }
                ';'.code -> uiDevice.pressKeyCode(KEYCODE_SEMICOLON)
                '='.code -> uiDevice.pressKeyCode(KEYCODE_EQUALS)
                ','.code -> uiDevice.pressKeyCode(KEYCODE_COMMA)
                '-'.code -> uiDevice.pressKeyCode(KEYCODE_MINUS)
                '.'.code -> uiDevice.pressKeyCode(KEYCODE_PERIOD)
                '/'.code -> uiDevice.pressKeyCode(KEYCODE_SLASH)
                '`'.code -> uiDevice.pressKeyCode(KEYCODE_GRAVE)
                '\''.code -> uiDevice.pressKeyCode(KEYCODE_APOSTROPHE)
                '['.code -> uiDevice.pressKeyCode(KEYCODE_LEFT_BRACKET)
                ']'.code -> uiDevice.pressKeyCode(KEYCODE_RIGHT_BRACKET)
                '\\'.code -> uiDevice.pressKeyCode(KEYCODE_BACKSLASH)
                ' '.code -> uiDevice.pressKeyCode(KEYCODE_SPACE)
                '@'.code -> uiDevice.pressKeyCode(KEYCODE_AT)
                '#'.code -> uiDevice.pressKeyCode(KEYCODE_POUND)
                '*'.code -> uiDevice.pressKeyCode(KEYCODE_STAR)
                '('.code -> uiDevice.pressKeyCode(KEYCODE_NUMPAD_LEFT_PAREN)
                ')'.code -> uiDevice.pressKeyCode(KEYCODE_NUMPAD_RIGHT_PAREN)
                '+'.code -> uiDevice.pressKeyCode(KEYCODE_NUMPAD_ADD)
                '!'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_1)
                '$'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_4)
                '%'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_5)
                '^'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_6)
                '&'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_7)
                '"'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_APOSTROPHE)
                '{'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_LEFT_BRACKET)
                '}'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_RIGHT_BRACKET)
                ':'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_SEMICOLON)
                '|'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_BACKSLASH)
                '<'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_COMMA)
                '>'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_PERIOD)
                '?'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_SLASH)
                '~'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_GRAVE)
                '_'.code -> keyPressShiftedToEvents(uiDevice, KEYCODE_MINUS)
            }
        }
    }

    private fun keyPressShiftedToEvents(uiDevice: UiDevice, keyCode: Int) {
        uiDevice.pressKeyCode(keyCode, META_SHIFT_LEFT_ON)
    }
}
