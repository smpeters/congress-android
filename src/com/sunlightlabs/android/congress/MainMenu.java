package com.sunlightlabs.android.congress;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.sunlightlabs.android.congress.utils.States;

public class MainMenu extends Activity {
	public static final int RESULT_ZIP = 1;
	public static final int RESULT_LASTNAME = 2;
	public static final int RESULT_STATE = 3;
	
	private static final int ABOUT = 0;
	private static final int FIRST = 1;
	
	private Location location;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        setupControls();
        
        if (firstTime())
        	showDialog(FIRST);
    }
	
	
	public void setupControls() {
        Button fetchZip = (Button) this.findViewById(R.id.fetch_zip);
        Button fetchLocation = (Button) this.findViewById(R.id.fetch_location);
        
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		location = null;
		
		if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
			location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		
		if (location == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
			location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        
    	if (location == null) {
    		fetchLocation.setEnabled(false);
	    	fetchLocation.setText(R.string.no_location);
    	} else {
    		fetchLocation.setOnClickListener(new View.OnClickListener() {
	    		public void onClick(View v) {
	    			if (location != null)
	    				searchByLatLong(location.getLatitude(), location.getLongitude());
	    		}
	    	});
    	}
    	
    	fetchZip.setOnClickListener(new View.OnClickListener() {
    		public void onClick(View v) {
    			getResponse(RESULT_ZIP);
    		}
    	});
    	
    	Button fetchLastName = (Button) this.findViewById(R.id.fetch_last_name);
    	fetchLastName.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				getResponse(RESULT_LASTNAME);
			}
		});
    	
    	Button fetchState = (Button) this.findViewById(R.id.fetch_state);
    	fetchState.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				getResponse(RESULT_STATE);
			}
		});
    }
	
	public void searchByZip(String zipCode) {
		Bundle extras = new Bundle();
		extras.putString("zip_code", zipCode);
		search(extras);
    }
	
	public void searchByLatLong(double latitude, double longitude) {
		Bundle extras = new Bundle();
		extras.putDouble("latitude", latitude);
		extras.putDouble("longitude", longitude);
		search(extras);
	}
	
	public void searchByLastName(String lastName) {
		Bundle extras = new Bundle();
		extras.putString("last_name", lastName);
		search(extras);
	}
	
	public void searchByState(String state) {
		Bundle extras = new Bundle();
		extras.putString("state", state);
		search(extras);
	}
	
	private void search(Bundle extras) {
		Intent i = new Intent();
		i.setClassName("com.sunlightlabs.android.congress", "com.sunlightlabs.android.congress.LegislatorList");
		i.putExtras(extras);
		startActivity(i);
	}
	
	private void getResponse(int requestCode) {
		Intent intent = new Intent();
		
		
		switch (requestCode) {
		case RESULT_ZIP:
			intent.setClassName("com.sunlightlabs.android.congress", "com.sunlightlabs.android.congress.GetText");
			intent.putExtra("ask", "Enter a zip code:");
			intent.putExtra("hint", "e.g. 11216");
			intent.putExtra("inputType", InputType.TYPE_CLASS_NUMBER);
			break;
		case RESULT_LASTNAME:
			intent.setClassName("com.sunlightlabs.android.congress", "com.sunlightlabs.android.congress.GetText");
			intent.putExtra("ask", "Enter a last name:");
			intent.putExtra("hint", "e.g. Schumer");
			intent.putExtra("inputType", InputType.TYPE_TEXT_FLAG_CAP_WORDS);
			break;
		case RESULT_STATE:
			intent.setClassName("com.sunlightlabs.android.congress", "com.sunlightlabs.android.congress.GetState");
			break;
		default:
			break;
		}
		
		startActivityForResult(intent, requestCode);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case RESULT_ZIP:
			if (resultCode == RESULT_OK) {
				String zipCode = data.getExtras().getString("response").trim();
				if (!zipCode.equals(""))
					searchByZip(zipCode);
			}
			break;
		case RESULT_LASTNAME:
			if (resultCode == RESULT_OK) {
				String lastName = data.getExtras().getString("response").trim();
				if (!lastName.equals(""))
					searchByLastName(lastName);
			}
			break;
		case RESULT_STATE:
			if (resultCode == RESULT_OK) {
				String state = data.getExtras().getString("response").trim();
				
				String code = States.nameToCode(state.trim());
				if (code != null)
					state = code;
				
				if (!state.equals(""))
					searchByState(state);
			}
			break;
		}
	}
	
	public boolean firstTime() {
		if (Preferences.getBoolean(this, "first_time", true)) {
			Preferences.setBoolean(this, "first_time", false);
			return true;
		}
		return false;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	LayoutInflater inflater = getLayoutInflater();
    	
        switch(id) {
        case ABOUT:
        	LinearLayout aboutView = (LinearLayout) inflater.inflate(R.layout.about, null);
        	
        	TextView about3 = (TextView) aboutView.findViewById(R.id.about_3);
        	about3.setText(R.string.about_3);
        	Linkify.addLinks(about3, Linkify.WEB_URLS);
        	
        	builder.setView(aboutView);
        	builder.setPositiveButton(R.string.about_button, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
				}
			});
            return builder.create();
        case FIRST:
        	ScrollView firstView = (ScrollView) inflater.inflate(R.layout.first_time, null);
        	
        	builder.setView(firstView);
        	builder.setPositiveButton(R.string.first_button, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {}
			});
            return builder.create();
        default:
            return null;
        }
    }
	
	@Override 
    public boolean onCreateOptionsMenu(Menu menu) { 
	    super.onCreateOptionsMenu(menu); 
	    getMenuInflater().inflate(R.menu.main, menu);
	    return true;
    }
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) { 
    	case R.id.settings: 
    		startActivity(new Intent(this, Preferences.class));
    		break;
    	case R.id.feedback:
    		Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getResources().getString(R.string.contact_email), null));
    		intent.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.contact_subject));
    		startActivity(intent);
    		break;
    	case R.id.about:
    		showDialog(ABOUT);
    		break;
    	}
    	return true;
    }
}