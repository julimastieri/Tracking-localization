package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.DexterBuilder;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    Button requestLocation, removeLocation;

    LocationService mLocationService=null;
    boolean mBound=false;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) iBinder;
            mLocationService = binder.getService();
            mBound=true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLocationService = null;
            mBound=false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Dexter.withActivity(this).withPermissions(Arrays.asList(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
                ))
                .withListener(new MultiplePermissionsListener(){
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {

                        // Chekeo que me haya dado todos los permisos
                        if (report.areAllPermissionsGranted()) {
                            requestLocation = (Button) findViewById(R.id.request);
                            removeLocation = (Button) findViewById(R.id.remove);

                            requestLocation.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    mLocationService.requestLocationUpdates();
                                }
                            });
                            removeLocation.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    mLocationService.removeLocationUpdates();
                                }
                            });

                            setButtonState(Common.requestingLocationUpdates(MainActivity.this));
                            bindService(new Intent(MainActivity.this,
                                            LocationService.class),
                                    mServiceConnection,
                                    Context.BIND_AUTO_CREATE);
                        }

                        // Chekeo si me los denego permanentemente, de ser asi habro app settings
                        if (report.isAnyPermissionPermanentlyDenied()) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        }

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    @Override
    protected void onStart (){
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        if(mBound){
            unbindService(mServiceConnection);
            mBound=false;
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if(s.equals(Common.KEY_REQUESTING_LOCATION_UPDATES))
        {
            setButtonState(sharedPreferences.getBoolean(Common.KEY_REQUESTING_LOCATION_UPDATES,false));
        }
    }

    private void setButtonState(boolean isRequestEnable) {
        if(isRequestEnable)
        {
            requestLocation.setEnabled(false);
            removeLocation.setEnabled(true);
        }
        else{
            requestLocation.setEnabled(true);
            removeLocation.setEnabled(false);
        }
    }

    @Subscribe (sticky = true, threadMode = ThreadMode.MAIN)
    public void onListenLocation(SendLocationToActivity event){
        if (event != null){
            String data = new  StringBuilder()
                    .append(event.getLocation().getLatitude())
                    .append("/")
                    .append(event.getLocation().getLongitude())
                    .toString();
            Toast.makeText(mLocationService, data, Toast.LENGTH_LONG).show();
        }
    }
}
