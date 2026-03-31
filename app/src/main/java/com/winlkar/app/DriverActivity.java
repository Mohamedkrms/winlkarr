package com.winlkar.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.winlkar.app.databinding.ActivityDriverBinding;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DriverActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "DriverActivity";
    private static final int LOCATION_PERMISSION_CODE = 101;

    private ActivityDriverBinding binding;
    private GoogleMap googleMap;
    private Marker driverMarker;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private DatabaseReference activeTripsRef;

    private Place startPlace;
    private Place endPlace;
    private String currentBusId;
    private boolean isTripActive;
    private boolean firebaseReady;

    // Use standard StartActivityForResult contract as the custom Places contract is not available
    private final ActivityResultLauncher<Intent> startPlaceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    startPlace = Autocomplete.getPlaceFromIntent(result.getData());
                    refreshRouteSummary();
                } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                    Status status = Autocomplete.getStatusFromIntent(result.getData());
                    handleAutocompleteError(status, true);
                }
            });

    private final ActivityResultLauncher<Intent> endPlaceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    endPlace = Autocomplete.getPlaceFromIntent(result.getData());
                    refreshRouteSummary();
                } else if (result.getResultCode() == AutocompleteActivity.RESULT_ERROR) {
                    Status status = Autocomplete.getStatusFromIntent(result.getData());
                    handleAutocompleteError(status, false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDriverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeSdkClients();
        initializeMap();
        initializeUi();
    }

    private void initializeSdkClients() {
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.maps_api_key));
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        firebaseReady = !FirebaseApp.getApps(this).isEmpty();
        if (!firebaseReady) {
            Toast.makeText(this, "Firebase not configured. Add google-services.json.", Toast.LENGTH_LONG).show();
        } else {
            activeTripsRef = FirebaseDatabase.getInstance().getReference("activeTrips");
        }

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location == null) {
                    return;
                }

                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                updateDriverMarker(latLng);

                if (isTripActive) {
                    publishLiveLocation(latLng);
                }
            }
        };
    }

    private void initializeMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.driverMapContainer);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initializeUi() {
        binding.selectStartButton.setOnClickListener(v -> openPlacePicker(true));
        binding.selectEndButton.setOnClickListener(v -> openPlacePicker(false));

        binding.startTripButton.setOnClickListener(v -> startTrip());
        binding.endTripButton.setOnClickListener(v -> endTrip());
    }

    private void openPlacePicker(boolean isStart) {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG);
        Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .build(this);

        if (isStart) {
            startPlaceLauncher.launch(intent);
        } else {
            endPlaceLauncher.launch(intent);
        }
    }

    private void handleAutocompleteError(Status status, boolean isStartPicker) {
        String pickerName = isStartPicker ? "Start" : "End";
        String rawMessage = status.getStatusMessage() == null ? "Unknown error" : status.getStatusMessage();
        String lowerMessage = rawMessage.toLowerCase(Locale.US);

        Log.e(TAG, "Autocomplete Error (" + pickerName + ") code=" + status.getStatusCode()
                + " (" + CommonStatusCodes.getStatusCodeString(status.getStatusCode()) + ")"
                + " message=" + rawMessage);

        if (lowerMessage.contains("legacy")) {
            Toast.makeText(this,
                    "Places is configured as legacy in Google Cloud. Enable Places API (New), "
                            + "keep Maps SDK for Android enabled, and allow this key to use both APIs.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Error: " + rawMessage, Toast.LENGTH_LONG).show();
    }

    private void refreshRouteSummary() {
        String from = startPlace != null ? startPlace.getName() : "?";
        String to = endPlace != null ? endPlace.getName() : "?";
        binding.routeSummaryText.setText(String.format(Locale.US, "Route: %s -> %s", from, to));
    }

    private void startTrip() {
        if (isTripActive) {
            return;
        }

        if (!firebaseReady) {
            Toast.makeText(this, "Firebase is required to start a trip", Toast.LENGTH_SHORT).show();
            return;
        }

        if (startPlace == null || endPlace == null) {
            Toast.makeText(this, "Please select start and end points", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }

        String typedBusId = binding.busIdInput.getText() == null
                ? ""
                : binding.busIdInput.getText().toString().trim();

        currentBusId = typedBusId.isEmpty()
                ? "BUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US)
                : typedBusId;

        binding.busIdInput.setText(currentBusId);
        isTripActive = true;
        binding.startTripButton.setEnabled(false);
        binding.endTripButton.setEnabled(true);

        startLocationUpdates();
        Toast.makeText(this, "Trip started for " + currentBusId, Toast.LENGTH_SHORT).show();
    }

    private void endTrip() {
        if (!isTripActive) {
            return;
        }

        stopLocationUpdates();
        if (activeTripsRef != null) {
            activeTripsRef.child(currentBusId).removeValue();
        }

        isTripActive = false;
        binding.startTripButton.setEnabled(true);
        binding.endTripButton.setEnabled(false);

        Toast.makeText(this, "Trip ended", Toast.LENGTH_SHORT).show();
    }

    private void publishLiveLocation(LatLng latLng) {
        if (activeTripsRef == null) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("busId", currentBusId);
        payload.put("routeFrom", startPlace.getName());
        payload.put("routeTo", endPlace.getName());
        payload.put("routeDescription", startPlace.getName() + " -> " + endPlace.getName());
        payload.put("lat", latLng.latitude);
        payload.put("lng", latLng.longitude);
        payload.put("lastUpdated", System.currentTimeMillis());
        payload.put("active", true);

        activeTripsRef.child(currentBusId).setValue(payload);
    }

    private void updateDriverMarker(LatLng latLng) {
        if (googleMap == null) {
            return;
        }

        if (driverMarker == null) {
            driverMarker = googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Current bus location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
        } else {
            driverMarker.setPosition(latLng);
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (googleMap != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    googleMap.setMyLocationEnabled(true);
                }
            }
            if (isTripActive) {
                startLocationUpdates();
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        if (hasLocationPermission()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                googleMap.setMyLocationEnabled(true);
            }
        } else {
            requestLocationPermission();
        }

        LatLng defaultCenter = new LatLng(33.5731, -7.5898);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 11f));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isTripActive) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }
}
