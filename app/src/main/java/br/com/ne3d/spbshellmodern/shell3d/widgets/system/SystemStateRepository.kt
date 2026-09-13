package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.StatFs
import android.net.ConnectivityManager

/** One-shot system sampling for the host; scenes only see SystemSnapshot. */
class SystemStateRepository(private val context: Context) {
 fun publish(source:SystemDataSource){
  val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
  val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,0)?:0;val scale=(battery?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100).coerceAtLeast(1)
  val charging=(battery?.getIntExtra(BatteryManager.EXTRA_STATUS,0)?:0).let{it==BatteryManager.BATTERY_STATUS_CHARGING||it==BatteryManager.BATTERY_STATUS_FULL}
  val stat=StatFs(context.filesDir.absolutePath);val total=stat.blockCountLong*stat.blockSizeLong;val free=stat.availableBlocksLong*stat.blockSizeLong
  val storage=if(total==0L)0 else (((total-free)*100)/total).toInt()
  val connected=context.getSystemService(ConnectivityManager::class.java).activeNetwork!=null
  source.publishState(level*100/scale,charging,storage,connected)
 }
}
