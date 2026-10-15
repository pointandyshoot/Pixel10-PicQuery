package au.pointandyshoot.picquery;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

/** Fixed, clipped AndroidView host. Child map gestures never transform surrounding Compose UI. */
public final class MapShell extends FrameLayout {
    private final MapView map;
    private final TextView status;
    private final Button online;
    private boolean requestedOnline, received;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Handler tileHandler;
    public MapShell(MapView map){
        super(map.getContext());this.map=map;
        setClipChildren(true);setClipToPadding(true);setBackgroundColor(0xffe7ede7);
        addView(map,new LayoutParams(-1,-1));
        map.setOnTouchListener((v,event)->{if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(event.getActionMasked()!=MotionEvent.ACTION_UP&&event.getActionMasked()!=MotionEvent.ACTION_CANCEL);return false;});
        LinearLayout controls=new LinearLayout(getContext());controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(6),dp(4),dp(6),dp(4));controls.setBackgroundColor(0xf5ffffff);
        LinearLayout row=new LinearLayout(getContext());
        online=new Button(getContext());online.setTextSize(11);online.setOnClickListener(v->toggleTiles());row.addView(online,new LinearLayout.LayoutParams(0,-2,1));
        Button photos=new Button(getContext());photos.setText("Photos in view");photos.setTextSize(11);photos.setOnClickListener(v->{for(Overlay o:map.getOverlays())if(o instanceof PhotoClusters){MapPhotos.gallery(getContext(),((PhotoClusters)o).visible(map),"Photos in this view");break;}});row.addView(photos,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(row);status=new TextView(getContext());status.setTextSize(11);status.setTextColor(0xff203c30);controls.addView(status);
        LayoutParams panel=new LayoutParams(-1,-2,Gravity.BOTTOM);addView(controls,panel);
        update(map.getContext().getSharedPreferences("maps",0).getBoolean("online",false));
        // Additional tile handler keeps osmdroid's own invalidation handler intact.
        tileHandler=new Handler(Looper.getMainLooper()){
            public void handleMessage(Message m){if(m.what==0){received=true;status.setText(requestedOnline?"Map ready · tap thumbnails to browse · search text also filters markers":"Cached map · online tiles off · tap thumbnails to browse");}else if(!received&&requestedOnline)status.setText("Tiles unavailable · check connection or tap Retry map; photo markers work offline");}
        };
        map.getTileProvider().getTileRequestCompleteHandlers().add(tileHandler);
    }
    private int dp(int value){return MapPhotos.dp(getContext(),value);}
    public static View wrap(MapView map){return new MapShell(map);}
    public static MapView unwrap(Object view){return view instanceof MapShell?((MapShell)view).map:(MapView)view;}
    public static void sync(MapView map,boolean ignored){if(map.getParent() instanceof MapShell)((MapShell)map.getParent()).update(map.getContext().getSharedPreferences("maps",0).getBoolean("online",false));else map.setUseDataConnection(ignored);}
    private void update(boolean enabled){
        boolean changed=requestedOnline!=enabled;requestedOnline=enabled;map.setUseDataConnection(enabled);
        online.setText(enabled?"Map options":"Enable map tiles");
        if(!enabled)status.setText("Online map off · photo markers still work. Enable tiles to see roads and places.");
        else if(changed||!received){status.setText("Loading map… photos and search stay on this phone");handler.postDelayed(()->{if(requestedOnline&&!received)status.setText("No map tiles received · Map options → Retry; check internet connection");},12000);}
        map.invalidate();
    }
    private void toggleTiles(){
        if(requestedOnline){new AlertDialog.Builder(getContext()).setTitle("Online map").setItems(new String[]{"Retry map tiles","Turn online tiles off"},(d,p)->{if(p==0){received=false;map.getTileProvider().clearTileCache();update(true);}else setEnabledTiles(false);}).show();}
        else new AlertDialog.Builder(getContext()).setTitle("Enable OpenStreetMap tiles?").setMessage("Only map tiles are downloaded. The provider sees the viewed map region and your IP address. Photos, OCR, exact photo coordinates and search text are not uploaded. Tiles are cached locally.").setPositiveButton("Enable",(d,p)->setEnabledTiles(true)).setNegativeButton("Cancel",null).show();
    }
    private void setEnabledTiles(boolean enabled){getContext().getSharedPreferences("maps",0).edit().putBoolean("online",enabled).apply();received=false;update(enabled);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);setClipBounds(new Rect(0,0,w,h));}
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(event.getActionMasked()!=MotionEvent.ACTION_UP&&event.getActionMasked()!=MotionEvent.ACTION_CANCEL);return super.dispatchTouchEvent(event);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);tileHandler.removeCallbacksAndMessages(null);map.getTileProvider().getTileRequestCompleteHandlers().remove(tileHandler);super.onDetachedFromWindow();}
}
