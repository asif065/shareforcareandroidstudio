package com.asif.shareforcare.activity;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;

import com.asif.shareforcare.R;
import com.asif.shareforcare.helper.SQLiteHandler;
import com.asif.shareforcare.helper.SessionManager;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationListener;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements
		ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

	private TextView txtName;
	private TextView txtEmail;
	private TextView txtLatitude;
	private TextView txtLongitude;
	private Button btnLogout;
	private double dblLatitude;
	private double dblLongitude;
	protected Button mStartUpdatesButton;
	protected Button mStopUpdatesButton;

	private SQLiteHandler db;
	private SessionManager session;

	protected static final String TAG = "MainActivity";

	/**
	 * The desired interval for location updates. Inexact. Updates may be more or less frequent.
	 */
	public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

	/**
	 * The fastest rate for active location updates. Exact. Updates will never be more frequent
	 * than this value.
	 */
	public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
			UPDATE_INTERVAL_IN_MILLISECONDS / 2;

	// Keys for storing activity state in the Bundle.
	protected final static String REQUESTING_LOCATION_UPDATES_KEY = "requesting-location-updates-key";
	protected final static String LOCATION_KEY = "location-key";
	protected final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";

	/**
	 * Provides the entry point to Google Play services.
	 */
	protected GoogleApiClient mGoogleApiClient;


	/**
	 * Represents a geographical location.
	 */
	protected Location mLastLocation;

	/**
	 * Stores parameters for requests to the FusedLocationProviderApi.
	 */
	protected LocationRequest mLocationRequest;

	/**
	 * Represents a geographical location.
	 */
	protected Location mCurrentLocation;

	// Labels.
	protected String mLatitudeLabel;
	protected String mLongitudeLabel;
	protected String mLastUpdateTimeLabel;

	/**
	 * Tracks the status of the location updates request. Value changes when the user presses the
	 * Start Updates and Stop Updates buttons.
	 */
	protected Boolean mRequestingLocationUpdates;

	/**
	 * Time when the location was updated represented as a String.
	 */
	protected String mLastUpdateTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mLatitudeLabel = getResources().getString(R.string.latitude_label);
		mLongitudeLabel = getResources().getString(R.string.longitude_label);
		mLastUpdateTimeLabel = getResources().getString(R.string.last_update_time_label);

		txtName = (TextView) findViewById(R.id.name);
		txtEmail = (TextView) findViewById(R.id.email);
		txtLatitude = (TextView) findViewById(R.id.latitude);
		txtLongitude = (TextView) findViewById(R.id.longitude);

		btnLogout = (Button) findViewById(R.id.btnLogout);
		mStartUpdatesButton = (Button) findViewById(R.id.start_updates_button);
		mStopUpdatesButton = (Button) findViewById(R.id.stop_updates_button);

		// SqLite database handler
		db = new SQLiteHandler(getApplicationContext());

		// session manager
		session = new SessionManager(getApplicationContext());

		if (!session.isLoggedIn()) {
			logoutUser();
		}

		// Fetching user details from sqlite
		HashMap<String, String> user = db.getUserDetails();

		String name = user.get("name");
		String email = user.get("email");

		// Displaying the user details on the screen
		txtName.setText(name);
		txtEmail.setText(email);


		// Logout button click event
		btnLogout.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				logoutUser();
			}
		});

		mRequestingLocationUpdates = false;
		mLastUpdateTime = "";

		// Update values using data stored in the Bundle.
		updateValuesFromBundle(savedInstanceState);

		Log.i(TAG, "Calling Build");

		buildGoogleApiClient();

	}

	/**
	 * Updates fields based on data stored in the bundle.
	 *
	 * @param savedInstanceState The activity state saved in the Bundle.
	 */
	private void updateValuesFromBundle(Bundle savedInstanceState) {
		Log.i(TAG, "Updating values from bundle");
		if (savedInstanceState != null) {
			// Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
			// the Start Updates and Stop Updates buttons are correctly enabled or disabled.
			if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
				mRequestingLocationUpdates = savedInstanceState.getBoolean(
						REQUESTING_LOCATION_UPDATES_KEY);
			}

			// Update the value of mCurrentLocation from the Bundle and update the UI to show the
			// correct latitude and longitude.
			if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
				// Since LOCATION_KEY was found in the Bundle, we can be sure that mCurrentLocation
				// is not null.
				mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
			}

			// Update the value of mLastUpdateTime from the Bundle and update the UI.
			if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
				mLastUpdateTime = savedInstanceState.getString(LAST_UPDATED_TIME_STRING_KEY);
			}
			updateUI();
		}
	}


	/**
	 * Updates the latitude, the longitude, and the last location time in the UI.
	 */
	private void updateUI() {
		txtLatitude.setText(String.format("%s: %f", mLatitudeLabel,
				mCurrentLocation.getLatitude()));
		txtLongitude.setText(String.format("%s: %f", mLongitudeLabel,
				mCurrentLocation.getLongitude()));
		//mLastUpdateTimeTextView.setText(String.format("%s: %s", mLastUpdateTimeLabel,
		//		mLastUpdateTime));
	}

	/**
	 * Ensures that only one button is enabled at any time. The Start Updates button is enabled
	 * if the user is not requesting location updates. The Stop Updates button is enabled if the
	 * user is requesting location updates.
	 */
	private void setButtonsEnabledState() {
		if (mRequestingLocationUpdates) {
			mStartUpdatesButton.setEnabled(false);
			mStopUpdatesButton.setEnabled(true);
		} else {
			mStartUpdatesButton.setEnabled(true);
			mStopUpdatesButton.setEnabled(false);
		}
	}

	/**
	 * Handles the Start Updates button and requests start of location updates. Does nothing if
	 * updates have already been requested.
	 */
	public void startUpdatesButtonHandler(View view) {
		if (!mRequestingLocationUpdates) {
			mRequestingLocationUpdates = true;
			setButtonsEnabledState();
			startLocationUpdates();
		}
	}

	/**
	 * Handles the Stop Updates button, and requests removal of location updates. Does nothing if
	 * updates were not previously requested.
	 */
	public void stopUpdatesButtonHandler(View view) {
		if (mRequestingLocationUpdates) {
			mRequestingLocationUpdates = false;
			setButtonsEnabledState();
			stopLocationUpdates();
		}
	}

	/**
	 * Requests location updates from the FusedLocationApi.
	 */
	protected void startLocationUpdates() {
		// The final argument to {@code requestLocationUpdates()} is a LocationListener
		// (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
		LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
	}

	/**
	 * Removes location updates from the FusedLocationApi.
	 */
	protected void stopLocationUpdates() {
		// It is a good practice to remove location requests when the activity is in a paused or
		// stopped state. Doing so helps battery performance and is especially
		// recommended in applications that request frequent location updates.

		// The final argument to {@code requestLocationUpdates()} is a LocationListener
		// (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
		LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
	}


	/**
	 * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
	 */
	protected synchronized void buildGoogleApiClient(){
		mGoogleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(LocationServices.API)
				.build();
		Log.i(TAG, "Build");
		createLocationRequest();
	}

	/**
	 * Sets up the location request. Android has two location request settings:
	 * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
	 * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
	 * the AndroidManifest.xml.
	 * <p/>
	 * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
	 * interval (5 seconds), the Fused Location Provider API returns location updates that are
	 * accurate to within a few feet.
	 * <p/>
	 * These settings are appropriate for mapping applications that show real-time location
	 * updates.
	 */
	protected void createLocationRequest() {
		mLocationRequest = new LocationRequest();

		// Sets the desired interval for active location updates. This interval is
		// inexact. You may not receive updates at all if no location sources are available, or
		// you may receive them slower than requested. You may also receive updates faster than
		// requested if other applications are requesting location at a faster interval.
		mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

		// Sets the fastest rate for active location updates. This interval is exact, and your
		// application will never receive updates faster than this value.
		mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
	}

	@Override
	protected void onStart() {
		super.onStart();
		mGoogleApiClient.connect();

		Toast.makeText(this, "OnStart", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onResume() {
		super.onResume();
		// Within {@code onPause()}, we pause location updates, but leave the
		// connection to GoogleApiClient intact.  Here, we resume receiving
		// location updates if the user has requested them.

		if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
			startLocationUpdates();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		// Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
		if (mGoogleApiClient.isConnected()) {
			stopLocationUpdates();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if(mGoogleApiClient.isConnected()){
			mGoogleApiClient.disconnect();
		}
	}

	/**
	 * Runs when a GoogleApiClient object successfully connects.
	 */
	@Override
	public void onConnected(Bundle connectionHint) {
		// Provides a simple way of getting a device's location and is well suited for
		// applications that do not require a fine-grained location and that do not need location
		// updates. Gets the best and most recent location currently available, which may be null
		// in rare cases when a location is not available.
//		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == getPackageManager().PERMISSION_GRANTED) {
//			mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
//
//			if (mLastLocation != null) {
//				dblLatitude = mLastLocation.getLatitude();
//				dblLongitude = mLastLocation.getLongitude();
//				txtLatitude.setText(Double.toString(dblLatitude));
//				txtLongitude.setText(Double.toString(dblLongitude));
//				Toast.makeText(this, String.format("%s, %s: %f, %f", mLatitudeLabel, mLongitudeLabel, mLastLocation.getLatitude(), mLastLocation.getLongitude()), Toast.LENGTH_LONG).show();
//			} else {
//				Toast.makeText(this, R.string.no_location_detected, Toast.LENGTH_LONG).show();
//			}
//		}

		// If the initial location was never previously requested, we use
		// FusedLocationApi.getLastLocation() to get it. If it was previously requested, we store
		// its value in the Bundle and check for it in onCreate(). We
		// do not request it again unless the user specifically requests location updates by pressing
		// the Start Updates button.
		//
		// Because we cache the value of the initial location in the Bundle, it means that if the
		// user launches the activity,
		// moves to a new location, and then changes the device orientation, the original location
		// is displayed as the activity is re-created.

		if(mCurrentLocation == null){
			mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
			mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
			updateUI();
		}

		// If the user presses the Start Updates button before GoogleApiClient connects, we set
		// mRequestingLocationUpdates to true (see startUpdatesButtonHandler()). Here, we check
		// the value of mRequestingLocationUpdates and if it is true, we start location updates.
		if (mRequestingLocationUpdates) {
			startLocationUpdates();
		}
	}

	@Override
	public void onConnectionFailed(ConnectionResult result){
		Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
	}


	@Override
	public void onConnectionSuspended(int Cause){
		Log.i(TAG, "Connection suspended");
		mGoogleApiClient.connect();
	}

	/**
	 * Logging out the user. Will set isLoggedIn flag to false in shared
	 * preferences Clears the user data from sqlite users table
	 */
	private void logoutUser() {
		session.setLogin(false);

		db.deleteUsers();

		// Launching the login activity
		Intent intent = new Intent(MainActivity.this, LoginActivity.class);
		startActivity(intent);
		finish();
	}

	/**
	 * Callback that fires when the location changes.
	 */
	@Override
	public void onLocationChanged(Location location) {
		mCurrentLocation = location;
		mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
		updateUI();
		Toast.makeText(this, getResources().getString(R.string.location_updated_message),
				Toast.LENGTH_SHORT).show();
	}

	/**
	 * Stores activity data in the Bundle.
	 */
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
		savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
		savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
		super.onSaveInstanceState(savedInstanceState);
	}
}
