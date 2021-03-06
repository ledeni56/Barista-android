package com.delfinerija.baristaApp.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.delfinerija.baristaApp.R;
import com.delfinerija.baristaApp.activities.FindCoffeshopsActivity;
import com.delfinerija.baristaApp.entities.ApiResponse;
import com.delfinerija.baristaApp.entities.MapLocation;
import com.delfinerija.baristaApp.network.ApiService;
import com.delfinerija.baristaApp.network.GenericResponse;
import com.delfinerija.baristaApp.network.InitApiService;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.MODE_PRIVATE;

@SuppressLint("ValidFragment")
public class MapsFragment extends Fragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap gmap;
    private LocationManager mLocationManager;
    private boolean isMyLocationOn = false;
    private ApiService apiService;
    private Call<GenericResponse<List<ApiResponse<MapLocation>>>> getLocations;
    private List<ApiResponse<MapLocation>> locations = new ArrayList<>();


    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";
    private static final int LOCATION_REQUEST_CODE = 1997;
    private static final int ENABLE_LOCATION_RESULT_CODE = 42;

    @SuppressLint("ValidFragment")
    public MapsFragment(List<ApiResponse<MapLocation>> locations){
        this.locations = locations;
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_coffeshop_locations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(savedInstanceState != null){
            InitApiService.initApiService();
        }

        apiService = InitApiService.apiService;


        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY);
        }


        mapView = view.findViewById(R.id.map_view);
        mapView.onCreate(mapViewBundle);
        initMaps();

        if (checkPermission()) {
            checkIfLocationIsEnabled();
        }

    }


    private void checkIfLocationIsEnabled() {
        mLocationManager = (LocationManager) getActivity().getApplicationContext().getSystemService(LOCATION_SERVICE);

        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }else{
            isMyLocationOn = true;
            initMaps();
        }
    }

    private void initMaps() {
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gmap = googleMap;
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        gmap.setMyLocationEnabled(true);
        putPinsOnMap(gmap);
        if(isMyLocationOn){
            zoomToMyLocation(gmap);
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivityForResult(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS),ENABLE_LOCATION_RESULT_CODE);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        isMyLocationOn = false;
                        initMaps();
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }


    private void zoomToMyLocation(GoogleMap googleMap) {
        Location location = getLastKnownLocation();

        if(location != null){
            double longitude = location.getLongitude();
            double latitude = location.getLatitude();
            LatLng latLng = new LatLng(latitude, longitude);

            CameraUpdate yourLocation = CameraUpdateFactory.newLatLngZoom(latLng, 8);
            googleMap.animateCamera(yourLocation);
        }else{
            Toasty.error(getActivity(),"No GPS signal!", Toast.LENGTH_SHORT,false).show();
        }
    }

    private void getPins(final GoogleMap googleMap){
        String userLocation = getUserLocation();
        if(userLocation == null){
            getLocations = apiService.getLocations(getUserToken(),"0.0,0.0");
        }else {
            getLocations = apiService.getLocations(getUserToken(), userLocation);
        }

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    getLocations.clone().enqueue(new Callback<GenericResponse<List<ApiResponse<MapLocation>>>>() {
                        @Override
                        public void onResponse(Call<GenericResponse<List<ApiResponse<MapLocation>>>> call, Response<GenericResponse<List<ApiResponse<MapLocation>>>> response) {
                            if(response.isSuccessful()){
                                locations = response.body().getResponseData();
                                putPinsOnMap(googleMap);
                            }else{
                                try {
                                    showError(response.errorBody().string());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<GenericResponse<List<ApiResponse<MapLocation>>>> call, Throwable t) {
                            showError(t.getMessage());
                            t.printStackTrace();
                        }
                    });
                }
            },100);

    }

    private void putPinsOnMap(GoogleMap googleMap){
        BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(resizeMapIcons(R.drawable.coffee_marker,80,80));
        for(ApiResponse<MapLocation> locationApiResponse : locations){
            MapLocation mapLocation = locationApiResponse.getData();
            googleMap.addMarker(new MarkerOptions().position(getLocationFromString(mapLocation.getCoordinates())).title(mapLocation.getCoffee_shop_name()).icon(icon));
        }
        mapView.invalidate();
    }

    private LatLng getLocationFromString(String location){
        String[] coordinates = location.split(",");
        Double latitude = Double.valueOf(coordinates[0]);
        Double longitude = Double.valueOf(coordinates[1]);
        return new LatLng(latitude,longitude);
    }

    private String getUserLocation(){
        Location location = getLastKnownLocation();
        StringBuilder stringBuilder = new StringBuilder();

        if(location != null){
            double longitude = location.getLongitude();
            double latitude = location.getLatitude();
            LatLng latLng = new LatLng(latitude, longitude);

            stringBuilder.append(latitude).append(",").append(longitude);
            return stringBuilder.toString();
        }else{
            Toasty.error(getActivity(),"No GPS signal.", Toast.LENGTH_SHORT,false).show();
            return null;
        }
    }

    private String getUserToken(){
        SharedPreferences prefs = getActivity().getSharedPreferences("UserData", MODE_PRIVATE);
        String token = prefs.getString("token","");
        return token;
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initMaps();
                    checkIfLocationIsEnabled();
                } else {
                    getActivity().finish();
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();

    }
    @Override
    public void onPause() {
        mapView.onPause();
        super.onPause();
    }
    @Override
    public void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle);
        }

        mapView.onSaveInstanceState(mapViewBundle);
    }

    private Bitmap resizeMapIcons(int iconName, int width, int height){
        Bitmap bm = BitmapFactory.decodeResource(getResources(), iconName);
        return Bitmap.createScaledBitmap(bm, width, height, false);
    }


    private Location getLastKnownLocation() {
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Location l = mLocationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                bestLocation = l;
            }
        }
        return bestLocation;
    }

    public void showError(String message){
        new AlertDialog.Builder(getContext())
                .setTitle("")
                .setMessage(message)
                .setPositiveButton("OK",null)
                .create()
                .show();
    }
}
