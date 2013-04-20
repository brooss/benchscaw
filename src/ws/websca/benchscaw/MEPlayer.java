package ws.websca.benchscaw;

import java.nio.ByteBuffer;

import android.annotation.TargetApi;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.os.Build;
import android.util.Log;
import android.view.Surface;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MEPlayer {
	
	public static String codec="";
	public static String codecs="";
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public static int MEPlay(AssetFileDescriptor assetFileDescriptor, Surface surface) {


		MediaCodec decoder = null;
		int frame = 0;
		MediaExtractor extractor = new MediaExtractor();

		AssetFileDescriptor rfd;
		rfd = assetFileDescriptor;

		int numCodecs = MediaCodecList.getCodecCount();
		Log.d("MEPlayer.MEPlay", "Found " + numCodecs + "codecs");
		codecs="";
		for (int i = 0; i < numCodecs; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			Log.d("MEPlayer.MEPlay", "Found " + info.getName());
			codecs+=info.getName()+" ";
		}

		extractor.setDataSource(rfd.getFileDescriptor(), rfd.getStartOffset(), rfd.getDeclaredLength());
		for (int i = 0; i < extractor.getTrackCount(); i++) {
			MediaFormat format = extractor.getTrackFormat(i);
			String mime = format.getString(MediaFormat.KEY_MIME);
			Log.d("MEPlayer.MEPlay", "MIMETYPE: "+mime);
			if (mime.startsWith("video/")) {
				extractor.selectTrack(i);
				for (int x = 0; x < numCodecs; x++) {
					MediaCodecInfo info = MediaCodecList.getCodecInfoAt(x);
					if(!info.isEncoder()) {
						String[] types = info.getSupportedTypes();
						for(String type : types) {
							if(type.equals(mime)) {
								Log.d("MEPlayer.MEPlay", "Found codec "+info.getName()+" supporting "+mime);
								if(decoder==null) {
									Log.d("MEPlayer.MEPlay", "Using codec "+info.getName());
									decoder = MediaCodec.createByCodecName(info.getName());
									Log.d("MEPlayer.MEPlay", "Created MediaCodec object");
									codec=info.getName();
								}
							}
						}
					}
				}
				//decoder = MediaCodec.createDecoderByType(mime);
				Log.d("MEPlayer.MEPlay", "configuring decoder: "+format);
				decoder.configure(format, surface, null, 0);

				break;
			}
		}
		if (decoder == null) {
			Log.e("DecodeActivity", "Can't find video info!");
			return 0;
		}
		decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING );
		decoder.start();


		ByteBuffer[] inputBuffers = decoder.getInputBuffers();
		decoder.getOutputBuffers();
		BufferInfo info = new BufferInfo();
		boolean isEOS = false;
		long startMs = System.currentTimeMillis();

		for(;;) {
			if (!isEOS) {
				int inIndex = decoder.dequeueInputBuffer(100000);
				if (inIndex >= 0) {
					ByteBuffer buffer = inputBuffers[inIndex];
					int sampleSize = extractor.readSampleData(buffer, 0);
					if (sampleSize < 0) {
						Log.d("Decode", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
						decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						isEOS = true;
					} else {
						//decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
						decoder.queueInputBuffer(inIndex, 0, sampleSize, 0, 0);
						extractor.advance();
					}
				}
			}

			int outIndex = decoder.dequeueOutputBuffer(info, 100000);
			if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
				Log.d("Decode", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
				break;
			}
			switch (outIndex) {
			case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				decoder.getOutputBuffers();
				break;
			case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				Log.d("Decode", "New format " + decoder.getOutputFormat());
				break;
			case MediaCodec.INFO_TRY_AGAIN_LATER:
				Log.d("Decode", "dequeueOutputBuffer timed out!");
				break;
			default:
				frame++;
				//Log.v("Decode", "frame");
				decoder.releaseOutputBuffer(outIndex, true);
				break;
			}
		}
		Log.d("MEPlayer.MEPlay", "calling decoder.stop()");
		decoder.stop();
		//Log.d("MEPlayer.MEPlay", "calling release.stop()");
		//decoder.release();
		//Log.d("MEPlayer.MEPlay", "calling extractor.release()");
		//extractor.release();
		Log.d("MEPlayer.MEPlay", "returning"+frame);
		return frame;
	}



	public static boolean isAvalible() {
		String sClassName = "android.media.MediaExtractor";  
		try {  
			Class.forName(sClassName);  
			return true;
		} catch (ClassNotFoundException e) {  
			return false;
		} catch (Exception e) {  
			return false;  
		}
	}
}
