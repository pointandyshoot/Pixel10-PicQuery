import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import au.pointandyshoot.picquery.*;
import org.osmdroid.views.MapView;
import org.osmdroid.config.Configuration;
import java.io.File;
import java.util.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE,application=Application.class)
public class MapUiTest {
 Activity activity;MapView map;MapShell shell;
 @Before public void setup(){
  activity=Robolectric.buildActivity(Activity.class).setup().get();
  Configuration.getInstance().setOsmdroidBasePath(new File(activity.getCacheDir(),"maps"));
  Configuration.getInstance().setOsmdroidTileCache(new File(activity.getCacheDir(),"maps/tiles"));
  activity.getSharedPreferences("maps",0).edit().clear().commit();
  map=new MapView(activity);map.setUseDataConnection(false);shell=(MapShell)MapShell.wrap(map);activity.setContentView(shell);
  shell.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));shell.layout(0,0,600,400);
 }
 @After public void clean(){map.onDetach();activity.finish();}
 @Test public void boundedHostKeepsMapInsideLayout(){assertTrue(shell.getClipChildren());assertTrue(shell.getClipToPadding());assertEquals(600,shell.getWidth());assertEquals(400,shell.getHeight());assertEquals(new android.graphics.Rect(0,0,600,400),shell.getClipBounds());assertSame(map,MapShell.unwrap(shell));}
 @Test public void onlineTilesRequireExplicitConsent(){assertFalse(map.useDataConnection());Button b=findButton(shell,"Enable map tiles");assertNotNull(b);b.performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertFalse(map.useDataConnection());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(map.useDataConnection());assertTrue(activity.getSharedPreferences("maps",0).getBoolean("online",false));}
 @Test public void preferenceDisableIsHonouredOnUpdate(){activity.getSharedPreferences("maps",0).edit().putBoolean("online",true).commit();MapShell.sync(map,false);assertTrue(map.useDataConnection());activity.getSharedPreferences("maps",0).edit().putBoolean("online",false).commit();MapShell.sync(map,true);assertFalse(map.useDataConnection());}
 @Test public void viewportBrowserHasAnExplicitButton(){assertNotNull(findButton(shell,"Photos in view"));}
 @Test public void emptyAreaDoesNotOpenBrokenGallery(){MapPhotos.gallery(activity,Collections.emptyList(),"Area");assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());}
 @Test public void dateLineRectangleIncludesBothSides(){assertTrue(PhotoClusters.inside(0,179,10,-170,-10,170));assertTrue(PhotoClusters.inside(0,-179,10,-170,-10,170));assertFalse(PhotoClusters.inside(0,0,10,-170,-10,170));}
 @Test public void regionBoundsAreInclusiveAndRejectInvalid(){assertTrue(PhotoClusters.inside(-10,130,-10,140,-20,130));assertFalse(PhotoClusters.inside(-9,130,-10,140,-20,130));assertFalse(PhotoClusters.inside(Double.NaN,130,-10,140,-20,130));}
 @Test public void zoomCannotResizeClippingHost(){map.getController().setZoom(4.0);map.getController().setZoom(12.0);map.scrollBy(100,100);shell.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));shell.layout(0,0,600,400);assertEquals(400,shell.getHeight());assertEquals(400,map.getHeight());assertEquals(600,map.getWidth());}
 @Test public void markerSelectionOpensAnImageViewer(){
  PhotoClusters overlay=new PhotoClusters(p->kotlin.Unit.INSTANCE);
  map.getController().setCenter(new org.osmdroid.util.GeoPoint(0,0));map.getController().setZoom(4.0);
  overlay.setPhotos(Arrays.asList(new LocatedPhoto("content://missing/photo/1","Photo one",0,0)));
  android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(600,400,android.graphics.Bitmap.Config.ARGB_8888);
  overlay.draw(new android.graphics.Canvas(b),map,false);
  android.graphics.Point pt=map.getProjection().toPixels(new org.osmdroid.util.GeoPoint(0,0),null);
  MotionEvent tap=MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,pt.x,pt.y,0);
  assertTrue(overlay.onSingleTapConfirmed(tap,map));tap.recycle();
  Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(d);assertTrue(d.isShowing());assertNotNull(findButton(d.getWindow().getDecorView(),"Open original"));d.dismiss();b.recycle();
 }
 @Test public void clusterGalleryHasEveryPhotoAndBackControl(){
  List<LocatedPhoto> photos=new ArrayList<>();for(int i=0;i<1100;i++)photos.add(new LocatedPhoto("content://missing/photo/"+i,"Photo "+i,0,0));
  MapPhotos.gallery(activity,photos,"Visible area");Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();
  GridView grid=findGrid(d.getWindow().getDecorView());assertNotNull(grid);assertEquals(1100,grid.getAdapter().getCount());assertNotNull(findButton(d.getWindow().getDecorView(),"Back to map"));d.dismiss();
 }
 @Test public void viewportSelectionUsesOnlyTheProvidedFilteredPhotos(){
  PhotoClusters overlay=new PhotoClusters(p->kotlin.Unit.INSTANCE);map.getController().setCenter(new org.osmdroid.util.GeoPoint(0,0));map.getController().setZoom(4.0);
  overlay.setPhotos(Arrays.asList(new LocatedPhoto("content://visible/1","Match",0,0),new LocatedPhoto("content://outside/2","Other",80,120)));
  List<LocatedPhoto> visible=overlay.visible(map);assertEquals(1,visible.size());assertEquals("Match",visible.get(0).getName());
 }
 private static GridView findGrid(View v){if(v instanceof GridView)return (GridView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){GridView result=findGrid(g.getChildAt(i));if(result!=null)return result;}}return null;}
 private static Button findButton(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().equals(text))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
}
