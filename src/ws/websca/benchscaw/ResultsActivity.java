package ws.websca.benchscaw;

import ws.websca.benchscaw.R;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;


public class ResultsActivity extends Activity {

	Clip[] clips;
	int currentClip=0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_results);
		Clip[] results = (Clip[])getIntent().getSerializableExtra("ws.websca.benchscaw.results");
		Log.e("results", ""+results.length);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public void onStart() {
		super.onStart();
	}
}
