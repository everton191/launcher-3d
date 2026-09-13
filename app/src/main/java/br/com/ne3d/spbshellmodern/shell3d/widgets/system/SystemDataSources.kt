package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import java.util.concurrent.atomic.AtomicLong

data class NotificationValue(val key:String,val title:String,val text:String)
data class NotificationSnapshot(override val revision:Long,val available:Boolean,val notifications:List<NotificationValue>):WidgetSnapshot
data class SystemSnapshot(override val revision:Long,val batteryPercent:Int,val charging:Boolean,val storagePercent:Int,val networkConnected:Boolean):WidgetSnapshot
class NotificationDataSource:LatestWidgetDataSource<NotificationSnapshot>(){private val r=AtomicLong();fun publishNotifications(available:Boolean,values:List<NotificationValue>){publish(NotificationSnapshot(r.incrementAndGet(),available,values.take(5)))}}
class SystemDataSource:LatestWidgetDataSource<SystemSnapshot>(){private val r=AtomicLong();fun publishState(battery:Int,charging:Boolean,storage:Int,network:Boolean){publish(SystemSnapshot(r.incrementAndGet(),battery.coerceIn(0,100),charging,storage.coerceIn(0,100),network))}}
