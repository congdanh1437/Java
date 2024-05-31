package com.example.graduation;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ObjectImageSaver {

    private static final String TAG = "ObjectImageSaver";
    private static final int IMAGES_PER_FOLDER = 30;

    public static void saveDetectedObject(Context context, Bitmap image) {
        int currentFolderIndex = getCurrentFolderIndex(context);
        int currentImageIndex = getCurrentImageIndex(context);

        if (currentImageIndex >= IMAGES_PER_FOLDER) {
            currentFolderIndex++;
            currentImageIndex = 0;
        }

        File directory = getDetectedObjectsDirectory(context, currentFolderIndex);

        if (!directory.exists()) {
            directory.mkdirs();
        }

        String fileName = "object_" + currentImageIndex + ".png";

        File file = new File(directory, fileName);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            updateIndexes(context, currentFolderIndex, currentImageIndex);
        } catch (IOException e) {
            Log.e(TAG, "Error saving image", e);
        }
    }

    private static File getDetectedObjectsDirectory(Context context, int folderIndex) {
        File externalStorageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(externalStorageDir, "detected_" + folderIndex);
    }

    private static int getCurrentFolderIndex(Context context) {
        return context.getSharedPreferences("FolderIndex", Context.MODE_PRIVATE).getInt("folderIndex", 0);
    }

    private static int getCurrentImageIndex(Context context) {
        return context.getSharedPreferences("ImageIndex", Context.MODE_PRIVATE).getInt("imageIndex", 0);
    }

    private static void updateIndexes(Context context, int folderIndex, int imageIndex) {
        context.getSharedPreferences("FolderIndex", Context.MODE_PRIVATE).edit().putInt("folderIndex", folderIndex).apply();
        context.getSharedPreferences("ImageIndex", Context.MODE_PRIVATE).edit().putInt("imageIndex", imageIndex + 1).apply();
    }

    public static String getCurrentFolderPath(Context context) {
        int currentFolderIndex = getCurrentFolderIndex(context);
        File directory = getDetectedObjectsDirectory(context, currentFolderIndex);
        return directory.getAbsolutePath();
    }
}
