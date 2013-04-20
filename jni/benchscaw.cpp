#include <string.h>
#include <jni.h>
#include <cpu-features.h>
extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/cpu.h"
#include "libavutil/opt.h"
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#define VPX_CODEC_DISABLE_COMPAT 1
#include "vpx/vpx_encoder.h"
#include "vpx/vp8cx.h"
#define interface (vpx_codec_vp8_cx())

#include "benchscaw.h"

}
extern "C" {


static void mem_put_le16(char *mem, unsigned int val) {
	mem[0] = val;
	mem[1] = val>>8;
}

static void mem_put_le32(char *mem, unsigned int val) {
	mem[0] = val;
	mem[1] = val>>8;
	mem[2] = val>>16;
	mem[3] = val>>24;
}

static void write_ivf_file_header(FILE *outfile,
		const vpx_codec_enc_cfg_t *cfg,
		int frame_cnt) {
	char header[32];

	if(cfg->g_pass != VPX_RC_ONE_PASS && cfg->g_pass != VPX_RC_LAST_PASS)
		return;
	header[0] = 'D';
	header[1] = 'K';
	header[2] = 'I';
	header[3] = 'F';
	mem_put_le16(header+4,  0);                   /* version */
	mem_put_le16(header+6,  32);                  /* headersize */
	mem_put_le32(header+8,  fourcc);              /* headersize */
	mem_put_le16(header+12, cfg->g_w);            /* width */
	mem_put_le16(header+14, cfg->g_h);            /* height */
	mem_put_le32(header+16, cfg->g_timebase.den); /* rate */
	mem_put_le32(header+20, cfg->g_timebase.num); /* scale */
	mem_put_le32(header+24, frame_cnt);           /* length */
	mem_put_le32(header+28, 0);                   /* unused */

	(void) fwrite(header, 1, 32, outfile);
}

static void write_ivf_frame_header(FILE *outfile,
		const vpx_codec_cx_pkt_t *pkt)
{
	char             header[12];
	vpx_codec_pts_t  pts;

	if(pkt->kind != VPX_CODEC_CX_FRAME_PKT)
		return;

	pts = pkt->data.frame.pts;
	mem_put_le32(header, pkt->data.frame.sz);
	mem_put_le32(header+4, pts&0xFFFFFFFF);
	mem_put_le32(header+8, pts >> 32);

	(void) fwrite(header, 1, 12, outfile);
}

//snow
/*static unsigned char random_char (void)
{
	static unsigned int state;

	state *= 1103515245;
	state += 12345;
	return (state >> 16) & 0xff;
}

static void fill_frame(int frame_number, vpx_image_t *img) {
	size_t to_read;

	to_read = img->w*img->h*3/2;
	int x;
	for(x=0; x<to_read; x++)
		img->planes[0][x]=random_char();
}*/

//hacked from gstreamer (lgpl)
static void fill_frame(int frame_number, int width, int height, vpx_image_t *img) {
	size_t to_read;

	int t = frame_number;
	int w = width, h = height;
	int i;
	int j;
	int xreset = -(w / 2);
	int yreset = -(h / 2);
	int x, y;
	int accum_kx;
	int accum_kxt;
	int accum_ky;
	int accum_kyt;
	int accum_kxy;
	int kt;
	int kt2;
	int ky2;
	int delta_kxt = 0 * t;
	int delta_kxy;
	int scale_kxy = 0xffff / (w / 2);
	int scale_kx2 = 0xffff / w;
	int vky=0;
	int vkt=10;
	int vkxy=0;
	int vkyt=0;
	int vky2=10;
	int vk0=0;
	int vkx=0;
	int vkx2=10;
	int vkt2=0;
	  /* optimised version, with original code shown in comments */
	  accum_ky = 0;
	  accum_kyt = 0;
	  kt = vkt * t;
	  kt2 = vkt2 * t * t;
	  for (j = 0, y = yreset; j < h; j++, y++) {
	    accum_kx = 0;
	    accum_kxt = 0;
	    accum_ky += vky;
	    accum_kyt += vkyt * t;
	    delta_kxy = vkxy * y * scale_kxy;
	    accum_kxy = delta_kxy * xreset;
	    ky2 = (vky2 * y * y) / h;
	    for (i = 0, x = xreset; i < w; i++, x++) {

	      /* zero order */
	      int phase = vk0;

	      /* first order */
	      accum_kx += vkx;
	      /* phase = phase + (v->kx * i) + (v->ky * j) + (v->kt * t); */
	      phase = phase + accum_kx + accum_ky + kt;

	      /* cross term */
	      accum_kxt += delta_kxt;
	      accum_kxy += delta_kxy;
	      /* phase = phase + (v->kxt * i * t) + (v->kyt * j * t); */
	      phase = phase + accum_kxt + accum_kyt;

	      /* phase = phase + (v->kxy * x * y) / (w/2); */
	      /* phase = phase + accum_kxy / (w/2); */
	      phase = phase + (accum_kxy >> 16);

	      /*second order */
	      /*normalise x/y terms to rate of change of phase at the picture edge */
	      /*phase = phase + ((v->kx2 * x * x)/w) + ((v->ky2 * y * y)/h) + ((v->kt2 * t * t)>>1); */
	      phase = phase + ((vkx2 * x * x * scale_kx2) >> 16) + ky2 + (kt2 >> 1);
			img->planes[0][(j*w)+i] = sine_table[phase & 0xff];
			img->planes[1][((j*w)+i)/2] = sine_table[phase & 0xff];
		}
	}
}

/*
static void fill_frame(int frame_number, vpx_image_t *img) {
	size_t to_read;

	to_read = img->w*img->h*3/2;
	int x;
	for(x=0; x<to_read; x++)
		img->planes[0][x]=random_char();
}
 */

JNIEXPORT jstring JNICALL Java_ws_websca_benchscaw_MainActivity_vpxOpen( JNIEnv* env, jobject thiz, jstring path, jint w, jint h, jint threads )
{

	vpx_codec_err_t      res;

	int width = w;
	int height = h;
	const char *nativeString = env->GetStringUTFChars(path, 0);
	if(!(outfile = fopen(nativeString, "wb")))
		return env->NewStringUTF("Failed to open ivf for writing");
	env->ReleaseStringUTFChars(path, nativeString);

	if(!vpx_img_alloc(&raw, VPX_IMG_FMT_I420, width, height, 1))
		return env->NewStringUTF("Failed to allocate image");


	res = vpx_codec_enc_config_default(interface, &cfg, 0);
	if(res) {
		return env->NewStringUTF( vpx_codec_err_to_string(res));
	}

	cfg.rc_target_bitrate = width * height * cfg.rc_target_bitrate	/ cfg.g_w / cfg.g_h;
	cfg.g_w = width;
	cfg.g_h = height;
	cfg.g_threads=threads;

	if(vpx_codec_enc_init(&codec, interface, &cfg, 0))
		return env->NewStringUTF("Failed to initialize encoder");

	if(threads==2)
		vpx_codec_control(&codec, VP8E_SET_TOKEN_PARTITIONS, VP8_ONE_TOKENPARTITION);
	else if(threads>2)
		vpx_codec_control(&codec, VP8E_SET_TOKEN_PARTITIONS, VP8_TWO_TOKENPARTITION);

	write_ivf_file_header(outfile, &cfg, 0);

	return env->NewStringUTF("vpx ok");
}

JNIEXPORT jstring JNICALL Java_ws_websca_benchscaw_MainActivity_vpxNextFrame( JNIEnv* env, jobject thiz, jstring path, jint w, jint h )
{
	vpx_codec_iter_t iter = NULL;
	const vpx_codec_cx_pkt_t *pkt;
	int                  flags = 0;
	char r[3];

	int frame_avail = 1;
	fill_frame(frame_cnt, w, h, &raw);
	if(vpx_codec_encode(&codec, frame_avail? &raw : NULL, frame_cnt, 1, flags, VPX_DL_REALTIME))
		return  env->NewStringUTF("Failed to encode frame\n");

	while( (pkt = vpx_codec_get_cx_data(&codec, &iter)) ) {

		switch(pkt->kind) {
		case VPX_CODEC_CX_FRAME_PKT:                                  //
			write_ivf_frame_header(outfile, pkt);                     //
			(void) fwrite(pkt->data.frame.buf, 1, pkt->data.frame.sz, //
					outfile);                                   //
			break;                                                    //
		default:
			break;
		}
		sprintf(r, pkt->kind == VPX_CODEC_CX_FRAME_PKT
		       && (pkt->data.frame.flags & VPX_FRAME_IS_KEY)? "K":".");
		//fflush(stdout);
	}
	frame_cnt++;
	return env->NewStringUTF(r);
}

JNIEXPORT jstring JNICALL Java_ws_websca_benchscaw_MainActivity_vpxClose( JNIEnv* env, jobject thiz, jstring path )
{
	//printf("Processed %d frames.\n",frame_cnt-1);
	vpx_img_free(&raw);                                                       //
	if(vpx_codec_destroy(&codec))                                             //
		return  env->NewStringUTF("Failed to destroy codec");                         //

	/* Try to rewrite the file header with the actual frame count */
	if(!fseek(outfile, 0, SEEK_SET))
		write_ivf_file_header(outfile, &cfg, frame_cnt-1);
	fclose(outfile);
	return env->NewStringUTF("libvpx success.\n");
	return env->NewStringUTF("libvpx success.\n");
}


int ffmpegNextFrame() {
	//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame cpu flags:", "flags");

	//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame", "av_read_frame");
	int done = av_read_frame(pFormatCtx, &packet);
	if(done>=0) {
		// Is this a packet from the video stream?
		if(packet.stream_index==videoStream) {
			// Decode video frame
			//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame", "avcodec_decode_video2");
			avcodec_decode_video2(pCodecCtx, pFrame, &frameFinished, &packet);

			// Did we get a video frame?
			if(frameFinished) {
				// Convert the image from its native format to RGB
				sws_scale
				(
						sws_ctx,
						(uint8_t const * const *)pFrame->data,
						pFrame->linesize,
						0,
						pCodecCtx->height,
						pFrameRGB->data,
						pFrameRGB->linesize
				);

				// Save the frame to disk
				//if(++i<=10)
				;//SaveFrame(pFrameRGB, pFrameRGB->width, pFrameRGB->height, i);
			}
		}

		// Free the packet that was allocated by av_read_frame
		//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame", "av_free_packet");
		av_free_packet(&packet);
	}
	return done;
}

jint Java_ws_websca_benchscaw_MainActivity_directRender( JNIEnv* env, jobject thiz, jobject surface ) {
	//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame cpu flags:", "flags");



	//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame", "av_read_frame");
	int done = av_read_frame(pFormatCtx, &packet);
	if(done>=0) {
		// Is this a packet from the video stream?
		if(packet.stream_index==videoStream) {
			// Decode video frame
			//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame", "avcodec_decode_video2");
			avcodec_decode_video2(pCodecCtx, pFrame, &frameFinished, &packet);

			// Did we get a video frame?
			if(frameFinished) {
				ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
				ANativeWindow_Buffer buffer;
				if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
					// Convert the image from its native format to RGB
					AVPicture pict;
					pict.data[0] = (uint8_t*)buffer.bits;
					pict.linesize[0] = buffer.stride*4;
					sws_scale
					(
							sws_ctx,
							(uint8_t const * const *)pFrame->data,
							pFrame->linesize,
							0,
							pCodecCtx->height,
							pict.data,
							pict.linesize
					);

					//char str[200];
					//sprintf(str, "%i", buffer.width);
					///__android_log_write(ANDROID_LOG_DEBUG, "width", str);
					ANativeWindow_unlockAndPost(window);
				}
				ANativeWindow_release(window);
			}
		}

		// Free the packet that was allocated by av_read_frame
		//__android_log_write(ANDROID_LOG_DEBUG, "Benchscaw JNI native nextFrame", "av_free_packet");
		av_free_packet(&packet);
	}
	return done;
}

jint Java_ws_websca_benchscaw_MainActivity_surfaceTest( JNIEnv* env, jobject thiz, jobject surface )
{
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_surfaceTest", "surfaceTest()");
	if(ffmpegNextFrame()<0)
		return -1;
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_surfaceTest", "ANativeWindow_fromSurface()");
	ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
	ANativeWindow_Buffer buffer;
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_surfaceTest", "ANativeWindow_lock()");
	if (ANativeWindow_lock(window, &buffer, NULL) == 0) {
		int y=0;

		for(y=0;y<pFrameRGB->height;y++)
		{
			memcpy((uint8_t *)buffer.bits+(y*buffer.stride*4), pFrameRGB->data[0]+y*pFrameRGB->linesize[0], pFrameRGB->width*4);
		}
		//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_surfaceTest", "ANativeWindow_unlock_and_post()");
		ANativeWindow_unlockAndPost(window);
	}
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_surfaceTest", "ANativeWindow_release()");
	ANativeWindow_release(window);
	return 1;
}

JNIEXPORT jstring JNICALL Java_ws_websca_benchscaw_MainActivity_ffmpegOpen( JNIEnv* env,	jobject thiz, jobject surface, jstring path)
{
	ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_surfaceTest", "ANativeWindow_lock()");
	int w = ANativeWindow_getWidth(window);
	int h = ANativeWindow_getHeight(window);
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "ffmpegOpen()");
	int numBytes;

	char errstr[200];
	int err=0;

	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "av_register_all()");
	av_register_all();
	// Open video file
	//return avformat_version();
	//return  env->NewStringUTF(avformat_license());

	const char *nativeString = env->GetStringUTFChars(path, 0);

	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avformat_open_input");
	err = avformat_open_input(&pFormatCtx, nativeString, NULL, NULL);
	env->ReleaseStringUTFChars(path, nativeString);
	if(err!=0) {
		av_strerror(err, errstr, 200);
		return env->NewStringUTF(errstr);
	}

	// Retrieve stream information
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avformat_find_stream_info");
	if(avformat_find_stream_info(pFormatCtx, NULL)<0)
		return env->NewStringUTF("Couldn't find stream information"); // Couldn't find stream information

	// Dump information about file onto standard error
	//av_dump_format(pFormatCtx, 0, "/sdcard/test.webm", 0);

	// Find the first video stream
	videoStream=-1;
	for(i=0; i<pFormatCtx->nb_streams; i++)
		if(pFormatCtx->streams[i]->codec->codec_type==AVMEDIA_TYPE_VIDEO) {
			videoStream=i;
			break;
		}
	if(videoStream==-1)
		return env->NewStringUTF("Didn't find a video stream");

	// Get a pointer to the codec context for the video stream
	pCodecCtx=pFormatCtx->streams[videoStream]->codec;


	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avcodec_find_decoder");
	// Find the decoder for the video stream
	pCodec=avcodec_find_decoder(pCodecCtx->codec_id);
	if(pCodec==NULL) {
		return env->NewStringUTF("Codec not found");
	}

	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avcodec_open2");
	// Open codec
	if(avcodec_open2(pCodecCtx, pCodec, &optionsDict)<0)
		return env->NewStringUTF("Could not open codec");

	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avcodec_alloc_frame()");
	// Allocate video frame
	pFrame=avcodec_alloc_frame();
	if(pFrame==NULL)
		return env->NewStringUTF("avcodec_alloc_frame() failed");
	// Allocate an AVFrame structure

	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avcodec_alloc_frame()");
	pFrameRGB=avcodec_alloc_frame();
	if(pFrameRGB==NULL)
		return env->NewStringUTF("avcodec_alloc_frame() failed");

	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avpicture_get_size()");
	// Determine required buffer size and allocate buffer
	numBytes=avpicture_get_size(AV_PIX_FMT_RGBA, w,h);
	buffer=(uint8_t *)av_malloc(numBytes*sizeof(uint8_t));

	ANativeWindow_setBuffersGeometry( window, pCodecCtx->width, pCodecCtx->height, WINDOW_FORMAT_RGBA_8888);
	sws_ctx =
			sws_getContext
			(
					pCodecCtx->width,
					pCodecCtx->height,
					pCodecCtx->pix_fmt,
					(int)pCodecCtx->width,
					(int)pCodecCtx->height,
					AV_PIX_FMT_RGBA,
					SWS_FAST_BILINEAR,
					NULL,
					NULL,
					NULL
			);
	pFrameRGB->width=w;
	pFrameRGB->height=h;
	// Assign appropriate parts of buffer to image planes in pFrameRGB
	// Note that pFrameRGB is an AVFrame, but AVFrame is a superset
	// of AVPicture
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avpicture_fill()");
	avpicture_fill((AVPicture *)pFrameRGB, buffer, AV_PIX_FMT_RGBA, w, h);

	i=0;
	return env->NewStringUTF("OK");
}

JNIEXPORT jstring JNICALL Java_ws_websca_benchscaw_MainActivity_ffmpegClose( JNIEnv* env, jobject thiz )
{

	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "av_free()");
	// Free the RGB image
	av_free(buffer);
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "av_free()");
	av_free(pFrameRGB);

	// Free the YUV frame
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "av_free()");
	av_free(pFrame);

	// Close the codec
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "av_close()");
	avcodec_close(pCodecCtx);

	// Close the video file
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegOpen", "avformat_close_input()");
	avformat_close_input(&pFormatCtx);


	return env->NewStringUTF("FFMPEG Close OK");
}




/////////////cpudetect stuff

jstring
Java_ws_websca_benchscaw_MainActivity_stringFromJNI( JNIEnv* env,
		jobject thiz )
{
	AndroidCpuFamily acf = android_getCpuFamily();
	switch(android_getCpuFamily()) {
	case ANDROID_CPU_FAMILY_ARM:
		return env->NewStringUTF("ANDROID_CPU_FAMILY_ARM");
		break;
	case ANDROID_CPU_FAMILY_X86:
		return env->NewStringUTF("ANDROID_CPU_FAMILY_X86");
		break;
	case ANDROID_CPU_FAMILY_MIPS:
		return env->NewStringUTF("ANDROID_CPU_FAMILY_MIPS");
		break;
	}
	return env->NewStringUTF("UNKNOWN");
}

jboolean Java_ws_websca_benchscaw_MainActivity_getCpuArmNeon( JNIEnv* env,
		jobject thiz )
{
	if(android_getCpuFamily()!=ANDROID_CPU_FAMILY_ARM)
		return JNI_FALSE;
	if ((android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_NEON) != 0)
		return JNI_TRUE;
	else
		return JNI_FALSE;
}

jboolean Java_ws_websca_benchscaw_MainActivity_getCpuArmv7( JNIEnv* env,
		jobject thiz )
{
	if(android_getCpuFamily()!=ANDROID_CPU_FAMILY_ARM)
		return JNI_FALSE;
	if ((android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_ARMv7) != 0)
		return JNI_TRUE;
	else
		return JNI_FALSE;
}

jboolean Java_ws_websca_benchscaw_MainActivity_getCpuArmVFPv3( JNIEnv* env,
		jobject thiz )
{
	if(android_getCpuFamily()!=ANDROID_CPU_FAMILY_ARM)
		return JNI_FALSE;
	if ((android_getCpuFeatures() & ANDROID_CPU_ARM_FEATURE_VFPv3) != 0)
		return JNI_TRUE;
	else
		return JNI_FALSE;
}

jboolean Java_ws_websca_benchscaw_MainActivity_getCpuX86SSSE3( JNIEnv* env,
		jobject thiz )
{
	if(android_getCpuFamily()!=ANDROID_CPU_FAMILY_X86)
		return JNI_FALSE;
	if ((android_getCpuFeatures() & ANDROID_CPU_X86_FEATURE_SSSE3) != 0)
		return JNI_TRUE;
	else
		return JNI_FALSE;
}

jboolean Java_ws_websca_benchscaw_MainActivity_getCpuX86POPCNT( JNIEnv* env,
		jobject thiz )
{
	if(android_getCpuFamily()!=ANDROID_CPU_FAMILY_X86)
		return JNI_FALSE;
	if ((android_getCpuFeatures() & ANDROID_CPU_X86_FEATURE_POPCNT) != 0)
		return JNI_TRUE;
	else
		return JNI_FALSE;
}

jboolean Java_ws_websca_benchscaw_MainActivity_getCpuX86MOVBE( JNIEnv* env,
		jobject thiz )
{
	if(android_getCpuFamily()!=ANDROID_CPU_FAMILY_X86)
		return JNI_FALSE;
	if ((android_getCpuFeatures() & ANDROID_CPU_X86_FEATURE_MOVBE) != 0)
		return JNI_TRUE;
	else
		return JNI_FALSE;
}

jint Java_ws_websca_benchscaw_MainActivity_getCpuCount( JNIEnv* env,
		jobject thiz )
{
	return (jint)android_getCpuCount();
}


JNIEXPORT jstring JNICALL Java_ws_websca_benchscaw_MainActivity_ffmpegCpuFlags( JNIEnv* env,	jobject thiz, jint w, jint h, jstring path)
{


	char str[2000];
	int cpu_flags = av_get_cpu_flags();
	int i;

	sprintf(str, "");
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegCpuFlags", str);
	for (i = 0; cpu_flag_tab[i].flag; i++)
		if (cpu_flags & cpu_flag_tab[i].flag) {
			sprintf(str, "%s %s", str, cpu_flag_tab[i].name);

		}
	//__android_log_write(ANDROID_LOG_DEBUG, "Java_ws_websca_benchscaw_MainActivity_ffmpegCpuFlags", str);
	return env->NewStringUTF(str);
}
}

