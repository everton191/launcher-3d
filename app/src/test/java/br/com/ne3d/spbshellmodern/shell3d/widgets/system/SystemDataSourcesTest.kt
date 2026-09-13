package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import org.junit.Assert.*
import org.junit.Test

class SystemDataSourcesTest {
 @Test fun `system normalizes values and notifications are bounded`() { val system=SystemDataSource(); system.publishState(120,true,-4,true); assertEquals(100,system.latest()!!.batteryPercent); assertEquals(0,system.latest()!!.storagePercent); val notifications=NotificationDataSource(); notifications.publishNotifications(false,List(7){NotificationValue("$it","t","x")}); assertFalse(notifications.latest()!!.available); assertEquals(5,notifications.latest()!!.notifications.size) }
 @Test fun `system only publishes changed normalized state`() { val source=SystemDataSource(); source.publishState(100,false,80,true); val revision=source.latest()!!.revision; source.publishState(150,false,80,true); assertEquals(revision,source.latest()!!.revision); source.publishState(0,true,100,false); assertTrue(source.latest()!!.charging); assertEquals(0,source.latest()!!.batteryPercent); assertEquals(100,source.latest()!!.storagePercent); assertFalse(source.latest()!!.networkConnected) }
}
