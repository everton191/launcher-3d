package br.com.ne3d.spbshellmodern.shell3d.widgets.system
import org.junit.Assert.*
import org.junit.Test
class SystemDataSourcesTest { @Test fun `system normalizes values and notifications are bounded`(){val system=SystemDataSource();system.publishState(120,true,-4,true);assertEquals(100,system.latest()!!.batteryPercent);assertEquals(0,system.latest()!!.storagePercent);val notifications=NotificationDataSource();notifications.publishNotifications(false,List(7){NotificationValue("$it","t","x")});assertFalse(notifications.latest()!!.available);assertEquals(5,notifications.latest()!!.notifications.size)} }
