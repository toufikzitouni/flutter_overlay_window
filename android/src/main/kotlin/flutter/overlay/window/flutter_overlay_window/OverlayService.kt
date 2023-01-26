package flutter.overlay.window.flutter_overlay_window

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.flutter_overlay_window.R
import io.flutter.FlutterInjector
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.FlutterEngineGroup
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.JSONMessageCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*
import kotlin.math.abs

class OverlayService : Service(), OnTouchListener {
    private lateinit var mContext: Context
    private lateinit var flutterView: FlutterView
    private var windowManager: WindowManager? = null
    private lateinit var flutterChannel: MethodChannel
    private lateinit var overlayMessageChannel: BasicMessageChannel<Any>
    private val clickableFlag =
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private val mAnimationHandler = Handler(Looper.getMainLooper())
    private var lastX = 0f
    private var lastY = 0f
    private var firstX = 0f
    private var firstY = 0f
    private var lastYPosition = 0
    private var dragging = false
    private val szWindow = Point()
    private var mTrayAnimationTimer: Timer? = null
    private var mTrayTimerTask: TrayAnimationTimerTask? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        mContext = applicationContext
        // flutter and message channel
        val engine = getFlutterEngine()
        flutterChannel = MethodChannel(
            engine.dartExecutor,
            OverlayConstants.OVERLAY_TAG,
        )
        overlayMessageChannel = BasicMessageChannel(
            engine.dartExecutor,
            OverlayConstants.MESSENGER_TAG,
            JSONMessageCodec.INSTANCE,
        )
        // create channel for notification
        createNotificationChannel()
        // notification intent
        val notificationIntent = Intent(this, FlutterOverlayWindowPlugin::class.java)
        val pendingFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this,
            0, notificationIntent, pendingFlags)
        val notifyIcon = getDrawableResourceId("mipmap", "launcher")
        val notification = NotificationCompat.Builder(this, OverlayConstants.CHANNEL_ID)
            .setContentTitle(WindowSetup.overlayTitle)
            .setContentText(WindowSetup.overlayContent)
            .setSmallIcon(if (notifyIcon == 0) R.drawable.notification_icon else notifyIcon)
            .setContentIntent(pendingIntent)
            .setVisibility(WindowSetup.notificationVisibility)
            .build()
        startForeground(OverlayConstants.NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                OverlayConstants.CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        isRunning = true
        Log.d("onStartCommand", "Service started")

        val engine = getFlutterEngine()
        engine.lifecycleChannel.appIsResumed()

        flutterView = FlutterView(applicationContext, FlutterTextureView(
            applicationContext))
        flutterView.attachToFlutterEngine(FlutterEngineCache.getInstance()[OverlayConstants.CACHED_TAG]!!)
        flutterView.fitsSystemWindows = true
        flutterView.isFocusable = true
        flutterView.isFocusableInTouchMode = true
        flutterView.setBackgroundColor(Color.TRANSPARENT)

        flutterChannel.setMethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
            when (call.method) {
                "updateFlag" -> {
                    val flag = call.argument<Any>("flag").toString()
                    updateOverlayFlag(result, flag)
                }
                "resizeOverlay" -> {
                    val width = call.argument<Int>("width")!!
                    val height = call.argument<Int>("height")!!
                    resizeOverlay(width, height, result)
                }
                "updateDrag" -> {
                    val enableDrag = call.argument<Boolean>("enableDrag")!!
                    updateDrag(enableDrag, result)
                }
            }
        }
        overlayMessageChannel.setMessageHandler { message: Any?, _: BasicMessageChannel.Reply<Any?>? ->
            WindowSetup.messenger?.send(message)
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager!!.currentWindowMetrics.bounds
            szWindow.set(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            windowManager!!.defaultDisplay.getSize(szWindow)
        }


        val params = WindowManager.LayoutParams(
            WindowSetup.width,
            WindowSetup.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowSetup.flag or WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag == clickableFlag) {
            params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
        }
        params.gravity = WindowSetup.gravity
        flutterView.setOnTouchListener(this)
        windowManager!!.addView(flutterView, params)
        return START_STICKY
    }

    private fun getFlutterEngine(): FlutterEngine {
        val engineCache = FlutterEngineCache.getInstance()
        // engine present in cache
        if (engineCache.contains(OverlayConstants.CACHED_TAG)) {
            Log.d(TAG, "cached engine found!")
            return engineCache[OverlayConstants.CACHED_TAG]!!
        }
        // engine removed from cache, create a new one
        else {
            Log.i(TAG, "engine not found in cache, creating one!")
            // ensure flutter initialization
            val loader = FlutterInjector.instance().flutterLoader().also {
                it.startInitialization(mContext)
                it.ensureInitializationComplete(mContext, arrayOf())
            }
            // create engine
            val engineGroup = FlutterEngineGroup(mContext)
            val entrypoint = DartExecutor.DartEntrypoint(
                loader.findAppBundlePath(),
                "overlayMain",
            )
            val engine = engineGroup.createAndRunEngine(mContext, entrypoint)
            // put engine in cache
            engineCache.put(OverlayConstants.CACHED_TAG, engine)

            return engine
        }
    }

    private fun updateOverlayFlag(result: MethodChannel.Result, flag: String) {
        if (windowManager != null) {
            WindowSetup.setFlag(flag)
            val params = flutterView.layoutParams as WindowManager.LayoutParams
            params.flags = WindowSetup.flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag == clickableFlag) {
                params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
            }
            windowManager!!.updateViewLayout(flutterView, params)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun resizeOverlay(width: Int, height: Int, result: MethodChannel.Result) {
        if (windowManager != null) {
            val params = flutterView.layoutParams as WindowManager.LayoutParams
            params.width = width
            params.height = height
            windowManager!!.updateViewLayout(flutterView, params)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun updateDrag(enableDrag: Boolean, result: MethodChannel.Result) {
        WindowSetup.enableDrag = enableDrag
        if (!enableDrag) {
            if (windowManager != null) {
                val params = flutterView.layoutParams as WindowManager.LayoutParams
                params.x = 0
                params.y = 0
                windowManager!!.updateViewLayout(flutterView, params)
                result.success(true)
            } else {
                result.success(false)
            }
        } else {
            result.success(true)
        }
    }

    private fun getDrawableResourceId(resType: String, name: String): Int {
        return applicationContext.resources.getIdentifier(String.format("ic_%s", name),
            resType,
            applicationContext.packageName)
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (WindowSetup.enableDrag) {
            val params = flutterView.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    lastX = event.rawX
                    lastY = event.rawY
                    firstX = event.rawX
                    firstY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    if (!dragging && dx * dx + dy * dy < 25) {
                        return false
                    }
                    lastX = event.rawX
                    lastY = event.rawY
                    val xx = params.x + dx.toInt()
                    val yy = params.y + dy.toInt()
                    params.x = xx
                    params.y = yy
                    windowManager!!.updateViewLayout(flutterView, params)
                    dragging = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - firstX
                    val dy = event.rawY - firstY
                    if (dx == 0f && dy == 0f) {
                        overlayMessageChannel.send("bubbleClick", null)
                    }
                    lastYPosition = params.y
                    if (WindowSetup.positionGravity !== "none") {
                        windowManager!!.updateViewLayout(flutterView, params)
                        mTrayTimerTask = TrayAnimationTimerTask()
                        mTrayAnimationTimer = Timer()
                        mTrayAnimationTimer!!.schedule(mTrayTimerTask, 0, 25)
                    }
                    return dragging
                }
                else -> return false
            }
            return false
        }
        return false
    }

    private inner class TrayAnimationTimerTask : TimerTask() {
        var mDestX = 0
        var mDestY: Int
        var params = flutterView.layoutParams as WindowManager.LayoutParams

        init {
            mDestY = lastYPosition
            when (WindowSetup.positionGravity) {
                "auto" -> mDestX = if (params.x + flutterView.width / 2 <= szWindow.x / 2) 0
                                    else szWindow.x - flutterView.width
                "left" ->  mDestX = 0
                "right" -> mDestX = szWindow.x - flutterView.width
                else -> {
                    mDestX = params.x
                    mDestY = params.y
                }
            }
        }

        override fun run() {
            mAnimationHandler.post {
                params.x = 2 * (params.x - mDestX) / 3 + mDestX
                params.y = 2 * (params.y - mDestY) / 3 + mDestY
                windowManager!!.updateViewLayout(flutterView, params)
                if (abs(params.x - mDestX) < 2 && abs(params.y - mDestY) < 2) {
                    cancel()
                    mTrayAnimationTimer?.cancel()
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d("OverLay", "Destroying the overlay window service")
        isRunning = false
        // remove flutter view
        windowManager?.removeView(flutterView)
        // detach engine
        flutterView.detachFromFlutterEngine()
        FlutterEngineCache.getInstance()[OverlayConstants.CACHED_TAG]?.lifecycleChannel?.appIsDetached()

        val notificationManager = applicationContext.getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager
        notificationManager.cancel(OverlayConstants.NOTIFICATION_ID)
    }

    companion object {
        var isRunning = false
        private const val TAG = "OverlayService"
        private const val MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER = 0.8f
    }
}