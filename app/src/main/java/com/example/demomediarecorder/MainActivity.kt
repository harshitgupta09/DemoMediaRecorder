package com.example.demomediarecorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.demomediarecorder.ForegroundService
import com.example.demomediarecorder.MainActivity
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var mScreenDensity = 0
    private var mProjectionManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    //private var MediaProjectionCallback=mMediaProjectionCallback;
    private var mMediaProjectionCallback: MediaProjectionCallback? = null
    private var mToggleButton: ToggleButton? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var serviceIntent: Intent? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE = 1000
        private const val DISPLAY_WIDTH = 720
        private const val DISPLAY_HEIGHT = 1280
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_PERMISSIONS = 10

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        serviceIntent = Intent(this, ForegroundService::class.java)
        serviceIntent!!.putExtra("inputExtra", "Foreground Service Example in Android")
        ContextCompat.startForegroundService(this, serviceIntent!!)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        val saveContext: Context = this
        mMediaRecorder = MediaRecorder()
        mProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mToggleButton = findViewById<View>(R.id.toggle) as ToggleButton
        mToggleButton!!.setOnClickListener { v ->
            serviceIntent!!.putExtra("inputExtra", "Screen Recording in Progress")
            ContextCompat.startForegroundService(saveContext, serviceIntent!!)
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) + ContextCompat
                    .checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    )
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this@MainActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    )
                ) {
                    mToggleButton!!.isChecked = false
                    Snackbar.make(
                        findViewById(android.R.id.content), R.string.label_permissions,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(
                        "ENABLE"
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO
                            ),
                            REQUEST_PERMISSIONS
                        )
                    }.show()
                } else {
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO
                        ),
                        REQUEST_PERMISSIONS
                    )
                }
            } else {
                onToggleScreenShare(v)
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: $requestCode")
            return
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(
                this,
                "Screen Cast Permission Denied", Toast.LENGTH_SHORT
            ).show()
            mToggleButton!!.isChecked = false
            return
        }
        mMediaProjectionCallback = MediaProjectionCallback()
        mMediaProjection = mProjectionManager!!.getMediaProjection(resultCode, data!!)
        with(mMediaProjection) { this?.registerCallback(mMediaProjectionCallback, null) }
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder!!.start()
    }

    fun onToggleScreenShare(view: View?) {
        if ((view as ToggleButton?)!!.isChecked) {
            val date = Date()
            val dateFormat = SimpleDateFormat("YYYY-MM-dd-hh-mm-ss-Ms-z")
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            val fileName = "/" + dateFormat.format(date) + ".mp4"
            initRecorder()
            shareScreen()
        } else {
            stopService(serviceIntent)
            mMediaRecorder!!.stop()
            mMediaRecorder!!.reset()
            Log.v(TAG, "Stopping Recording")
            stopScreenSharing()
        }
    }

    private fun shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE)
            return
        }
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder!!.start()
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        return mMediaProjection!!.createVirtualDisplay(
            "MainActivity",
            DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mMediaRecorder!!.surface, null /*Callbacks*/, null /*Handler*/
        )
    }

    private fun initRecorder() {
        try {
            mMediaRecorder!!.reset()
            val date = Date()
            val dateFormat = SimpleDateFormat("YYYY-MM-dd-hh-mm-ss-Ms")
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            val fileName = "/" + dateFormat.format(date) + ".mp4"
            Log.d("DEBUG", fileName)
            mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mMediaRecorder!!.setOutputFile(
                Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .toString() + fileName
            )
            mMediaRecorder!!.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mMediaRecorder!!.setVideoEncodingBitRate(512 * 1000)
            mMediaRecorder!!.setVideoFrameRate(30)
            val rotation = windowManager.defaultDisplay.rotation
            val orientation = ORIENTATIONS[rotation + 90]
            mMediaRecorder!!.setOrientationHint(orientation)
            mMediaRecorder!!.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            if (mToggleButton!!.isChecked) {
                mToggleButton!!.isChecked = false
                mMediaRecorder!!.stop()
                mMediaRecorder!!.reset()
                Log.v(TAG, "Recording Stopped")
            }
            mMediaProjection = null
            stopScreenSharing()
        }
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return
        }
        stopService(serviceIntent)
        mVirtualDisplay!!.release()
        destroyMediaProjection()
    }

    public override fun onDestroy() {
        super.onDestroy()
        destroyMediaProjection()
    }

    private fun destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection!!.unregisterCallback(mMediaProjectionCallback)
            mMediaProjection!!.stop()
            mMediaProjection = null
        }
        Log.i(TAG, "MediaProjection Stopped")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (grantResults.size > 0 && grantResults[0] +
                    grantResults[1] == PackageManager.PERMISSION_GRANTED
                ) {
                    onToggleScreenShare(mToggleButton)
                } else {
                    mToggleButton!!.isChecked = false
                    Snackbar.make(
                        findViewById(android.R.id.content), R.string.label_permissions,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(
                        "ENABLE"
                    ) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.addCategory(Intent.CATEGORY_DEFAULT)
                        intent.data = Uri.parse("package:$packageName")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        startActivity(intent)
                    }.show()
                }
                return
            }
        }
    }
}