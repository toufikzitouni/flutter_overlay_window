package flutter.overlay.window.flutter_overlay_window

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.FlutterEngineGroup
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.JSONMessageCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener


class FlutterOverlayWindowPlugin : FlutterPlugin, ActivityAware,
    BasicMessageChannel.MessageHandler<Any?>, MethodCallHandler, ActivityResultListener {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private lateinit var mActivity: Activity

    private var messenger: BasicMessageChannel<Any>? = null
    private var pendingResult: MethodChannel.Result? = null

    private val REQUEST_CODE_FOR_OVERLAY_PERMISSION = 1248

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, OverlayConstants.CHANNEL_TAG)
        channel.setMethodCallHandler(this)

        messenger = BasicMessageChannel(
            flutterPluginBinding.binaryMessenger,
            OverlayConstants.MESSENGER_TAG,
            JSONMessageCodec.INSTANCE,
        )
        messenger!!.setMessageHandler(this)

        WindowSetup.messenger = messenger
        WindowSetup.messenger!!.setMessageHandler(this)
    }


    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mActivity = binding.activity
        val engineGroup = FlutterEngineGroup(context)
        val dartEntrypoint = DartEntrypoint(
            FlutterInjector.instance().flutterLoader().findAppBundlePath(),
            "overlayMain",
        )
        val engine = engineGroup.createAndRunEngine(context, dartEntrypoint)
        FlutterEngineCache.getInstance().put(OverlayConstants.CACHED_TAG, engine)
        binding.addActivityResultListener(this)
    }

    override fun onMessage(message: Any?, reply: BasicMessageChannel.Reply<Any?>) {
        val overlayMessageChannel: BasicMessageChannel<Any> = BasicMessageChannel(
            FlutterEngineCache.getInstance()[OverlayConstants.CACHED_TAG]!!.dartExecutor,
            OverlayConstants.MESSENGER_TAG,
            JSONMessageCodec.INSTANCE,
        )
        overlayMessageChannel.send(message, reply)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) =
        when (call.method) {
            "checkPermission" -> result.success(checkOverlayPermission())
            "requestPermission" -> requestOverlayPermission(result)
            "showOverlay" -> showOverlay(call, result)
            "isOverlayActive" -> result.success(OverlayService.isRunning)
            "closeOverlay" -> result.success(closeOverlay())
            else -> result.notImplemented()
        }


    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    private fun requestOverlayPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:" + mActivity.packageName)
            mActivity.startActivityForResult(intent, REQUEST_CODE_FOR_OVERLAY_PERMISSION)
            pendingResult = result
        } else {
            result.success(true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_FOR_OVERLAY_PERMISSION) {
            pendingResult?.success(checkOverlayPermission())
            return true
        }
        return false
    }

    private fun showOverlay(call: MethodCall, result: MethodChannel.Result) {
        if (!checkOverlayPermission()) {
            result.error("PERMISSION", "overlay permission is not enabled", null)
            return
        }
        // update window config
        WindowSetup.width = call.argument<Int>("height") ?: -1
        WindowSetup.height = call.argument<Int>("width") ?: -1
        WindowSetup.enableDrag = call.argument<Boolean>("enableDrag") ?: false
        WindowSetup.setGravityFromAlignment(call.argument<String>("alignment"))
        WindowSetup.setFlag(call.argument<String>("flag"))
        WindowSetup.overlayTitle = call.argument<String>("overlayTitle")!!
        WindowSetup.overlayContent = call.argument<String>("overlayContent") ?: ""
        WindowSetup.positionGravity = call.argument<String>("positionGravity") ?: "none"
        WindowSetup.setNotificationVisibility(call.argument<String>("notificationVisibility"))
        // create overlay intent
        val intent = Intent(context, OverlayService::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // start overlay service in foreground if app isn't running
        if (appInBackground() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("OverlayPlugin", "Starting service in foreground..")
            context.startForegroundService(intent)
        } else {
            // start overlay service in background
            Log.d("OverlayPlugin", "Starting service in background..")
            context.startService(intent)
        }
        // service started, notify from Result
        result.success(null)
    }

    private fun appInBackground(): Boolean {
        val myProcess = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(myProcess)
        return myProcess.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun closeOverlay(): Boolean {
        if (OverlayService.isRunning) {
            val intent = Intent(context, OverlayService::class.java)
            context.stopService(intent)
            return true
        }
        return false
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        WindowSetup.messenger?.setMessageHandler(null)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        mActivity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onDetachedFromActivity() {}
}