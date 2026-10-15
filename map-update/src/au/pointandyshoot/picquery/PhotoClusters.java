package au.pointandyshoot.picquery;

import android.graphics.*;
import android.view.MotionEvent;
import java.util.*;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.util.GeoPoint;

/** Screen-space clusters with direct browsing at every zoom level. */
public final class PhotoClusters extends Overlay {
    private List<LocatedPhoto> photos=Collections.emptyList();
    private final Function1<List<LocatedPhoto>,Unit> select;
    private final Paint paint=new Paint(3), text=new Paint(3);
    private final List<Group> drawn=new ArrayList<>();
    static final class Group { float x,y; List<LocatedPhoto> photos=new ArrayList<>(); }
    public PhotoClusters(Function1<List<LocatedPhoto>,Unit> select){this.select=select;text.setColor(Color.WHITE);text.setTextAlign(Paint.Align.CENTER);}
    public List<LocatedPhoto> getPhotos(){return photos;}
    public void setPhotos(List<LocatedPhoto> value){photos=value==null?Collections.emptyList():value;}
    public List<LocatedPhoto> visible(MapView map){
        org.osmdroid.util.BoundingBox b=map.getBoundingBox();
        List<LocatedPhoto> result=new ArrayList<>();
        for(LocatedPhoto p:photos)if(inside(p.getLatitude(),p.getLongitude(),b.getLatNorth(),b.getLonEast(),b.getLatSouth(),b.getLonWest()))result.add(p);
        return result;
    }
    public static boolean inside(double lat,double lon,double north,double east,double south,double west){
        return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=south&&lat<=north&&(west<=east?lon>=west&&lon<=east:lon>=west||lon<=east);
    }
    @Override public void draw(Canvas canvas,MapView map,boolean shadow){
        if(shadow)return;
        float density=map.getResources().getDisplayMetrics().density;int cell=Math.max(1,(int)(64*density));
        Map<Long,Group> groups=new LinkedHashMap<>();
        for(LocatedPhoto p:photos){
            Point pt=map.getProjection().toPixels(new GeoPoint(p.getLatitude(),p.getLongitude()),null);
            if(pt.x< -cell||pt.y< -cell||pt.x>map.getWidth()+cell||pt.y>map.getHeight()+cell)continue;
            long key=((long)Math.floorDiv(pt.x,cell)<<32)^(Math.floorDiv(pt.y,cell)&0xffffffffL);
            Group g=groups.get(key);if(g==null){g=new Group();groups.put(key,g);}g.x+=pt.x;g.y+=pt.y;g.photos.add(p);
        }
        drawn.clear();drawn.addAll(groups.values());text.setTextSize(13*density);
        for(Group g:drawn){g.x/=g.photos.size();g.y/=g.photos.size();float r=23*density;
            Bitmap image=MapPhotos.cached(g.photos.get(0).getUri());
            paint.setColor(Color.WHITE);canvas.drawRoundRect(g.x-r-2*density,g.y-r-2*density,g.x+r+2*density,g.y+r+2*density,5*density,5*density,paint);
            paint.setColor(0xff216355);canvas.drawRoundRect(g.x-r,g.y-r,g.x+r,g.y+r,4*density,4*density,paint);
            if(image!=null){int side=Math.min(image.getWidth(),image.getHeight());int x=(image.getWidth()-side)/2,y=(image.getHeight()-side)/2;canvas.drawBitmap(image,new Rect(x,y,x+side,y+side),new RectF(g.x-r,g.y-r,g.x+r,g.y+r),paint);}
            else MapPhotos.thumbnail(map.getContext(),g.photos.get(0).getUri(),map::invalidate);
            if(g.photos.size()>1){paint.setColor(0xff164f43);canvas.drawCircle(g.x+r-3*density,g.y-r+3*density,13*density,paint);canvas.drawText(Integer.toString(g.photos.size()),g.x+r-3*density,g.y-r+3*density-(text.ascent()+text.descent())/2,text);}
        }
    }
    @Override public boolean onSingleTapConfirmed(MotionEvent event,MapView map){
        float radius=34*map.getResources().getDisplayMetrics().density;Group nearest=null;double distance=radius;
        for(Group g:drawn){double d=Math.hypot(event.getX()-g.x,event.getY()-g.y);if(d<distance){nearest=g;distance=d;}}
        if(nearest==null)return false;
        MapPhotos.gallery(map.getContext(),nearest.photos,"Photos at this marker");return true;
    }
}
