package com.winlkar.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.winlkar.app.databinding.ActivityPassengerBinding;
import com.winlkar.app.model.ActiveTrip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PassengerActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ActivityPassengerBinding binding;
    private GoogleMap googleMap;

    private DatabaseReference activeTripsRef;
    private ChildEventListener tripsListener;

    private final Map<String, Marker> busMarkers = new HashMap<>();
    private final List<Station> stations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPassengerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeStations();

        if (!FirebaseApp.getApps(this).isEmpty()) {
            activeTripsRef = FirebaseDatabase.getInstance().getReference("activeTrips");
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.passengerMapContainer);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        drawStations();
        attachTripsRealtimeListener();

        LatLng defaultCenter = new LatLng(33.5731, -7.5898);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 11f));
    }

    private void initializeStations() {
        stations.add(new Station("Station A", new LatLng(33.5731, -7.5898)));
        stations.add(new Station("Station B", new LatLng(33.5890, -7.6030)));
        stations.add(new Station("Station C", new LatLng(33.5585, -7.6205)));
        stations.add(new Station("Station D", new LatLng(33.5400, -7.5802)));
    }

    private void drawStations() {
        if (googleMap == null) {
            return;
        }

        for (Station station : stations) {
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(station.location)
                    .title(station.name)
                    .snippet("Station")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            if (marker != null) {
                marker.setTag("station");
            }
        }
    }

    private void attachTripsRealtimeListener() {
        if (activeTripsRef == null) {
            return;
        }

        tripsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                upsertBusMarker(snapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                upsertBusMarker(snapshot);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String busId = snapshot.getKey();
                Marker marker = busMarkers.remove(busId);
                if (marker != null) {
                    marker.remove();
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };

        activeTripsRef.addChildEventListener(tripsListener);
    }

    private void upsertBusMarker(DataSnapshot snapshot) {
        if (googleMap == null) {
            return;
        }

        ActiveTrip trip = snapshot.getValue(ActiveTrip.class);
        if (trip == null || trip.getBusId() == null || trip.getLat() == null || trip.getLng() == null) {
            return;
        }

        LatLng position = new LatLng(trip.getLat(), trip.getLng());
        String snippet = buildBusSnippet(trip, position);

        Marker existing = busMarkers.get(trip.getBusId());
        if (existing == null) {
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(position)
                    .title("Bus " + trip.getBusId())
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

            if (marker != null) {
                marker.setTag(trip.getBusId());
                busMarkers.put(trip.getBusId(), marker);
            }
            return;
        }

        existing.setPosition(position);
        existing.setSnippet(snippet);
    }

    private String buildBusSnippet(ActiveTrip trip, LatLng busPosition) {
        String route = trip.getRouteDescription() == null ? "Unknown route" : trip.getRouteDescription();
        Station nextStation = findNearestStation(busPosition);

        if (nextStation == null) {
            return "ID: " + trip.getBusId() + "\nRoute: " + route;
        }

        double distanceKm = distanceKm(busPosition, nextStation.location);
        int etaMinutes = (int) Math.round((distanceKm / 30.0) * 60.0);
        if (etaMinutes < 1) {
            etaMinutes = 1;
        }

        return String.format(Locale.US,
                "ID: %s\nRoute: %s\nNext: %s (~%d min)",
                trip.getBusId(),
                route,
                nextStation.name,
                etaMinutes);
    }

    private Station findNearestStation(LatLng position) {
        Station nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Station station : stations) {
            double dist = distanceKm(position, station.location);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = station;
            }
        }

        return nearest;
    }

    private double distanceKm(LatLng a, LatLng b) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(b.latitude - a.latitude);
        double dLon = Math.toRadians(b.longitude - a.longitude);

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitude))
                * Math.cos(Math.toRadians(b.latitude))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return earthRadiusKm * c;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeTripsRef != null && tripsListener != null) {
            activeTripsRef.removeEventListener(tripsListener);
        }
    }

    private static class Station {
        final String name;
        final LatLng location;

        Station(String name, LatLng location) {
            this.name = name;
            this.location = location;
        }
    }
}
