package com.example.myapplication;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.greenrobot.eventbus.EventBus;

public class LocationService extends Service {

    private static final String CHANNEL_ID = "my_channel";
    private static final long UPDATE_INTERVAL_IN_MIL=10000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MIL=UPDATE_INTERVAL_IN_MIL/2;
    private static final int NOTIFICATION_ID = 1223;
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = "com.example.myapplication"+"started_from:notification";
    private boolean mChangingConfiguration=false;
    private NotificationManager mNotificationManager;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Handler mServiceHandler;
    private Location mLocation;

    public  LocationService(){}

    @Override
    public void onCreate() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };

        createLocationRequest();
        getLastLocation();

        HandlerThread handlerThread = new HandlerThread("loc");
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false);
        if (startedFromNotification)
        {
            removeLocationUpdates();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration=true;
    }

    public void removeLocationUpdates() {
        try{
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            Common.setRequestingLocationUpdates(this,false);
            stopSelf();
        } catch (SecurityException ex) {
            Common.setRequestingLocationUpdates(this,true);
            Log.e("Ofertapp", "Permiso de localizacion perdido. "+ex);
        }
    }

    private void getLastLocation() {
        try
        {
            fusedLocationProviderClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                             if (task.isSuccessful() && task.getResult() != null)
                                 mLocation = task.getResult();
                             else
                                 Log.e("Ofertapp", "Fallo en obtener la ubicacion");

                        }

                    });
        }catch (SecurityException ex){
            Log.e("Ofertapp", "Se perdio el permiso de localizacion"+ex);
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MIL);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MIL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void onNewLocation(Location lastLocation) {
        mLocation = lastLocation;

        //Mando
        //EventBus.getDefault().postSticky(new SendLocationToActivity(mLocation));

        //Update notification content if running as foreground service
        if (serviceIsRunningInForeground(this))
            mNotificationManager.notify(NOTIFICATION_ID,getNotification());
    }

    private Notification getNotification() {
        Intent intent = new Intent(this, LocationService.class);
        String text = Common.getLocationText(mLocation);

        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0,intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent activityPendingIntent = PendingIntent.getActivity(this,0,
                new Intent(this, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_launch_black_24dp, "Launch", activityPendingIntent)
                .addAction(R.drawable.ic_cancel_black_24dp, "Remove", servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Common.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        //Set channel id for Android O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            builder.setChannelId(CHANNEL_ID);
        }
        return builder.build();
    }

    private boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service:manager.getRunningServices(Integer.MAX_VALUE))
            if(getClass().getName().equals(service.service.getClassName()))
                if(service.foreground)
                    return true;
        return false;
    }

    private final IBinder mBinder = new LocalBinder();

    public void requestLocationUpdates() {
        Common.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(),LocationService.class));
        try{
            fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback, Looper.myLooper());
        } catch (SecurityException ex)
        {
            Log.e("Ofertapp", "Se perdio el permiso de localizacion"+ex);
        }
    }

    public class LocalBinder extends Binder {
        LocationService getService(){return LocationService.this;}
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration=false;

        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        stopForeground(true);
        mChangingConfiguration=false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (!mChangingConfiguration && Common.requestingLocationUpdates(this))
            startForeground(NOTIFICATION_ID,getNotification());
        return true;
    }

    @Override
    public void onDestroy() {
        mServiceHandler.removeCallbacks(null);
        super.onDestroy();
    }
}
