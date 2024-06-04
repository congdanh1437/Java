package com.example.graduation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Detector {
    private static final String TAG = "Detector";

    private Activity activity;
    private Context context;
    private String modelPath;
    private String labelPath;
    private DetectorListener detectorListener;

    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();

    private int tensorWidth = 0;
    private int tensorHeight = 0;
    private int numChannel = 0;
    private int numElements = 0;

    private final ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
            .add(new CastOp(INPUT_IMAGE_TYPE))
            .build();

    private SharedPreferences sharedPreferences;
    private int folderIndex;
    private String folderPath;

    public Detector(Activity activity, String modelPath, String labelPath, DetectorListener detectorListener) {
        this.activity = activity;
        this.context = activity.getApplicationContext(); // Use application context to avoid memory leaks
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.detectorListener = detectorListener;

        // Initialize SharedPreferences
        sharedPreferences = this.context.getSharedPreferences("DetectorPrefs", Context.MODE_PRIVATE);
        folderIndex = sharedPreferences.getInt("folderIndex", 0);
    }

    public void setup(Context context) {
        try {
            // Load the model file into a byte array
            byte[] model = ModelLoader.loadModel(context, Constants.MODEL_PATH);

            // Create a ByteBuffer from the model byte array
            ByteBuffer modelBuffer = ByteBuffer.allocateDirect(model.length)
                    .order(ByteOrder.nativeOrder());
            modelBuffer.put(model);

            Interpreter.Options options = new Interpreter.Options();
            CompatibilityList compatList = new CompatibilityList();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
                options.addDelegate(new GpuDelegate(delegateOptions));
            }
            options.setNumThreads(4);

            // Initialize the interpreter with the model ByteBuffer
            interpreter = new Interpreter(modelBuffer, options);

            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            tensorWidth = inputShape[1];
            tensorHeight = inputShape[2];

            if (inputShape[1] == 3) {
                tensorWidth = inputShape[2];
                tensorHeight = inputShape[3];
            }

            numChannel = outputShape[1];
            numElements = outputShape[2];

            InputStream inputStream = context.getAssets().open(labelPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                labels.add(line);
            }

            reader.close();
            inputStream.close();

            // Create the folder for storing images
            createFolderIfNotExists();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clear() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    public String getLatestFolderPath() {
        return folderPath;
    }

    public void detectAndSaveObjects(Bitmap frame, String imageName, File parentFolder) {
        if (interpreter == null || tensorWidth == 0 || tensorHeight == 0 || numChannel == 0 || numElements == 0) {
            return;
        }

        long inferenceTime = SystemClock.uptimeMillis();

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false);

        TensorImage tensorImage = new TensorImage(INPUT_IMAGE_TYPE);
        tensorImage.load(resizedBitmap);
        TensorImage processedImage = imageProcessor.process(tensorImage);
        ByteBuffer imageBuffer = processedImage.getBuffer();

        TensorBuffer output = TensorBuffer.createFixedSize(new int[]{1, numChannel, numElements}, OUTPUT_IMAGE_TYPE);
        interpreter.run(imageBuffer, output.getBuffer());

        List<BoundingBox> bestBoxes = bestBox(output.getFloatArray());
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;

        if (bestBoxes == null || bestBoxes.isEmpty()) {
            detectorListener.onEmptyDetect();
            return;
        }

        List<Bitmap> objectBitmaps = new ArrayList<>();

        // Create a new folder based on the captured image file name
        String folderName = imageName.substring(0, imageName.lastIndexOf('.'));
        File newFolder = new File(parentFolder, folderName);
        if (!newFolder.exists()) {
            newFolder.mkdirs();
        }

        for (BoundingBox box : bestBoxes) {
            Bitmap objectBitmap = extractObject(frame, box);
            objectBitmaps.add(objectBitmap);
        }

        saveBitmapToFileWithDialog(frame, newFolder, objectBitmaps);

        detectorListener.onDetect(bestBoxes, inferenceTime);
    }

    private void saveBitmapToFileWithDialog(Bitmap bitmap, File folder, List<Bitmap> objectBitmaps) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Save Objects");
        builder.setMessage("Do you want to save all detected objects?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Save all detected objects
                for (int i = 0; i < objectBitmaps.size(); i++) {
                    Bitmap objectBitmap = objectBitmaps.get(i);
                    saveBitmapToFile(objectBitmap, folder, i + 1);
                }

                // Navigate to ImageDisplayActivity
                Intent intent = new Intent(activity, ImageDisplayActivity.class);
                intent.putExtra("folderPath", folder.getAbsolutePath());
                activity.startActivity(intent);
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Do nothing or handle decline action
            }
        });
        builder.show();
    }

    private Bitmap extractObject(Bitmap frame, BoundingBox box) {
        int left = (int) (box.x1 * frame.getWidth());
        int top = (int) (box.y1 * frame.getHeight());
        int right = (int) (box.x2 * frame.getWidth());
        int bottom = (int) (box.y2 * frame.getHeight());

        return Bitmap.createBitmap(frame, left, top, right - left, bottom - top);
    }

    private void saveBitmapToFile(Bitmap bitmap, File folder, int index) {
        String fileName = "object_" + index + ".png";
        File file = new File(folder, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Error saving bitmap to file: " + e.getMessage(), e);
        }
    }

    private List<BoundingBox> bestBox(float[] array) {
        List<BoundingBox> boundingBoxes = new ArrayList<>();

        for (int c = 0; c < numElements; c++) {
            float maxConf = -1.0f;
            int maxIdx = -1;
            int j = 4;
            int arrayIdx = c + numElements * j;
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx];
                    maxIdx = j - 4;
                }
                j++;
                arrayIdx += numElements;
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                String clsName = labels.get(maxIdx);
                float cx = array[c];
                float cy = array[c + numElements];
                float w = array[c + numElements * 2];
                float h = array[c + numElements * 3];
                float x1 = cx - (w / 2F);
                float y1 = cy - (h / 2F);
                float x2 = cx + (w / 2F);
                float y2 = cy + (h / 2F);
                if (x1 < 0F || x1 > 1F) continue;
                if (y1 < 0F || y1 > 1F) continue;
                if (x2 < 0F || x2 > 1F) continue;
                if (y2 < 0F || y2 > 1F) continue;
                boundingBoxes.add(new BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, clsName));
            }
        }

        if (boundingBoxes.isEmpty()) return null;

        return applyNMS(boundingBoxes);
    }

    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        List<BoundingBox> sortedBoxes = new ArrayList<>(boxes);
        sortedBoxes.sort((a, b) -> Float.compare(b.cnf, a.cnf));
        List<BoundingBox> selectedBoxes = new ArrayList<>();

        while (!sortedBoxes.isEmpty()) {
            BoundingBox first = sortedBoxes.get(0);
            selectedBoxes.add(first);
            sortedBoxes.remove(first);

            sortedBoxes.removeIf(nextBox -> calculateIoU(first, nextBox) >= IOU_THRESHOLD);
        }

        return selectedBoxes;
    }

    private float calculateIoU(BoundingBox box1, BoundingBox box2) {
        float x1 = Math.max(box1.x1, box2.x1);
        float y1 = Math.max(box1.y1, box2.y1);
        float x2 = Math.min(box1.x2, box2.x2);
        float y2 = Math.min(box1.y2, box2.y2);
        float intersectionArea = Math.max(0F, x2 - x1) * Math.max(0F, y2 - y1);
        float box1Area = box1.w * box1.h;
        float box2Area = box2.w * box2.h;
        return intersectionArea / (box1Area + box2Area - intersectionArea);
    }

    private void createFolderIfNotExists() {
        folderPath = context.getFilesDir().getAbsolutePath() + "/object_" + folderIndex;
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        // Increment and save the folder index for the next run
        folderIndex++;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("folderIndex", folderIndex);
        editor.apply();
    }

    public interface DetectorListener {
        void onEmptyDetect();
        void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime);
    }

    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STANDARD_DEVIATION = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.3F;
    private static final float IOU_THRESHOLD = 0.5F;
}
