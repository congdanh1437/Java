package com.example.graduation;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;

public class ModelLoader {
    public static byte[] loadModel(Context context, String modelPath) {
        AssetManager assetManager = context.getAssets();
        try {
            InputStream inputStream = assetManager.open(modelPath);
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
