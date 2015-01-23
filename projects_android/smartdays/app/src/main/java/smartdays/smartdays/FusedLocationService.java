package smartdays.smartdays;

/**
 * Created by root on 1/17/15.
 */
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FusedLocationService implements LocationListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final long INTERVAL = 1000 * 60 * 5;
    private static final long FASTEST_INTERVAL = 1000 * 60 * 1;
    private static final long INTERVAL_TO_REMOVE = 1000 * 60 * 4;
    private static final long REFRESH_TIME = 1000 * 60 * 5;
    private static final float MINIMUM_ACCURACY = 50.0f;
    private LocationRequest locationRequest;
    private GoogleApiClient googleApiClient;
    private Location location;
    private FusedLocationProviderApi fusedLocationProviderApi = LocationServices.FusedLocationApi;

    public FusedLocationService(Context context) {
        locationRequest = LocationRequest.create();
        //locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Location currentLocation = fusedLocationProviderApi.getLastLocation(googleApiClient);

        if (currentLocation != null) {
            location = currentLocation;
            Log.d(Constants.TAG, "Connected - Location= " + location.getTime());
        }

        /*
        if (currentLocation != null && currentLocation.getTime() > REFRESH_TIME) {
            location = currentLocation;
        }
        else {
            fusedLocationProviderApi.requestLocationUpdates(googleApiClient, locationRequest, this);
            // Schedule a Thread to unregister location listeners
            Executors.newScheduledThreadPool(1).schedule(new Runnable() {
                @Override
                public void run() {
                    fusedLocationProviderApi.removeLocationUpdates(googleApiClient, FusedLocationService.this);
                }
            }, INTERVAL_TO_REMOVE, TimeUnit.MILLISECONDS);
        }
        */
    }

    @Override
    public void onLocationChanged(Location loc) {
        /*
        //if the existing location is empty or
        //the current location accuracy is greater than existing accuracy
        //then store the current location
        if (null == this.location || loc.getAccuracy() < this.location.getAccuracy()) {
            this.location = loc;
            //if the accuracy is not better, remove all location updates for this listener
            if (this.location.getAccuracy() < MINIMUM_ACCURACY) {
                fusedLocationProviderApi.removeLocationUpdates(googleApiClient, this);
            }
        }
        */
        Log.d(Constants.TAG, "Changed - Location= " + location.getTime());
        location = loc;
    }

    public Location getLocation() {
        Log.d(Constants.TAG, "Location asked");
        return location;
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}