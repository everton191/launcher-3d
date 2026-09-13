package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import java.util.concurrent.atomic.AtomicLong

data class NotificationValue(val key:String,val title:String,val text:String)
data class NotificationSnapshot(override val revision:Long,val available:Boolean,val notifications:List<NotificationValue>):WidgetSnapshot
data class SystemSnapshot(override val revision:Long,val batteryPercent:Int,val charging:Boolean,val storagePercent:Int,val networkConnected:Boolean):WidgetSnapshot
class NotificationDataSource:LatestWidgetDataSource<NotificationSnapshot>() { private val r=AtomicLong(); fun publishNotifications(available:Boolean,values:List<NotificationValue>){ val next=available to values.take(5); val previous=latest()?.let { it.available to it.notifications }; if(next==previous)return; publish(NotificationSnapshot(r.incrementAndGet(),available,next.second)) } }
class SystemDataSource:LatestWidgetDataSource<SystemSnapshot>() { private val r=AtomicLong(); fun publishState(battery:Int,charging:Boolean,storage:Int,network:Boolean){ val next=SystemSnapshot(0,battery.coerceIn(0,100),charging,storage.coerceIn(0,100),network); val previous=latest(); if(previous?.let { it.batteryPercent==next.batteryPercent&&it.charging==next.charging&&it.storagePercent==next.storagePercent&&it.networkConnected==next.networkConnected }==true)return; publish(next.copy(revision=r.incrementAndGet())) } }
