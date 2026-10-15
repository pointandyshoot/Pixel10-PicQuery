package au.pointandyshoot.picquery;
/** Compile-time/test ABI only. Excluded from APK; original 0.3.0 class remains unchanged. */
public final class LocatedPhoto {
 private final String uri,name; private final double latitude,longitude;
 public LocatedPhoto(String uri,String name,double latitude,double longitude){this.uri=uri;this.name=name;this.latitude=latitude;this.longitude=longitude;}
 public String getUri(){return uri;}
 public String getName(){return name;}
 public double getLatitude(){return latitude;}
 public double getLongitude(){return longitude;}
}
