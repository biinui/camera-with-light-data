package com.example.camerawithlightdata

import android.content.Context
import android.graphics.Rect
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.os.Looper.prepare
import android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.FrameLayout
import java.io.File
import java.io.IOException
import java.nio.file.Files.exists
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    val TAG = "aaagh"

    private var mCamera: Camera? = null
    private var mPreview: CameraPreview? = null
    private lateinit var mCameraHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create an instance of Camera
        mCamera = getCameraInstance()

        mPreview = mCamera?.let {
            // Create our Preview view
            CameraPreview(this, it)
        }

        // Set the Preview view as the content of our activity.
        mPreview?.also {
            val preview: FrameLayout = findViewById(R.id.camera_preview)
            preview.addView(it)
        }

        var isRecording = false
        val captureButton: Button= findViewById(R.id.button_capture)
        captureButton.setOnClickListener {
            if (isRecording) {
                try {
                    // stop recording and release camera
                    mediaRecorder?.stop() // stop the recording
//                    mCameraHandler.sendEmptyMessage(STOP)
                } catch (e: Exception) {
                    Log.d(TAG, "stopRecording: ${e.message}")
                }

                releaseMediaRecorder() // release the MediaRecorder object
                mCamera?.lock() // take camera access back from MediaRecorder

                // inform the user that recording has stopped
                captureButton?.text = "Record"
                isRecording = false
            } else {
                // initialize video camera
                mCamera?.stopPreview()
                if (prepareVideoRecorder()) {
                    // Camera is available and unlocked, MediaRecorder is prepared,
                    // now you can start recording
                    try {
                        // stop recording and release camera
//                        mediaRecorder?.start()
                        mCameraHandler.sendEmptyMessage(START)
                    } catch (e: Exception) {
                        Log.d(TAG, "startRecording: ${e.message}")
                    }
                    // inform the user that recording has started
                    captureButton?.text = "Stop"
                    isRecording = true
                } else {
                    // prepare didn't work, release the camera
                    releaseMediaRecorder()
                    // inform user
                }
            }
        }

        val handlerThread: HandlerThread = HandlerThread("Camera Handler Thread")
        handlerThread.start()
        mCameraHandler = CameraHandler(handlerThread.looper)
    }

    /** A safe way to get an instance of the Camera object. */
    fun getCameraInstance(): Camera? {
        return try {
            Camera.open(0) // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            Log.d(TAG, "getCameraInstance: ${e.message}")
            null // returns null if camera is unavailable
        }
    }

    /** A basic Camera preview class */
    inner class CameraPreview(
        context: Context,
        private val mCamera: Camera
    ) : SurfaceView(context), SurfaceHolder.Callback {
        private val mHolder: SurfaceHolder = holder.apply {
            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            addCallback(this@CameraPreview)
            // deprecated setting, but required on Android versions prior to 3.0
            setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            mCamera.apply {
                try {
                    Log.d(TAG, "CameraPreview: surfaceCreated")
//                    val profile: CamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
//                    val parameters: Camera.Parameters? = mCamera?.parameters
//                    parameters?.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight)
//                    mCamera?.parameters = parameters
                    setPreviewDisplay(holder)
                    startPreview()
                } catch (e: IOException) {
                    Log.d(TAG, "Error setting camera preview: ${e.message}")
                }
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.
            if (mHolder.surface == null) {
                // preview surface does not exist
                return
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview()
            } catch (e: Exception) {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            mCamera.apply {
                try {
                    val profile: CamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                    val parameters: Camera.Parameters? = mCamera?.parameters
                    parameters?.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight)
                    mCamera?.parameters = parameters
                    setPreviewDisplay(mHolder)
                    startPreview()
                } catch (e: Exception) {
                    Log.d(TAG, "Error starting camera preview: ${e.message}")
                }
            }
        }
    }

    private fun prepareVideoRecorder(): Boolean {
        mediaRecorder = MediaRecorder()

        mCamera?.let { camera ->

            val profile: CamcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);

            // Step 1: Unlock and set camera to MediaRecorder
            camera?.unlock()

            mediaRecorder?.run {
                setCamera(camera)

                // Step 2: Set sources
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)

                // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
                setProfile(profile)
                setAudioSamplingRate(16000);

                // Step 4: Set output file
                setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString())

                // Step 5: Set the preview output
                setPreviewDisplay(mPreview?.holder?.surface)

                // Step 6: Prepare configured MediaRecorder
                return try {
                    prepare()
                    true
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "IllegalStateException preparing MediaRecorder: ${e.message}")
                    releaseMediaRecorder()
                    false
                } catch (e: IOException) {
                    Log.d(TAG, "IOException preparing MediaRecorder: ${e.message}")
                    releaseMediaRecorder()
                    false
                }
            }

        }
        return false
    }

    var mediaRecorder: MediaRecorder? = null

    override fun onPause() {
        super.onPause()
        releaseMediaRecorder() // if you are using MediaRecorder, release it first
        releaseCamera() // release the camera immediately on pause event
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.reset() // clear recorder configuration
        mediaRecorder?.release() // release the recorder object
        mediaRecorder = null
        mCamera?.lock() // lock camera for later use
    }

    private fun releaseCamera() {
        mCamera?.release() // release the camera for other applications
        mCamera = null
    }

    val MEDIA_TYPE_VIDEO = 2

    /** Create a file Uri for saving an image or video */
    private fun getOutputMediaFileUri(type: Int): Uri {
        return Uri.fromFile(getOutputMediaFile(type))
    }

    /** Create a File for saving an image or video */
    private fun getOutputMediaFile(type: Int): File? {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "MyCameraApp"
        )
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        mediaStorageDir.apply {
            if (!exists()) {
                if (!mkdirs()) {
                    Log.d("MyCameraApp", "failed to create directory")
                    return null
                }
            }
        }

        // Create a media file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return when (type) {
            MEDIA_TYPE_VIDEO -> {
                File("${mediaStorageDir.path}${File.separator}VID_$timeStamp.mp4")
            }
            else -> null
        }
    }

    fun setParameters() {
        val params: Camera.Parameters? = mCamera?.parameters
        params?.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        mCamera?.parameters = params

        params?.apply {
            if (maxNumMeteringAreas > 0) { // check that metering areas are supported
                meteringAreas = ArrayList<Camera.Area>().apply {
                    val areaRect1 = Rect(-100, -100, 100, 100) // specify an area in center of image
                    add(Camera.Area(areaRect1, 600)) // set weight to 60%
                    val areaRect2 = Rect(800, -1000, 1000, -800) // specify an area in upper right of image
                    add(Camera.Area(areaRect2, 400)) // set weight to 40%
                }
            }
            mCamera?.parameters = this
        }
    }

    private val START = 0;
    private val STOP  = 1;

    inner class CameraHandler(looper: Looper?): Handler(looper) {

        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            try {
                when (msg?.what) {
                    START -> mediaRecorder?.start()
                    STOP -> {
//                        mediaRecorder?.setOnErrorListener(null)
//                        mediaRecorder?.setOnInfoListener(null)
//                        mediaRecorder?.setPreviewDisplay(null)
                        mediaRecorder?.stop()
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}