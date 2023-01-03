package flutter.overlay.window.flutter_overlay_window

import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.BasicMessageChannel

object WindowSetup {
    var height = WindowManager.LayoutParams.MATCH_PARENT
    var width = WindowManager.LayoutParams.MATCH_PARENT
    var flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    var gravity = Gravity.CENTER
    var messenger: BasicMessageChannel<Any>? = null
    var overlayTitle = "Overlay is activated"
    var overlayContent = "Tap to edit settings or disable"
    var positionGravity = "none"
    var notificationVisibility = NotificationCompat.VISIBILITY_PRIVATE
    var enableDrag = false

    fun setNotificationVisibility(value: String?) {
        if (value == null) return
        when (value) {
            "visibilityPublic" -> notificationVisibility = NotificationCompat.VISIBILITY_PUBLIC
            "visibilitySecret" -> notificationVisibility = NotificationCompat.VISIBILITY_SECRET
            "visibilityPrivate" -> notificationVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
    }

    fun setFlag(value: String?) {
        if (value == null) return
        when (value) {
            "defaultFlag", "flagNotFocusable" -> flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            "clickThrough", "flagNotTouchable" -> flag =
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            "focusPointer", "flagNotTouchModal" -> flag = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
    }

    fun setGravityFromAlignment(value: String?) {
        if (value == null) return
        when (value) {
            "topLeft" -> gravity = Gravity.TOP or Gravity.LEFT
            "topCenter" -> gravity = Gravity.TOP
            "topRight" -> gravity = Gravity.TOP or Gravity.RIGHT
            "centerLeft" -> gravity = Gravity.CENTER or Gravity.LEFT
            "center" -> gravity = Gravity.CENTER
            "centerRight" -> gravity = Gravity.CENTER or Gravity.RIGHT
            "bottomLeft" -> gravity = Gravity.BOTTOM or Gravity.LEFT
            "bottomCenter" -> gravity = Gravity.BOTTOM
            "bottomRight" -> gravity = Gravity.BOTTOM or Gravity.RIGHT
        }
    }
}