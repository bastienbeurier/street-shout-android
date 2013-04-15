package com.streetshout.android.Activities;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.*;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.streetshout.android.Models.ShoutModel;
import com.streetshout.android.Utils.MapRequestHandler;
import com.streetshout.android.R;
import com.streetshout.android.Utils.LocationUtils;
import com.streetshout.android.Utils.TimeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class MainActivity extends Activity {

    private static final boolean ADMIN = true;
    private boolean admin_super_powers = false;

    /** Required recentness and accuracy of the user position for creating a new shout */
    private static final int REQUIRED_RECENTNESS = 1000 * 60 * 2;

    /** Zoom for the initial camera position when we have the user location */
    private static final int INITIAL_ZOOM = 11;

    private static final int MIN_SHOUT_RADIUS = 200;

    /** Location manager that handles the network services */
    private LocationManager locationManager = null;

    /** Location listener to get location from network services */
    private LocationListener locationListener = null;

    /** Best user location that we have right now */
    private Location bestLoc = null;

    /** Aquery instance to handle ajax calls to the API */
    private AQuery aq = null;

    /** Google map instance */
    private GoogleMap mMap;

    /** Set of shout ids to keep track of shouts already added to the map */
    private Set<Integer> markedShouts = null;

    /** Because dialog "done" action is triggered twice */
    private boolean canCreateShout = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        displayMainActionBar();

        this.aq = new AQuery(this);

        markedShouts = new HashSet<Integer>();

        ToggleButton adminToggle = (ToggleButton) findViewById(R.id.admin_toggle);
        if (ADMIN)  {
            adminToggle.setVisibility(View.VISIBLE);
            adminToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    admin_super_powers = isChecked;
                }
            });
        } else {
            adminToggle.findViewById(R.id.admin_toggle).setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Set up location service
        this.locationManager = (LocationManager) this.getSystemService(this.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                //If location is significantly better, update bestLoc
                if (LocationUtils.isBetterLocation(location, MainActivity.this.bestLoc)) {
                    MainActivity.this.bestLoc = location;
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        //Set up network services for location updates
        final boolean networkEnabled = this.locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (networkEnabled) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 10 ,locationListener);
        }

        //This is where the map is instantiated
        boolean newMap = setUpMapIfNeeded();

        //If the map is new, camera hasn't been initialized to user position, let's do it if we have the user location
        if (newMap) {
            if (bestLoc == null && getIntent().hasExtra("firstLocation")) {
                bestLoc = getIntent().getParcelableExtra("firstLocation");
            }

            if (bestLoc != null) {
                initializeCamera(bestLoc);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onStop();
        locationManager.removeUpdates(locationListener);
    }

    /** Set initial camera position on the user location */
    private void initializeCamera(Location location) {
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(LocationUtils.toLatLng(location), INITIAL_ZOOM);
        mMap.moveCamera(update);
    }

    private void displayMainActionBar() {
        LayoutInflater inflater = (LayoutInflater) getActionBar().getThemedContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);

        final View mainActionBarView = inflater.inflate(R.layout.actionbar_feed_and_create_shout, null);

        mainActionBarView.findViewById(R.id.create_shout_item_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (admin_super_powers) {
                    getNewShoutDescription(bestLoc, null, null);
                } else {

                    if (bestLoc != null && (System.currentTimeMillis() - bestLoc.getTime() < REQUIRED_RECENTNESS)) {
                        startShoutCreationProcess(MainActivity.this.bestLoc);
                    } else {
                        Toast toast = Toast.makeText(MainActivity.this, "No good location available!", Toast.LENGTH_LONG);
                        toast.show();
                    }
                }
            }
        });

        actionBar.setCustomView(mainActionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** If no map already present, set up map with settings, event listener, ... Return true if a new map is set */
    private boolean setUpMapIfNeeded() {
        if (mMap == null) {
            //Instantiate map
            mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

            //Set map settings
            UiSettings settings = mMap.getUiSettings();
            settings.setZoomControlsEnabled(false);
            settings.setCompassEnabled(true);
            settings.setMyLocationButtonEnabled(true);
            settings.setRotateGesturesEnabled(false);
            settings.setTiltGesturesEnabled(false);

            /** Set camera move listener that sends requests to populate the map to the MapRequestHandler and listen for
             its response */
            mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
                @Override
                public void onCameraChange(CameraPosition cameraPosition) {
                //Set listener and send calls to the MapRequestHandler
                pullShouts(cameraPosition);
                }
            });

            return true;
        }
        return false;
    }

    /** Set listener for MapRequestHandler responses and add requests to this handler */
    private void pullShouts(CameraPosition cameraPosition) {
        MapRequestHandler mapReqHandler = new MapRequestHandler();

        //Set listener to catch API response from the MapRequestHandler
        mapReqHandler.setRequestResponseListener(new MapRequestHandler.RequestResponseListener() {
            @Override
            public void responseReceived(String url, JSONObject object, AjaxStatus status) {
                if (status.getError() == null) {
                    JSONArray rawResult = null;
                    try {
                        rawResult = object.getJSONArray("result");

                        //Get ShoutModel instances for a raw JSONArray
                        List<ShoutModel> shouts = ShoutModel.rawShoutsToInstances(rawResult);

                        //Add received shours on the map
                        addShoutsOnMap(shouts);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //Add a request to populate the map to the MapRequestHandler
        mapReqHandler.addMapRequest(this, aq, cameraPosition);
    }

    /** Add a list of shouts on the map */
    private void addShoutsOnMap(List<ShoutModel> shouts) {

        for (ShoutModel shout: shouts) {
            //Check that the shout is not already marked on the map
            if (!markedShouts.contains(shout.id)) {
                displayShoutOnMap(shout);
            }
        }
    }

    private void startShoutCreationProcess(Location newShoutLoc) {
        int shoutRadius = Math.max((int) newShoutLoc.getAccuracy(), MIN_SHOUT_RADIUS);

        Circle newShoutCircle = setShoutPerimeterCircle(shoutRadius, newShoutLoc);

        Marker newShoutMarker = getShoutAccuratePosition(shoutRadius, newShoutLoc);

        displayDoneDiscardActionBar(newShoutLoc, newShoutCircle, newShoutMarker);
    }

    private void endShoutCreationProcess(Circle shoutRadiusCircle, Marker newShoutMarker) {
        //Remove circle and position marker
        if (shoutRadiusCircle != null) {
            shoutRadiusCircle.remove();
        }

        if (newShoutMarker != null) {
            newShoutMarker.remove();
        }

        //Bring initial action bar back
        displayMainActionBar();
    }

    /** Display on the map the zone where the user will be able to drag his shout */
    private Circle setShoutPerimeterCircle(int shoutRadius, Location newShoutLoc) {
        /**Shout radius is the perimeter where the user could be due to location inaccuracy (there is a minimum radius
        if location is very accurate) */

        //Compute bouds of this perimeter
        LatLng[] boundsResult = LocationUtils.getLatLngBounds(shoutRadius, newShoutLoc);
        LatLngBounds bounds = new LatLngBounds(boundsResult[0], boundsResult[1]);

        //Update the camera to fit this perimeter
        CameraUpdate update = CameraUpdateFactory.newLatLngBounds(bounds, shoutRadius/15);
        mMap.moveCamera(update);

        //Draw the circle where the user will be able to drag is shout
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(LocationUtils.toLatLng(newShoutLoc)).radius(shoutRadius).strokeWidth(0).fillColor(Color.parseColor("#66327CCB"));
        Circle shoutRadiusCircle = mMap.addCircle(circleOptions);

        return shoutRadiusCircle;
    }

    /** Let the user indicate the accurate position of his shout by dragging a marker within the shout radius */
    private Marker getShoutAccuratePosition(final int shoutRadius, final Location newShoutLoc) {
        //Display marker the user is going to drag to specify his accurate position
        MarkerOptions marker = new MarkerOptions();
        marker.position(new LatLng(newShoutLoc.getLatitude(), newShoutLoc.getLongitude()));
        marker.draggable(true);
        marker.icon(BitmapDescriptorFactory.fromResource(R.drawable.location_arrow));
        Marker newShoutMarker = mMap.addMarker(marker);

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                //Nothing
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                //Nothing
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                float[] distance = new float[] {0};
                Location.distanceBetween(newShoutLoc.getLatitude(), newShoutLoc.getLongitude(), marker.getPosition().latitude, marker.getPosition().longitude, distance);

                //If user drags marker outside of the shoutRadius, bring back shout marker to initial position
                if (distance[0] > shoutRadius) {
                    marker.setPosition(LocationUtils.toLatLng(newShoutLoc));
                //Else update shout position
                } else {
                    newShoutLoc.setLatitude(marker.getPosition().latitude);
                    newShoutLoc.setLongitude(marker.getPosition().longitude);
                }
            }
        });

        return newShoutMarker;
    }

    /** Display shout on the map and add shout id to current shouts */
    private void displayShoutOnMap(ShoutModel shout) {
        MarkerOptions marker = new MarkerOptions();

        marker.position(new LatLng(shout.lat, shout.lng));
        marker.title(shout.description).snippet(TimeUtils.shoutAgeToString(TimeUtils.getShoutAge(shout.created)));
        marker.icon(BitmapDescriptorFactory.fromResource(R.drawable.shout_marker));
        markedShouts.add(shout.id);
        mMap.addMarker(marker);
    }

    private void displayDoneDiscardActionBar(final Location newShoutLoc, final Circle newShoutCircle, final Marker newShoutMarker) {
        LayoutInflater inflater = (LayoutInflater) getActionBar().getThemedContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);

        // Inflate a "Done/Discard" custom action bar view.
        final View customActionBarView = inflater.inflate(R.layout.actionbar_custom_view_done_discard, null);

        //Done button
        customActionBarView.findViewById(R.id.actionbar_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getNewShoutDescription(newShoutLoc, newShoutCircle, newShoutMarker);
            }
        });

        //Discard button
        customActionBarView.findViewById(R.id.actionbar_discard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endShoutCreationProcess(newShoutCircle, newShoutMarker);
            }
        });

        actionBar.setCustomView(customActionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void getNewShoutDescription(final Location newShoutLoc, final Circle shoutRadiusCircle, final Marker newShoutMarker) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();

        builder.setView(inflater.inflate(R.layout.create_shout, null));

        //OK: Redirect user to edit location settings
        builder.setPositiveButton(R.string.shout, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                String description = ((EditText) ((AlertDialog) dialog).findViewById(R.id.create_shout_descr_dialog)).getText().toString();
                createNewShoutFromInfo(description, newShoutLoc);
                endShoutCreationProcess(shoutRadiusCircle, newShoutMarker);
            }
        });

        //DISMISS: MainActivity without user location
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                endShoutCreationProcess(shoutRadiusCircle, newShoutMarker);
            }
        });

        Dialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        //User press "send" on the keyboard
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    //For some reason, the event get fired twice. This is a hack to send the shout only once.
                    if (canCreateShout) {
                        canCreateShout = false;
                        String description = ((EditText) ((AlertDialog) dialog).findViewById(R.id.create_shout_descr_dialog)).getText().toString();
                        dialog.dismiss();
                        createNewShoutFromInfo(description, newShoutLoc);
                        endShoutCreationProcess(shoutRadiusCircle, newShoutMarker);
                    } else {
                        canCreateShout = true;
                    }
                }
                return false;
            }
        });

        dialog.show();
    }

    /** User confirmed shout creation after scpecifying accurate location and shout description */
    public void createNewShoutFromInfo(String description, final Location newShoutLoc) {
        double lat;
        double lng;

        //If a admin capabilities, create shout in the middle on the map
        if (admin_super_powers) {
            lat = mMap.getCameraPosition().target.latitude;
            lng = mMap.getCameraPosition().target.longitude;
        //Else get the location specified by the user
        } else {
            lat = newShoutLoc.getLatitude();
            lng = newShoutLoc.getLongitude();
        }

        //Create shout!
        ShoutModel.createShout(aq, lat, lng, description, new AjaxCallback<JSONObject>() {
            @Override
            public void callback(String url, JSONObject object, AjaxStatus status) {
                super.callback(url, object, status);
                if (status.getError() == null) {
                    JSONObject rawShout = null;

                    try {
                        rawShout = object.getJSONObject("result");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    displayShoutOnMap(ShoutModel.rawShoutToInstance(rawShout));
                    Toast toast = Toast.makeText(MainActivity.this, getString(R.string.create_shout_success), Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        });
    }
}
