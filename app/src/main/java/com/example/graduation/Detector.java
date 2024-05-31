package com.example.graduation;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;

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

    public Detector(Context context, String modelPath, String labelPath, DetectorListener detectorListener) {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.detectorListener = detectorListener;

        // Initialize SharedPreferences
        sharedPreferences = context.getSharedPreferences("DetectorPrefs", Context.MODE_PRIVATE);
        folderIndex = sharedPreferences.getInt("folderIndex", 0);
    }

    public void setup(Context context) {
        try {
            // Load the model file into a byte array
            byte[] model = ModelLoader.loadModel(context, "best_float32.tflite");

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

    public void detect(Bitmap frame) {
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

        if (bestBoxes == null) {
            detectorListener.onEmptyDetect();
            return;
        }

        for (BoundingBox box : bestBoxes) {
            Bitmap objectBitmap = extractObject(frame, box);
            saveObjectImage(objectBitmap);
        }

        detectorListener.onDetect(bestBoxes, inferenceTime);
    }

    private Bitmap extractObject(Bitmap frame, BoundingBox box) {
        int left = (int) (box.x1 * frame.getWidth());
        int top = (int) (box.y1 * frame.getHeight());
        int right = (int) (box.x2 * frame.getWidth());
        int bottom = (int) (box.y2 * frame.getHeight());

        return Bitmap.createBitmap(frame, left, top, right - left, bottom - top);
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
        // Increment and save the folder index for next run
        folderIndex++;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("folderIndex", folderIndex);
        editor.apply();
    }

    private void saveObjectImage(Bitmap bitmap) {
        File folder = new File(folderPath);
        if (folder.listFiles() != null && folder.listFiles().length >= 30) {
            // Stop saving images if the folder already has 30 images
            return;
        }
        try {
            String fileName = "object_" + System.currentTimeMillis() + ".png";
            File file = new File(folderPath, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            detectorListener.onObjectImageSaved(fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface DetectorListener {
        void onEmptyDetect();
        void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime);
        void onObjectImageSaved(String fileName);
    }

    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STANDARD_DEVIATION = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.3F;
    private static final float IOU_THRESHOLD = 0.5F;
}
