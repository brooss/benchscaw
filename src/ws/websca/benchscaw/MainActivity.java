package ws.websca.benchscaw;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.os.Build;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements Callback, OnErrorListener, OnInfoListener, Runnable {

	public native String dlopen();
	public native String ffmpegOpen(Surface surface, String path);
	public native String ffmpegClose();
	public native int surfaceTest(Surface surface);
	public native int directRender(Surface surface);
	public native boolean getCpuArmNeon();
	public native boolean getCpuArmv7();
	public native boolean getCpuArmVFPv3();
	public native boolean getCpuX86SSSE3();
	public native boolean getCpuX86POPCNT();
	public native boolean getCpuX86MOVBE();
	public native int getCpuCount();
	public native String ffmpegCpuFlags();
	public native String vpxOpen(String path, int w, int h, int threads);
	public native String vpxNextFrame(String path, int w, int h);
	public native String vpxClose(String path);

	public boolean showResults=false;
	static {
		System.loadLibrary("ffmpeg");
		System.loadLibrary("benchscaw");
	}
	
	private SurfaceView surfaceView;
	private SurfaceHolder surfaceHolder;
	public static String BROADCAST_ACTION_CLIPDONE = "ws.webca.benchscaw.CLIPDONE";
	private boolean paused;
	Clip[] clips;
	int currentClip=0;
	private String codec = new String();
	private String codecs = new String();

	//private boolean useMediaCodec;
	private Method method;
	private enum Method{mediacodec, ffmpeg, libvpxencode, notest};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initClips();
	}

	private void initClips() {
		clips = new Clip[6];
		for(int x=0; x<6; x++)
			clips[x]=new Clip();
		clips[0].name = "480x270 WebM";
		clips[0].filename = "270.webm";
		clips[0].width=480;
		clips[0].height=270;
		clips[1].name = "640x360 WebM";
		clips[1].filename = "360.webm";
		clips[1].width=640;
		clips[1].height=360;
		clips[2].name = "852x480 WebM";
		clips[2].filename = "480.webm";
		clips[2].width=852;
		clips[2].height=480;
		clips[3].name = "1024x576 WebM";
		clips[3].filename = "576.webm";
		clips[3].width=1024;
		clips[3].height=576;
		clips[4].name = "1280x720 WebM";
		clips[4].filename = "720.webm";
		clips[4].width=1280;
		clips[4].height=720;
		clips[5].name = "1920x1080 WebM";
		clips[5].filename = "1080.webm";
		clips[5].width=1920;
		clips[5].height=1080;
		
		/*clips[0].fps=60;
		clips[1].fps=50;
		clips[2].fps=39;
		clips[3].fps=35;
		clips[4].fps=31;
		clips[5].fps=19;*/
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


	public void onPause() {
		super.onPause();
		paused=true;
	}

	public void onResume() {
		super.onResume();
		if(showResults) {
			results();
		}
		else {
			//super.onRestart();
			//Intent intent = new Intent(MainActivity.this, StartActivity.class);
			//startActivity(intent);
		}
	}

	public void onRestart() {
		super.onRestart();
		if(showResults) {
			results();
		}
		else {
			super.onRestart();
			Intent intent = new Intent(MainActivity.this, StartActivity.class);
			startActivity(intent);
		}
	}

	private void results() {
		showResults=true;
		surfaceHolder.removeCallback(this);
		setContentView(R.layout.activity_results);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		fillResults();
		TextView method = (TextView)findViewById(R.id.method);
		if(this.method==Method.mediacodec)
			method.setText(getResources().getString(R.string.usingMediaCodec));
		else if(this.method==Method.ffmpeg)
			method.setText(getResources().getString(R.string.usingFFMPEG));
		else if(this.method==Method.libvpxencode)
			method.setText(getResources().getString(R.string.usingLibvpxEncoder));
		else if(this.method==Method.notest)
			method.setText(getResources().getString(R.string.usingNoTest));
		Button button = (Button)findViewById(R.id.buttonCopy);
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); 
				//ClipData clip = ClipData.newPlainText("benchscaw", ((TextView)findViewById(R.id.textview)).getText());
				//clipboard.setPrimaryClip(clip);
				clipboard.setText(((TextView)findViewById(R.id.textview)).getText());
		        Toast.makeText(MainActivity.this, "Details copied to clipboard", Toast.LENGTH_SHORT).show();
			}

		});
		button = (Button)findViewById(R.id.buttonRestart);
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				Intent intent = new Intent(MainActivity.this, StartActivity.class);
				startActivity(intent);
			}
		});

		button = (Button)findViewById(R.id.buttonDetails);
		if(this.method!=Method.notest) {
			((TextView)findViewById(R.id.textview)).setVisibility(View.GONE);
		}
		else  {
			button.setEnabled(false);
			((View)findViewById(R.id.resultsTable)).setVisibility(View.GONE);
		}
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View b) {
				((TextView)findViewById(R.id.textview)).setVisibility(View.VISIBLE);
				b.setEnabled(false);
			}
		});
		button = (Button)findViewById(R.id.buttonSend);
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View b) {
				//b.setEnabled(false);
				sendData();
			}
		});
	}
	public void onStart() {
		super.onStart();
		paused=false;
		setContentView(R.layout.activity_main);
		initClips();
		currentClip=0;
		IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_ACTION_CLIPDONE);
		registerReceiver(this.receiver, filter);
		getWindow().setFormat(PixelFormat.UNKNOWN);
		surfaceView = (SurfaceView)findViewById(R.id.surfaceview);

		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);

		android.view.ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
		lp.width = dm.widthPixels;
		lp.height = (int) (((float)1080 / (float)1920) * (float)lp.width);
		if(lp.height>dm.heightPixels) {
			lp.height=dm.heightPixels;
			lp.width = (int) (((float)1920 / (float)1080) * (float)lp.height);
		}
		//lp.width=480;
		//lp.height=270;
		surfaceView.setLayoutParams(lp);  

		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.setFormat(PixelFormat.RGBA_8888);		//surfaceHolder.setFixedSize(clips[0].width, clips[0].height);
		//setSurfaceSize();
		surfaceHolder.addCallback(this);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);	
		Intent intent = getIntent();

		if(intent.getStringExtra("ws.websca.benchscaw.use").equals("mediacodec"))
			method=Method.mediacodec;
		else if(intent.getStringExtra("ws.websca.benchscaw.use").equals("ffmpeg"))
			method=Method.ffmpeg;
		else if(intent.getStringExtra("ws.websca.benchscaw.use").equals("libvpxencode"))
			method=Method.libvpxencode;
		else if(intent.getStringExtra("ws.websca.benchscaw.use").equals("notest")) {
			method=Method.notest;
			surfaceHolder.removeCallback(this);
			results();
		}
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		if(method==Method.libvpxencode) {
			SurfaceView s = (SurfaceView)findViewById(R.id.surfaceview);
			s.setVisibility(View.GONE);
			TextView t = (TextView)findViewById(R.id.encoderLog);
			t.setVisibility(View.VISIBLE);
			
		}		playNextClip();
	}

	public void surfaceCreated(SurfaceHolder h) {}
	public void surfaceDestroyed(SurfaceHolder arg0) {}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e("mediaPlayer", "error");
		return false;
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		Log.e("onInfo", ""+what);
		return false;
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			if(paused)
				return;
			if(currentClip<clips.length) {
				//setSurfaceSize();
				//surfaceHolder.setFixedSize(clips[currentClip].width, clips[currentClip].height);
				//surfaceHolder.setFormat(PixelFormat.RGBA_8888);
				playNextClip();
			}
			else {
				results();
			}
		};
	};

	
	private void sendData() {
		final Thread thread = new Thread(new Runnable(){
		    @Override
		    public void run() {
				String r=new String();
				try {
					r=r+"?version=2";
					r=r+"&cpuCount="+URLEncoder.encode(""+getCpuCount(), "UTF-8");

				r=r+"&cpuFeatures="+URLEncoder.encode(getCpuFeatures(), "UTF-8");
				r=r+"&ffmpegCpuFlags=" + URLEncoder.encode(ffmpegCpuFlags(), "UTF-8");
				if(method==Method.mediacodec)
					r=r+"&method=mediacodec";
				else if(method==Method.ffmpeg)
					r=r+"&method=ffmpeg";
				else if(method==Method.libvpxencode)
					r=r+"&method=libvpxencode";
				else if(method==Method.notest)
					r=r+"&method=notest";
				r=r+"&codec=" + URLEncoder.encode(codec, "UTF-8");
				r=r+"&codecs=" + URLEncoder.encode(codecs, "UTF-8");
				for(int x=0; x<clips.length; x++) {
					r=r+"&clip"+x;
					r=r+"="+URLEncoder.encode(""+clips[x].fps, "UTF-8");
				}
				r=r+"&board="+URLEncoder.encode(android.os.Build.BOARD, "UTF-8");
				r=r+"&brand="+URLEncoder.encode(android.os.Build.BRAND, "UTF-8");
				r=r+"&cpu_abi="+URLEncoder.encode( android.os.Build.CPU_ABI, "UTF-8");
				r=r+"&hardware="+URLEncoder.encode( android.os.Build.HARDWARE, "UTF-8");
				r=r+"&device="+URLEncoder.encode( android.os.Build.DEVICE, "UTF-8");
				r=r+"&display="+URLEncoder.encode( android.os.Build.DISPLAY, "UTF-8");
				r=r+"&fingerprint="+URLEncoder.encode( android.os.Build.FINGERPRINT, "UTF-8");	 	
				r=r+"&host="+URLEncoder.encode( android.os.Build.HOST, "UTF-8");
				r=r+"&id="+URLEncoder.encode( android.os.Build.ID, "UTF-8");
				r=r+"&manufacturer="+URLEncoder.encode( android.os.Build.MANUFACTURER , "UTF-8");
				r=r+"&model="+URLEncoder.encode( android.os.Build.MODEL, "UTF-8");
				r=r+"&product="+URLEncoder.encode( android.os.Build.PRODUCT , "UTF-8");
				r=r+"&tags="+URLEncoder.encode( android.os.Build.TAGS , "UTF-8");
				r=r+"&time="+URLEncoder.encode( ""+android.os.Build.TIME, "UTF-8");
				r=r+"&user="+URLEncoder.encode( android.os.Build.USER, "UTF-8");
				r=r+"&os="+URLEncoder.encode(System.getProperty("os.version"), "UTF-8");
				Display d = getWindowManager().getDefaultDisplay();
				r=r+"&window="+URLEncoder.encode((""+d.getWidth()+"x"+d.getHeight()), "UTF-8" );
				r=r+"&sdk="+URLEncoder.encode(""+Build.VERSION.SDK_INT, "UTF-8" );
				r=r+"&camera="+URLEncoder.encode(getCameraInfo(), "UTF-8" );
				} catch (UnsupportedEncodingException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				};

				try {
				    HttpClient client = new DefaultHttpClient();  
				    HttpGet get = new HttpGet("http://websca.ws/benchscawsubmit.php"+r);
				    HttpResponse responseGet = client.execute(get);  
				    HttpEntity resEntityGet = responseGet.getEntity();  
				    if (resEntityGet != null) {  
				        // do something with the response
				        String response = EntityUtils.toString(resEntityGet);
				        Log.i("GET RESPONSE", response);
				        MainActivity.this.runOnUiThread(new Runnable() {
				            public void run() {
								Toast toast = Toast.makeText(getApplicationContext(), "Results sent", Toast.LENGTH_SHORT);
								toast.show();
				            }
				        });
				    }
				    
				} catch (Exception e) {
				    e.printStackTrace();
				}
		    }
		});
	    AlertDialog confirmDialog =new AlertDialog.Builder(this) 
        //set message, title, and icon
        .setTitle("Send to websca.ws") 
        .setMessage("Your detailed benchmark results will be sent to websca.ws where they may be made publicly avalible.") 
        .setIcon(android.R.drawable.ic_menu_share)

        .setPositiveButton("I Agree", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) { 
                dialog.dismiss();
                MainActivity.this.runOnUiThread(new Runnable() {
                	public void run() {
                		((Button)findViewById(R.id.buttonSend)).setEnabled(false);
                	}
                });
        		thread.start();       		
            }   
        })

        .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        })
        .create();
	    confirmDialog.show();


	}

	private void fillResults() {

		
		TextView text = (TextView)findViewById(R.id.textview);
		String r=new String();

		r=r+"CPU Count: "+getCpuCount()+"\n";
		r=r+"CPU Features: "+getCpuFeatures();
		r=r+"\nffmpegCpuFlags: " + ffmpegCpuFlags();
		r=r+"\n";
		if(method==Method.mediacodec)
			r=r+"Using MediaCodec\n";
		else if(method==Method.ffmpeg)
			r=r+"Using FFMPEG\n";
		else if(method==Method.libvpxencode)
			r=r+"Using libvpx encoder\n";
		r=r+"Avalible Codecs: "+codecs+"\n";
		r=r+"Codec: "+codec+"\n";
		for(int x=0; x<clips.length; x++) {
			r=r+"Clip: "+clips[x].name;
			if(method==Method.libvpxencode)
				r=r+" encoded ";
			else
				r=r+" played ";
			r=r+clips[x].frames+" frames in "+clips[x].totalTime+"ms";
			r=r+" = "+clips[x].fps+"FPS";
			r=r+"\n";

		}
		r=r+"Device Details";
		r=r+"\nBoard: "+android.os.Build.BOARD;
		r=r+"\nBrand: "+android.os.Build.BRAND;
		r=r+"\nCPU_ABI: "+ android.os.Build.CPU_ABI;
		r=r+"\nHardware: "+ android.os.Build.HARDWARE;
		r=r+"\nDevice: "+ android.os.Build.DEVICE;
		r=r+"\nDisplay: "+ android.os.Build.DISPLAY;
		r=r+"\nFingerprint: "+ android.os.Build.FINGERPRINT;	 	
		r=r+"\nHost: "+ android.os.Build.HOST;
		r=r+"\nID: "+ android.os.Build.ID;
		r=r+"\nManufacturer: "+ android.os.Build.MANUFACTURER ;
		r=r+"\nModel: "+ android.os.Build.MODEL;
		r=r+"\nProduct: "+ android.os.Build.PRODUCT ;
		r=r+"\nTags: "+ android.os.Build.TAGS ;
		r=r+"\nTime: "+ ""+android.os.Build.TIME;
		r=r+"\nUser: "+ android.os.Build.USER;
		r=r+"\nos.version: "+System.getProperty("os.version");
		Display d = getWindowManager().getDefaultDisplay();
		r=r+"\nwindow: "+d.getWidth()+"x"+d.getHeight();
		r=r+"\nsdk: "+Build.VERSION.SDK_INT;
		r=r+"\ncameraInfo: "+getCameraInfo();
		text.setText(r);


		((TextView)findViewById(R.id.textView270)).setText(""+clips[0].fps+"FPS");
		((TextView)findViewById(R.id.textView360)).setText(""+clips[1].fps+"FPS");
		((TextView)findViewById(R.id.textView480)).setText(""+clips[2].fps+"FPS");
		((TextView)findViewById(R.id.textView576)).setText(""+clips[3].fps+"FPS");
		((TextView)findViewById(R.id.textView720)).setText(""+clips[4].fps+"FPS");
		((TextView)findViewById(R.id.textView1080)).setText(""+clips[5].fps+"FPS");

		if(clips[0].fps<30)
			((TextView)findViewById(R.id.textView270)).setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable( android.R.drawable.presence_offline ), null);
		if(clips[1].fps<30)
			((TextView)findViewById(R.id.textView360)).setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable( android.R.drawable.presence_offline ), null);
		if(clips[2].fps<30)
			((TextView)findViewById(R.id.textView480)).setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable( android.R.drawable.presence_offline ), null);
		if(clips[3].fps<30)
			((TextView)findViewById(R.id.textView576)).setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable( android.R.drawable.presence_offline ), null);
		if(clips[4].fps<30)
			((TextView)findViewById(R.id.textView720)).setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable( android.R.drawable.presence_offline ), null);
		if(clips[5].fps<30)
			((TextView)findViewById(R.id.textView1080)).setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable( android.R.drawable.presence_offline ), null);

	}


	private void playNextClip() {
		Toast toast = Toast.makeText(getApplicationContext(), ""+method+" "+clips[currentClip].name, Toast.LENGTH_SHORT);
		toast.show();
		Thread t = new Thread(this);
		t.start();
	}

	public void run() {
		int frames=0;
		long startMs = System.currentTimeMillis();
		AssetFileDescriptor fd = null;

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && method==Method.mediacodec){
			try {
				Log.d("Benchscaw", "Using MediaCodec");
				fd = this.getApplicationContext().getAssets().openFd(clips[currentClip].filename);
				frames = MEPlayer.MEPlay(fd, surfaceHolder.getSurface());
				Log.d("MainActivity.run()", "fd.close()");
				codec=MEPlayer.codec;
				codecs=MEPlayer.codecs;
				fd.close();
			} catch (IOException e1) {
				e1.printStackTrace();
				return;
			}
		}
		else if(method == Method.ffmpeg){
			Log.d("Benchscaw", "Using FFMPEG");
			String path = this.getCacheDir().getAbsolutePath()+"/"+clips[currentClip].filename;
			try {
				copyFile(getAssets().open(clips[currentClip].filename), new FileOutputStream(path));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			Log.d("native", ffmpegOpen(surfaceHolder.getSurface(), path));
			while(directRender(surfaceHolder.getSurface())>=0) {
				frames++;
			}
			new File(path).delete();
			ffmpegClose();
			codec="ffmpeg";
			codecs="ffmpeg";
		}
		else if(method == Method.libvpxencode) {

            MainActivity.this.runOnUiThread(new Runnable() {
            	public void run() {
        			((TextView)findViewById(R.id.encoderLog)).setText("Encoding 200 frames ("+
            	MainActivity.this.clips[MainActivity.this.currentClip].width+"x"+
            	MainActivity.this.clips[MainActivity.this.currentClip].height+")\n");
            	}
            });

			Log.d("Benchscaw", "Using libvpxendode");
			String path = this.getCacheDir().getAbsolutePath()+"/"+clips[currentClip].filename+".ivf";
			vpxOpen(path, clips[currentClip].width, clips[currentClip].height, getCpuCount());
			String s = new String();
			for(int x=0; x<200; x++) {
				s += vpxNextFrame(path, clips[currentClip].width, clips[currentClip].height);
				//Slow GUI updates to stop it hogging CPU
				if(System.currentTimeMillis()%10==0) {
					final String o = s; 
		            MainActivity.this.runOnUiThread(new Runnable() {
		            	public void run() {
		        			((TextView)findViewById(R.id.encoderLog)).append(o);
		            	}
		            });
		            s="";
				}
				frames++;
			}
			vpxClose(path);
			//TODO let the user copy these to the sdcard?
			new File(path).delete();
		}

		if(frames!=0) {
			clips[currentClip].frames=frames;
			clips[currentClip].totalTime=(System.currentTimeMillis()-startMs);
			clips[currentClip].fps=(frames/(((System.currentTimeMillis())-startMs)/1000));
			Log.v("frames", ""+frames+" frames in "+(System.currentTimeMillis()-startMs)+"ms = "+(frames/((System.currentTimeMillis()-startMs)/1000))+"fps");
		}
		currentClip++;
		Intent broadcast = new Intent();
		broadcast.setAction(BROADCAST_ACTION_CLIPDONE);
		sendBroadcast(broadcast);
	}
	
	private void copyFile(InputStream in, OutputStream out) throws IOException {
		Log.d("Benchscaw", "copyFile");
	    byte[] buffer = new byte[1024];
	    int read;
	    while((read = in.read(buffer)) != -1){
	      out.write(buffer, 0, read);
	    }
	}
	
	private String getCpuFeatures() {
		String r = new String();

		if(getCpuArmNeon()==true)
			r=r+"ANDROID_CPU_ARM_FEATURE_NEON ";
		if(getCpuArmv7()==true)
			r=r+"ANDROID_CPU_ARM_FEATURE_ARMv7 ";
		if(getCpuArmVFPv3()==true)
			r=r+"ANDROID_CPU_ARM_FEATURE_VFPv3 ";
		if(getCpuX86SSSE3()==true)
			r=r+"ANDROID_CPU_X86_FEATURE_SSSE3 ";
		if(getCpuX86POPCNT()==true)
			r=r+"ANDROID_CPU_X86_FEATURE_MOVBE ";
		if(getCpuX86MOVBE()==true)
			r=r+"ANDROID_CPU_X86_FEATURE_MOVBE ";
		return r;
	}
	
	private String getCameraInfo() {
		String r="";
		CameraInfo i = new CameraInfo();
		Camera.getCameraInfo(0, i);
		r=r+Camera.getNumberOfCameras()+"";
		for(int x=0; x<Camera.getNumberOfCameras(); x++) {
			Camera.getCameraInfo(x, i);
			if(i.facing==CameraInfo.CAMERA_FACING_BACK)
				r=r+" "+x+":CAMERA_FACING_BACK";
			else if(i.facing==CameraInfo.CAMERA_FACING_FRONT)
				r=r+" "+x+":CAMERA_FACING_FRONT";
		}
		return r;
	}

}
