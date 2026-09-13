package br.com.ne3d.spbshellmodern.shell3d.widgets.personal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/** Android-side readers; never used by WidgetScene or renderer. */
class PersonalWidgetRepository(private val context: Context) {
 fun calendar(): Pair<Boolean,List<CalendarEventValue>> { if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)return false to emptyList(); val out=ArrayList<CalendarEventValue>(); context.contentResolver.query(CalendarContract.Instances.CONTENT_URI,arrayOf(CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN),null,null,"${CalendarContract.Instances.BEGIN} ASC")?.use{c->while(c.moveToNext()&&out.size<5)out+=CalendarEventValue(c.getString(0),c.getString(1).orEmpty(),c.getLong(2))};return true to out }
 fun photos(): Pair<Boolean,List<PhotoValue>> { if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED)return false to emptyList(); val out=ArrayList<PhotoValue>(); context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID),null,null,"${MediaStore.Images.Media.DATE_ADDED} DESC")?.use{c->while(c.moveToNext()&&out.size<6){val id=c.getLong(0);out+=PhotoValue(id.toString(),"content://media/external/images/media/$id")}};return true to out }
 fun contacts(): Pair<Boolean,List<ContactValue>> { if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return false to emptyList(); val out=ArrayList<ContactValue>();context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI,arrayOf(ContactsContract.Contacts._ID,ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),null,null,"${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC")?.use{c->while(c.moveToNext()&&out.size<6)out+=ContactValue(c.getString(0),c.getString(1).orEmpty())};return true to out }
}
