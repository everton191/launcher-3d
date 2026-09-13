package br.com.ne3d.spbshellmodern.shell3d.widgets.interaction

sealed interface WidgetInteraction { data class Tap(val x: Float,val y: Float): WidgetInteraction; data class LongPress(val x: Float,val y: Float): WidgetInteraction; data class DragStart(val x: Float,val y: Float): WidgetInteraction; data class Drag(val dx: Float,val dy: Float): WidgetInteraction; data object DragEnd: WidgetInteraction }
data class InteractionRegion(val id: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val draggable: Boolean = false, val onInteraction: (WidgetInteraction) -> Unit) { fun contains(x: Float,y: Float)=x>=left&&x<=right&&y>=top&&y<=bottom }
/** Local/projectable bounds; Android views and carousel gesture ownership are intentionally absent. */
class InteractionMap {
    private val regions=ArrayList<InteractionRegion>(); private var active: InteractionRegion?=null
    fun add(region: InteractionRegion) { regions.add(region) }
    fun clear() { regions.clear(); active=null }
    fun dispatch(event: WidgetInteraction): Boolean = when(event) { is WidgetInteraction.Tap -> hit(event.x,event.y)?.also{it.onInteraction(event)}!=null; is WidgetInteraction.LongPress -> hit(event.x,event.y)?.also{it.onInteraction(event)}!=null; is WidgetInteraction.DragStart -> hit(event.x,event.y)?.takeIf{it.draggable}?.also{active=it;it.onInteraction(event)}!=null; is WidgetInteraction.Drag -> active?.also{it.onInteraction(event)}!=null; WidgetInteraction.DragEnd -> active?.also{it.onInteraction(event);active=null}!=null }
    private fun hit(x:Float,y:Float):InteractionRegion? { var i=regions.size-1;while(i>=0){if(regions[i].contains(x,y))return regions[i];i--};return null }
}
