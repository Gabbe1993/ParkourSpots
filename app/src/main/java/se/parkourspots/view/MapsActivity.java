package se.parkourspots.view;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SearchView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import se.parkourspots.R;
import se.parkourspots.connector.SpotHandler;
import se.parkourspots.connector.SpotInfoWindowAdapter;
import se.parkourspots.util.Keyboard;
import se.parkourspots.util.SharedPreferencesSaver;

/**
 * The main Activity containing the map and main entry point for the application.
 */
public class MapsActivity extends AppCompatActivity implements GoogleMap.OnMapClickListener, CreateSpotFragment.OnFragmentInteractionListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private FragmentManager fragmentManager;
    private Marker currentMarker;
    private SpotHandler handler;
    private CreateSpotFragment fragment;
    private LatLng currentLoc;
    private LocationManager locationManager;
    private boolean isVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (handler == null) {
            handler = SpotHandler.getInstance();
        }
        setContentView(R.layout.activity_maps);

        setUpMapIfNeeded();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            if (mMap != null) {
                setUpMap();
                SharedPreferencesSaver.restoreSharedPreferences(this, mMap);
            }
        }
    }

    /**
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.setMyLocationEnabled(true);
        mMap.setOnMapClickListener(this);

        SpotInfoWindowAdapter adapter = new SpotInfoWindowAdapter(this);
        mMap.setInfoWindowAdapter(adapter);
        mMap.setOnInfoWindowClickListener(adapter);

        fragmentManager = getFragmentManager();
        final GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
            boolean zoomed = false;

            @Override
            public void onMyLocationChange(Location location) {
                currentLoc = new LatLng(location.getLatitude(), location.getLongitude());
                if (!zoomed) {
                    zoomed = true;
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLoc, 13.5f));
                }
                //mMap.setOnMyLocationChangeListener(null);
            }
        };
        mMap.setOnMyLocationChangeListener(myLocationChangeListener);

        checkGPS();
    }

    /**
     * Checks for the GPS signal. If GPS is disabled the user will be notified.
     */
    private void checkGPS() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setMessage("Your GPS is disabled\n Do you want to enable it?").setCancelable(false)
                    .setPositiveButton("SETTINGS", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            alert.create().show();
        }
    }

    /**
     * Called when the add button in the map is pressed.
     * If the <Code>CreateSpotFragment</Code> is visible it will be detached, else it will be attached.
     *
     * @param view The view which is pressed.
     */
    public void clickAddButton(View view) {
        if (isVisible) {
            currentMarker.remove();
            currentMarker = null;
            detachFragment();
        } else {
            attachFragment();
        }
    }

    /**
     * Attaches the <Code>CreateSpotFragment</Code>  to the map.
     */
    private void attachFragment() {
        isVisible = true;
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc != null) {
                currentLoc = new LatLng(loc.getLatitude(), loc.getLongitude());
            } else if (currentLoc == null) {
                currentLoc = new LatLng(0, 0);
            }
        }
        currentMarker = mMap.addMarker(new MarkerOptions().position(currentLoc).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        currentMarker.setDraggable(true);

        if ((currentMarker.getPosition().longitude != 0) && (currentMarker.getPosition().latitude != 0)) {
            mMap.animateCamera(CameraUpdateFactory.newLatLng(currentLoc));
        }
        if (fragment == null) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.add(R.id.mapLayout, CreateSpotFragment.newInstance()).commit();
            fragment = (CreateSpotFragment) fragmentManager.findFragmentById(R.id.createSpotFragment);
        } else {
            fragmentManager.beginTransaction().attach(fragment).commit();
        }
        setMapWeight(2);

    }

    /**
     * Sets the layout weight of the map to the given weight.
     * The layout weight specifies how much of the map that should be visible in the activity.
     *
     * @param weight The weight to set the map to.
     */
    private void setMapWeight(int weight) {
        LinearLayout layout = (LinearLayout) findViewById(R.id.mapLayout);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, weight));
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferencesSaver.saveSharedPreferences(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SpotHandler.getInstance().getMap().clear();
    }

    @Override
    public void detachFragment() {
        Keyboard.hideKeyboard(this);
        isVisible = false;
        fragment.clearFields();
        fragmentManager.beginTransaction().detach(fragment).commit();

        setMapWeight(0);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (currentMarker != null && currentMarker.isDraggable()) {
            currentMarker.setPosition(latLng);
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }

    public SpotHandler getHandler() {
        return handler;
    }

    @Override
    public Marker getCurrentMarker() {
        return currentMarker;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_map, menu);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        return true;
    }

}