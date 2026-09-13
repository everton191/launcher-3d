package br.com.ne3d.spbshellmodern.shell3d.widgets.personal

import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import java.util.concurrent.atomic.AtomicLong

data class CalendarEventValue(val id:String,val title:String,val startMillis:Long)
data class CalendarSnapshot(override val revision:Long,val available:Boolean,val events:List<CalendarEventValue>):WidgetSnapshot
data class PhotoValue(val id:String,val uri:String)
data class PhotosSnapshot(override val revision:Long,val available:Boolean,val photos:List<PhotoValue>):WidgetSnapshot
data class ContactValue(val id:String,val name:String)
data class ContactsSnapshot(override val revision:Long,val available:Boolean,val contacts:List<ContactValue>):WidgetSnapshot

class CalendarDataSource:LatestWidgetDataSource<CalendarSnapshot>(){private val r=AtomicLong();fun publishEvents(available:Boolean,events:List<CalendarEventValue>){publish(CalendarSnapshot(r.incrementAndGet(),available,events.take(5)))}}
class PhotosDataSource:LatestWidgetDataSource<PhotosSnapshot>(){private val r=AtomicLong();fun publishPhotos(available:Boolean,photos:List<PhotoValue>){publish(PhotosSnapshot(r.incrementAndGet(),available,photos.take(6)))}}
class ContactsDataSource:LatestWidgetDataSource<ContactsSnapshot>(){private val r=AtomicLong();fun publishContacts(available:Boolean,contacts:List<ContactValue>){publish(ContactsSnapshot(r.incrementAndGet(),available,contacts.take(6)))}}
