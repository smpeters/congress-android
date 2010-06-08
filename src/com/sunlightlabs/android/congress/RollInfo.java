package com.sunlightlabs.android.congress;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.commonsware.cwac.merge.MergeAdapter;
import com.sunlightlabs.android.congress.utils.LegislatorImage;
import com.sunlightlabs.android.congress.utils.LoadPhotoTask;
import com.sunlightlabs.android.congress.utils.Utils;
import com.sunlightlabs.congress.java.CongressException;
import com.sunlightlabs.congress.java.Legislator;
import com.sunlightlabs.congress.java.Roll;
import com.sunlightlabs.services.Services;

public class RollInfo extends ListActivity implements LoadPhotoTask.LoadsPhoto {
	private String id;
	
	private Roll roll;
	private HashMap<String,Roll.Vote> voters;
	
	private LoadRollTask loadRollTask, loadVotersTask;
	private View loadingView;
	
	private HashMap<String,LoadPhotoTask> loadPhotoTasks = new HashMap<String,LoadPhotoTask>();
	private ArrayList<String> queuedPhotos = new ArrayList<String>();
	
	private static final int MAX_PHOTO_TASKS = 10;
	private static final int MAX_QUEUE_TASKS = 20;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_titled_fastscroll);
		
		Bundle extras = getIntent().getExtras();
		id = extras.getString("id");
		
		setupControls();
		
		RollInfoHolder holder = (RollInfoHolder) getLastNonConfigurationInstance();
		if (holder != null) {
			this.loadRollTask = holder.loadRollTask;
			this.roll = holder.roll;
			this.loadVotersTask = holder.loadVotersTask;
			this.voters = holder.voters;
			this.loadPhotoTasks = holder.loadPhotoTasks;
			
			if (loadPhotoTasks != null) {
				Iterator<LoadPhotoTask> iterator = loadPhotoTasks.values().iterator();
				while (iterator.hasNext())
					iterator.next().onScreenLoad(this);
			}
		}
		
		loadRoll();
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		return new RollInfoHolder(loadRollTask, roll, loadVotersTask, voters, loadPhotoTasks);
	}
	
	public void setupControls() {
		Roll tempRoll = Roll.splitRollId(id);
		String title = Utils.capitalize(tempRoll.chamber) + " Roll No. " + tempRoll.number;
		((TextView) findViewById(R.id.title_text)).setText(title);
		Utils.setLoading(this, R.string.roll_loading);
	}
	
	@Override
	public void onListItemClick(ListView parent, View v, int position, long id) {
		String voter_id = (String) v.getTag();
    	if (voter_id != null)
    		startActivity(Utils.legislatorIntent(voter_id));
	}
	
	public void onLoadRoll(String tag, Roll roll) {
		if (tag.equals("basic")) {
			this.loadRollTask = null;
			this.roll = roll;
			displayRoll();
		} else if (tag.equals("voters")) {
			this.loadVotersTask = null;
			this.voters = roll.voters;
			displayVoters();
		}
	}
	
	public void onLoadRoll(String tag, CongressException exception) {
		Utils.alert(this, R.string.error_connection);
		if (tag.equals("basic")) {
			this.loadRollTask = null;
			finish();
		} else if (tag.equals("voters")) {
			this.loadVotersTask = null;
			
			View loadingView = findViewById(R.id.loading_votes);
			loadingView.findViewById(R.id.loading_spinner).setVisibility(View.GONE);
			((TextView) loadingView.findViewById(R.id.loading_message)).setText("Error loading votes.");
		}
	}
	
	public void displayRoll() {
		LayoutInflater inflater = LayoutInflater.from(this);
		MergeAdapter adapter = new MergeAdapter();
		
		LinearLayout header = (LinearLayout) inflater.inflate(R.layout.roll_basic, null);
		((TextView) header.findViewById(R.id.question)).setText(roll.question);
		((TextView) header.findViewById(R.id.voted_at)).setText(new SimpleDateFormat("MMM dd, yyyy").format(roll.voted_at));
		
		View resultHeader = header.findViewById(R.id.result_header);
		((TextView) resultHeader.findViewById(R.id.header_text)).setText("Results");
		((TextView) resultHeader.findViewById(R.id.header_text_right)).setText(roll.required + " majority required");
		
		((TextView) header.findViewById(R.id.result)).setText(roll.result);
		
		((TextView) header.findViewById(R.id.ayes)).setText(roll.ayes + "");
		((TextView) header.findViewById(R.id.nays)).setText(roll.nays + "");
		((TextView) header.findViewById(R.id.present)).setText(roll.present + "");
		((TextView) header.findViewById(R.id.not_voting)).setText(roll.not_voting + "");
		
		((TextView) header.findViewById(R.id.voters_header)).setText("Votes");
		loadingView = header.findViewById(R.id.loading_votes);
		((TextView) loadingView.findViewById(R.id.loading_message)).setText("Loading votes...");
		
		adapter.addView(header);
		setListAdapter(adapter);
		
		// kick off vote loading
		loadVotes();
	}
	
	public void displayVoters() {
		MergeAdapter adapter = (MergeAdapter) getListAdapter();
		
		ArrayList<Roll.Vote> voterArray = new ArrayList<Roll.Vote>(voters.values());
		Collections.sort(voterArray);
		adapter.addAdapter(new VoterAdapter(this, voterArray));
		
		loadingView.setVisibility(View.GONE);
		
		setListAdapter(adapter);
	}
	
	public void loadRoll() {
		if (loadRollTask != null)
			loadRollTask.onScreenLoad(this);
		else {
			if (roll != null)
				displayRoll();
			else
				loadRollTask = (LoadRollTask) new LoadRollTask(this, id, "basic").execute("basic,bill");
		}
	}
	
	public void loadVotes() {
		if (loadVotersTask != null)
			loadVotersTask.onScreenLoad(this);
		else {
			if (voters != null)
				displayVoters();
			else
				loadVotersTask = (LoadRollTask) new LoadRollTask(this, id, "voters").execute("voters");
		}
	}
	
	public void loadPhoto(String bioguide_id) {
		if (!loadPhotoTasks.containsKey(bioguide_id)) {
			// if we have free space, fetch the photo
			if (loadPhotoTasks.size() <= MAX_PHOTO_TASKS) 
				loadPhotoTasks.put(bioguide_id, (LoadPhotoTask) new LoadPhotoTask(this, LegislatorImage.PIC_MEDIUM, bioguide_id).execute(bioguide_id));

			// otherwise, add it to the queue for later
			else {
				if (queuedPhotos.size() > MAX_QUEUE_TASKS)
					queuedPhotos.clear();
				
				if (!queuedPhotos.contains(bioguide_id))
					queuedPhotos.add(bioguide_id);
			}
		}
	}
	
	public void onLoadPhoto(Drawable photo, Object tag) {
		loadPhotoTasks.remove((String) tag);
		
		View result = getListView().findViewWithTag(tag);
		if (result != null) {
			if (photo != null)
				((ImageView) result.findViewById(R.id.photo)).setImageDrawable(photo);
			else // leave as loading, no better solution I can think of right now
				((ImageView) result.findViewById(R.id.photo)).setImageResource(R.drawable.loading_photo);
		}
		
		// if there's any in the queue, send the next one
		if (!queuedPhotos.isEmpty())
			loadPhoto(queuedPhotos.remove(0));
	}
	
	public Context getContext() {
		return this;
	}
	
	
	private class LoadRollTask extends AsyncTask<String,Void,Roll> {
		private RollInfo context;
		private CongressException exception;
		private String rollId, tag;
		
		public LoadRollTask(RollInfo context, String rollId, String tag) {
			this.context = context;
			this.rollId = rollId;
			this.tag = tag;
			Utils.setupDrumbone(context);
		}
		
		public void onScreenLoad(RollInfo context) {
			this.context = context;
		}
		
		@Override
		public Roll doInBackground(String... sections) {
			try {
				return Services.rolls.find(rollId, sections[0]);
			} catch (CongressException exception) {
				this.exception = exception;
				return null;
			}
		}
		
		@Override
		public void onPostExecute(Roll roll) {
			if (exception != null && roll == null)
				context.onLoadRoll(tag, exception);
			else
				context.onLoadRoll(tag, roll);
		}
	}
	
	private class VoterAdapter extends ArrayAdapter<Roll.Vote> {
		LayoutInflater inflater;
		Activity context;
		Resources resources;

	    public VoterAdapter(Activity context, ArrayList<Roll.Vote> items) {
	        super(context, 0, items);
	        this.context = context;
	        this.resources = context.getResources();
	        this.inflater = LayoutInflater.from(context);
	    }
	    
	    public boolean areAllItemsEnabled() {
	    	return true;
	    }

		public View getView(int position, View convertView, ViewGroup parent) {
			Roll.Vote vote = getItem(position);
			Legislator legislator = vote.voter;
			
			LinearLayout view;
			if (convertView == null)
				view = (LinearLayout) inflater.inflate(R.layout.legislator_voter, null);
			else
				view = (LinearLayout) convertView;
			
			// used as the hook to get the legislator image in place when it's loaded
			// and to link to the legislator's activity
			view.setTag(vote.voter_id);
			
			((TextView) view.findViewById(R.id.name)).setText(nameFor(legislator));
			((TextView) view.findViewById(R.id.position)).setText(positionFor(legislator));
			
			TextView voteView = (TextView) view.findViewById(R.id.vote);
			int value = vote.vote;
			switch (value) {
			case Roll.AYE:
				voteView.setText("Aye");
				voteView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
				voteView.setTextColor(resources.getColor(R.color.aye));
				break;
			case Roll.NAY:
				voteView.setText("Nay");
				voteView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
				voteView.setTextColor(resources.getColor(R.color.nay));
				break;
			case Roll.PRESENT:
				voteView.setText("Present");
				voteView.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
				voteView.setTextColor(resources.getColor(R.color.present));
				break;
			case Roll.NOT_VOTING:
				voteView.setText("Not Voting");
				voteView.setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
				voteView.setTextColor(resources.getColor(R.color.not_voting));
				break;
			case Roll.OTHER:
			default:
				voteView.setText(vote.vote_name);
				voteView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
				voteView.setTextColor(resources.getColor(android.R.color.white));
				break;
			}
			
			ImageView photoView = (ImageView) view.findViewById(R.id.photo); 
			BitmapDrawable photo = LegislatorImage.quickGetImage(LegislatorImage.PIC_MEDIUM, legislator.bioguide_id, context);
			if (photo != null)
				photoView.setImageDrawable(photo);
			else {
				photoView.setImageResource(R.drawable.loading_photo);
				RollInfo.this.loadPhoto(legislator.bioguide_id);
			}
			
			return view;
		}
		
		public String nameFor(Legislator legislator) {
			return legislator.last_name + ", " + legislator.firstName();
		}
		
		public String positionFor(Legislator legislator) {
			String district = legislator.district;
			String stateName = Utils.stateCodeToName(context, legislator.state);
			
			String position = "";
			if (district.equals("Senior Seat"))
				position = "Senior Senator from " + stateName;
			else if (district.equals("Junior Seat"))
				position = "Junior Senator from " + stateName;
			else if (district.equals("0")) {
				if (legislator.title.equals("Rep"))
					position = "Representative for " + stateName + " At-Large";
				else
					position = legislator.fullTitle() + " for " + stateName;
			} else
				position = "Representative for " + stateName + "-" + district;
			
			return "(" + legislator.party + ") " + position; 
		}
		
	}
	
	static class RollInfoHolder {
		private LoadRollTask loadRollTask, loadVotersTask;
		private Roll roll;
		private HashMap<String,Roll.Vote> voters;
		HashMap<String,LoadPhotoTask> loadPhotoTasks;
		
		public RollInfoHolder(LoadRollTask loadRollTask, Roll roll, LoadRollTask loadVotersTask, HashMap<String,Roll.Vote> voters, HashMap<String,LoadPhotoTask> loadPhotoTasks) {
			this.loadRollTask = loadRollTask;
			this.roll = roll;
			this.loadVotersTask = loadVotersTask;
			this.voters = voters;
			this.loadPhotoTasks = loadPhotoTasks;
		}
	}
}