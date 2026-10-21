package com.example.hidecamera;

// AndroidX ko hata kar standard Core Android Activity use ki hai
import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class VideoGalleryActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Secure Folder ki videos dekhne ke liye layout yahan set hoga
    }

    // Is function se video secure app storage se nikal kar phone ki main gallery me save ho jayegi
    public void exportVideoToGallery(File privateVideoFile) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, privateVideoFile.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HiddenFolder");
        }

        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri videoUri = getContentResolver().insert(collection, values);

        if (videoUri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(videoUri);
                 FileInputStream in = new FileInputStream(privateVideoFile)) {
                
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                Toast.makeText(this, "Video successfully saved to Phone Gallery!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Export failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
