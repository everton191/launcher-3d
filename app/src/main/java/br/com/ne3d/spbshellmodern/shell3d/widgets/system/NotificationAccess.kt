package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import android.content.Context
import android.provider.Settings

object NotificationAccess {
 fun isAuthorized(context: Context): Boolean = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
  ?.split(':')?.any { it.contains(context.packageName) } == true
}
