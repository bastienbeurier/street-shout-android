package com.streetshout.android.Activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import com.androidquery.AQuery;
import com.androidquery.callback.AjaxStatus;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.streetshout.android.Fragments.FeedFragment;
import com.streetshout.android.Fragments.ShoutFragment;
import com.streetshout.android.Models.ShoutModel;
import com.streetshout.android.R;
import com.streetshout.android.Utils.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class NavActivity extends Activity implements GoogleMap.OnMyLocationChangeListener, ShoutFragment.OnShoutSelectedListener, FeedFragment.OnFeedShoutSelectedListener {

    private static int CREATE_SHOUT_CODE = 11101;

    private ConnectivityManager connectivityManager = null;

    private LocationManager locationManager = null;

    private AQuery aq = null;

    private GoogleMap mMap = null;

    private CameraPosition.Builder builder = null;

    /** Set of shout ids to keep track of shouts already added to the map */
    private HashMap<Integer, ShoutModel> displayedShouts = null;

    private HashMap<String, Integer> markerIdToShoutId = null;

    private Location myLocation = null;

    private CameraPosition savedCameraPosition = null;

    private FeedFragment feedFragment = null;

    private ShoutFragment shoutFragment = null;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nav);

        this.aq = new AQuery(this);

        this.locationManager = (LocationManager) this.getSystemService(this.LOCATION_SERVICE);

        this.connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        builder = new CameraPosition.Builder();
        builder.zoom(Constants.CLICK_ON_SHOUT_ZOOM);

        displayedShouts = new HashMap<Integer, ShoutModel>();
        markerIdToShoutId = new HashMap<String, Integer>();

        feedFragment = (FeedFragment) getFragmentManager().findFragmentById(R.id.feed_fragment);
        shoutFragment = (ShoutFragment) getFragmentManager().findFragmentById(R.id.shout_fragment);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.hide(shoutFragment);
        ft.commit();

        if (savedInstanceState != null) {
            savedCameraPosition = savedInstanceState.getParcelable("cameraPosition");
        }

        checkLocationServicesEnabled();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable("cameraPosition", mMap.getCameraPosition());
        super.onSaveInstanceState(outState);
    }

    private void checkLocationServicesEnabled() {
        boolean gps_enabled = false;
        boolean network_enabled = false;

        try {
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {}

        try {
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {}

        if(!gps_enabled && !network_enabled){
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle(getText(R.string.no_location_dialog_title));
            dialog.setMessage(getText(R.string.no_location_dialog_message));
            dialog.setPositiveButton(getText(R.string.settings), new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    Intent settings = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    NavActivity.this.startActivity(settings);
                }
            });
            dialog.setNegativeButton(getText(R.string.skip), null);
            dialog.show();
        }
    }

    @Override
    public void onMyLocationChange(Location location) {
        myLocation = location;
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean newMap = setUpMapIfNeeded();
        myLocation = getMyInitialLocation();

        //If the map is new, camera hasn't been initialized to user position, let's do it if we have the user location
        if (newMap) {
            if (savedCameraPosition != null) {
                initializeCameraWithCameraPosition(savedCameraPosition);
                savedCameraPosition = null;
            } else if (myLocation != null) {
                initializeCameraWithLocation(myLocation);
            }
        }
    }

    private boolean setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

            if (mMap == null) {
                return false;
            }

            //Set map settings
            UiSettings settings = mMap.getUiSettings();
            settings.setZoomControlsEnabled(false);
            settings.setCompassEnabled(true);
            settings.setMyLocationButtonEnabled(true);
            settings.setRotateGesturesEnabled(false);
            settings.setTiltGesturesEnabled(false);

            //Set user location
            mMap.setMyLocationEnabled(true);

            //Set location listener
            mMap.setOnMyLocationChangeListener(this);

            //Pull shouts on the map
            mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
                @Override
                public void onCameraChange(CameraPosition cameraPosition) {
                    pullShouts(cameraPosition);
                }
            });

            mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                public boolean onMarkerClick(Marker marker) {
                    ShoutModel shout = displayedShouts.get(markerIdToShoutId.get(marker.getId()));

                    shoutFragment.displayShout(shout);

                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.hide(feedFragment);
                    ft.show(shoutFragment);

                    ft.addToBackStack(null);
                    ft.commit();

                    return false;
                }
            });

            return true;
        }

        return false;
    }

    private Location getMyInitialLocation() {
        if (locationManager == null) {
            this.locationManager = (LocationManager) this.getSystemService(this.LOCATION_SERVICE);
        }

        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, true);
        return locationManager.getLastKnownLocation(provider);
    }

    private void initializeCameraWithLocation(Location location) {
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(LocationUtils.toLatLng(location), Constants.INITIAL_ZOOM);
        mMap.moveCamera(update);
    }

    private void initializeCameraWithCameraPosition(CameraPosition cameraPosition) {
        CameraUpdate update = CameraUpdateFactory.newCameraPosition(cameraPosition);
        mMap.moveCamera(update);
    }

    private void pullShouts(CameraPosition cameraPosition) {
        MapRequestHandler mapReqHandler = new MapRequestHandler();

        //Set listener to catch API response from the MapRequestHandler
        mapReqHandler.setRequestResponseListener(new MapRequestHandler.RequestResponseListener() {
            @Override
            public void responseReceived(String url, JSONObject object, AjaxStatus status) {
                if (status.getError() == null) {
                    JSONArray rawResult;
                    try {
                        rawResult = object.getJSONArray("result");

                        List<ShoutModel> shouts = ShoutModel.rawShoutsToInstances(rawResult);
                        addShoutsOnMap(shouts);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //Add a request to populate the map with shouts
        mapReqHandler.addMapRequest(this, aq, cameraPosition, true);
    }

    private void addShoutsOnMap(List<ShoutModel> shouts) {

        for (ShoutModel shout: shouts) {
            //Check that the shout is not already marked on the map
            if (!displayedShouts.containsKey(shout.id)) {
                displayShoutOnMap(shout);
            }
        }
    }

    private void displayShoutOnMap(ShoutModel shout) {
        MarkerOptions markerOptions = new MarkerOptions();

        markerOptions.position(new LatLng(shout.lat, shout.lng));
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.shout_marker));
        markerOptions.anchor((float) 0.2, (float) 0.6);

        displayedShouts.put(shout.id, shout);
        Marker marker = mMap.addMarker(markerOptions);

        markerIdToShoutId.put(marker.getId(), shout.id);
    }

    public void createShout(View view) {
        if (connectivityManager != null && connectivityManager.getActiveNetworkInfo() == null) {
            Toast toast = Toast.makeText(this, getString(R.string.no_connection), Toast.LENGTH_SHORT);
            toast.show();
        } else if (myLocation == null) {
            Toast toast = Toast.makeText(this, getString(R.string.no_location), Toast.LENGTH_SHORT);
            toast.show();
        } else {
            Intent createShout = new Intent(this, CreateShoutActivity.class);
            createShout.putExtra("myLocation", myLocation);
            startActivityForResult(createShout, CREATE_SHOUT_CODE);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == CREATE_SHOUT_CODE) {

            if(resultCode == RESULT_OK){
                Toast toast = Toast.makeText(this, getString(R.string.create_shout_success), Toast.LENGTH_LONG);
                toast.show();

                ShoutModel shout = data.getParcelableExtra("newShout");

                displayShoutOnMap(shout);

                animateCameraToShout(shout);

                shoutFragment.displayShout(shout);

                showFragment(R.id.shout_fragment);
            }
        }
    }

    @Override
    public void onFeedShoutSelected(JSONObject rawShout) {
        final ShoutModel shout = ShoutModel.rawShoutToInstance(rawShout);

        animateCameraToShout(shout);

        shoutFragment.displayShout(shout);

        showFragment(R.id.shout_fragment);
    }

    @Override
    public void onShoutSelected(ShoutModel shout) {
        animateCameraToShout(shout);
    }

    private void animateCameraToShout(ShoutModel shout) {
        CameraUpdate update = CameraUpdateFactory.newCameraPosition(builder.target(new LatLng(shout.lat, shout.lng)).build());
        mMap.animateCamera(update);
    }

    private void showFragment(int fragmentId) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();

        if (fragmentId == R.id.feed_fragment) {

        } else if (fragmentId == R.id.shout_fragment) {
            ft.hide(feedFragment);
            ft.show(shoutFragment);
        }

        ft.addToBackStack(null);
        ft.commit();
    }
}