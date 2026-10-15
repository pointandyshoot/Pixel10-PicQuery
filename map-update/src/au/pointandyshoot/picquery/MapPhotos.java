package au.pointandyshoot.picquery;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import java.util.*;
import java.util.concurrent.*;

/** Local photo browsing shared by cluster and viewport selection. No photo network requests. */
public final class MapPhotos {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(96), new ThreadPoolExecutor.AbortPolicy());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private static final Map<String,List<Runnable>> PENDING = new HashMap<>();
    public static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    public static Bitmap cached(String uri) { return CACHE.get(uri); }
    public static void thumbnail(Context c, String uri, Runnable ready) {
        if (CACHE.get(uri) != null) { MAIN.post(ready); return; }
        synchronized(PENDING) {
            List<Runnable> listeners=PENDING.get(uri);
            if(listeners!=null){if(listeners.size()<12)listeners.add(ready);return;}
            listeners=new ArrayList<>();listeners.add(ready);PENDING.put(uri,listeners);
        }
        try { IO.execute(() -> {
            Bitmap b = decode(c.getApplicationContext(), uri, 160);
            if (b != null) CACHE.put(uri, b);
            List<Runnable> listeners; synchronized(PENDING){listeners=PENDING.remove(uri);}
            if(b!=null&&listeners!=null) for(Runnable listener:listeners) MAIN.post(listener);
        }); } catch (RejectedExecutionException e) { synchronized(PENDING){PENDING.remove(uri);} }
    }
    private static Bitmap decode(Context c, String uri, int side) {
        try {
            Uri u = Uri.parse(uri);
            if (!"content".equals(u.getScheme())) return null;
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(), u), (d, info, s) -> {
                int w=info.getSize().getWidth(), h=info.getSize().getHeight();
                double scale=Math.min(1.0, (double)side/Math.max(w,h));
                d.setTargetSize(Math.max(1,(int)(w*scale)), Math.max(1,(int)(h*scale)));
                d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                d.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            });
        } catch (Exception | OutOfMemoryError e) { return null; }
    }
    public static void gallery(Context c, List<LocatedPhoto> input, String title) {
        List<LocatedPhoto> photos = new ArrayList<>(input);
        if (photos.isEmpty()) { Toast.makeText(c,"No matching located photos in this area",Toast.LENGTH_SHORT).show(); return; }
        if (photos.size()==1) { viewer(c,photos,0); return; }
        Dialog dialog = new Dialog(c);
        LinearLayout root = new LinearLayout(c); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        TextView heading = new TextView(c); heading.setText(title+" · "+photos.size()+" photos"); heading.setTextSize(18); heading.setTextColor(Color.BLACK); heading.setPadding(dp(c,16),dp(c,12),dp(c,12),dp(c,12));
        root.addView(heading);
        GridView grid=new GridView(c); grid.setNumColumns(3); grid.setVerticalSpacing(dp(c,3)); grid.setHorizontalSpacing(dp(c,3));
        grid.setAdapter(new BaseAdapter() {
            public int getCount(){return photos.size();}
            public Object getItem(int p){return photos.get(p);}
            public long getItemId(int p){return p;}
            public View getView(int p, View old, android.view.ViewGroup parent) {
                ImageView image=old instanceof ImageView?(ImageView)old:new ImageView(c);
                image.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(c,120)));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackgroundColor(0xffdbe4df);
                String uri=photos.get(p).getUri(); image.setTag(uri); image.setContentDescription(photos.get(p).getName());
                Bitmap b=cached(uri); image.setImageBitmap(b);
                if(b==null) thumbnail(c,uri,()->{if(uri.equals(image.getTag()))image.setImageBitmap(cached(uri));});
                return image;
            }
        });
        grid.setOnItemClickListener((p,v,pos,id)->viewer(c,photos,pos));
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        Button close=new Button(c);close.setText("Back to map");close.setOnClickListener(v->dialog.dismiss());root.addView(close);
        dialog.setContentView(root); dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    }
    public static void viewer(Context c, List<LocatedPhoto> photos, int initial) {
        Dialog dialog=new Dialog(c); LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff101815);
        TextView title=new TextView(c);title.setTextColor(Color.WHITE);title.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));root.addView(title);
        ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(c);int[] position={initial};int[] generation={0};Bitmap[] full={null};
        Runnable load=()->{
            LocatedPhoto p=photos.get(position[0]); title.setText((position[0]+1)+" / "+photos.size()+" · "+p.getName());
            int token=++generation[0]; image.setImageBitmap(cached(p.getUri()));
            Bitmap previous=full[0];full[0]=null;if(previous!=null)previous.recycle();
            try { IO.execute(()->{Bitmap b=decode(c,p.getUri(),1600);MAIN.post(()->{
                if(token!=generation[0]||!dialog.isShowing()){if(b!=null)b.recycle();return;}
                if(b!=null){full[0]=b;image.setImageBitmap(b);}else Toast.makeText(c,"Photo is no longer readable",Toast.LENGTH_SHORT).show();
            });}); } catch(RejectedExecutionException e){Toast.makeText(c,"Loading thumbnails; try this photo again shortly",Toast.LENGTH_SHORT).show();}
        };
        String[] labels={"Previous","Next","Open original","Close"};
        for(int i=0;i<labels.length;i++){final int action=i;Button b=new Button(c);b.setText(labels[i]);b.setTextSize(11);buttons.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{
            if(action==0||action==1){position[0]=(position[0]+photos.size()+(action==0?-1:1))%photos.size();load.run();}
            else if(action==2){try{c.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(photos.get(position[0]).getUri()),"image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception e){Toast.makeText(c,"No photo viewer available",Toast.LENGTH_SHORT).show();}}
            else dialog.dismiss();
        });}
        root.addView(buttons);dialog.setContentView(root);dialog.setOnDismissListener(d->{generation[0]++;image.setImageDrawable(null);if(full[0]!=null)full[0].recycle();full[0]=null;});dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);load.run();
    }
}
