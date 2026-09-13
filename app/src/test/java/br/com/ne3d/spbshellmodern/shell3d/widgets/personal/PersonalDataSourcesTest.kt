package br.com.ne3d.spbshellmodern.shell3d.widgets.personal
import org.junit.Assert.*
import org.junit.Test
class PersonalDataSourcesTest {
 @Test fun `limits personal payloads`() { val c=CalendarDataSource();c.publishEvents(true,List(7){CalendarEventValue("$it","e",it.toLong())});assertEquals(5,c.latest()!!.events.size);val p=PhotosDataSource();p.publishPhotos(false,List(8){PhotoValue("$it","u")});assertFalse(p.latest()!!.available);assertEquals(6,p.latest()!!.photos.size);val contacts=ContactsDataSource();contacts.publishContacts(true,List(8){ContactValue("$it","n")});assertEquals(6,contacts.latest()!!.contacts.size) }
}
