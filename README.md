# WinLkar Android App (Java)

A fully functional Android app with two interfaces:

- Driver interface: create route, start trip, and publish real-time bus location.
- Passenger interface: see all active buses and stations in real time.

## Tech stack

- Java (Android)
- Google Maps SDK
- Google Places Autocomplete
- Firebase Realtime Database (no authentication)

## Features

### Driver

- Select start and end points via Places Autocomplete.
- Enter manual bus ID or auto-generate one when starting.
- Start trip and publish live GPS location to Firebase.
- See current bus marker on map.
- End trip and remove bus from active trips list.

### Passenger

- Open map and instantly see active buses.
- See predefined station markers.
- Tap bus marker to view:
  - Bus ID
  - Route description
  - Estimated time to nearest station (simple approximation)
- Bus markers move in real time via Firebase listeners.

## Project structure

- app/src/main/java/com/winlkar/app/MainActivity.java
- app/src/main/java/com/winlkar/app/DriverActivity.java
- app/src/main/java/com/winlkar/app/PassengerActivity.java
- app/src/main/java/com/winlkar/app/model/ActiveTrip.java

## Setup

1. Create a Firebase project.
2. Enable Realtime Database.
3. Download google-services.json and place it at:
   - app/google-services.json
4. In Google Cloud Console, enable:

- Maps SDK for Android
- Places API (New)

1. Ensure billing is enabled on the Google Cloud project.
1. In API key restrictions, allow this key to call:

- Maps SDK for Android
- Places API (New)

1. If you use Android app restrictions for the key, add the app package and SHA-1 that match your debug/release signing.
1. Put your Maps API key in:

- app/src/main/res/values/strings.xml
- Replace `YOUR_GOOGLE_MAPS_API_KEY`.

1. Sync and run in Android Studio.

## Firebase Realtime Database schema

Data is written under:

- activeTrips/{busId}

Each item includes:

- busId
- routeFrom
- routeTo
- routeDescription
- lat
- lng
- lastUpdated
- active

## Firebase rules (no auth)

Use this for development only:

```json
{
  "rules": {
    "activeTrips": {
      ".read": true,
      ".write": true
    }
  }
}
```

## Notes

- No authentication is used, as requested.
- Driver location sharing uses high-accuracy fused location updates.
- If google-services.json is missing, app still builds, but Firebase features are disabled.
