package com.example.sunflower;


import android.graphics.Bitmap;
import android.util.Pair;
import android.util.Size;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Classifier {
    private boolean isInitialized = false;

    public void init() throws IOException {
        model = Model.createModel(context, MODEL_NAME);

        initModelShape();
        labels = FileUtil.loadLabels(context, LABEL_FILE);
        //labels.remove(0);

        isInitialized = true;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public Size getModelInputSize() {
        if(!isInitialized)
            return new Size(0,0);
        return new Size(modelInputWidth, modelInputHeight);
    }

    public void finish() {
        if(model != null) {
            model.close();
            isInitialized = false;
        }
    }

    private TensorImage loadImage(final Bitmap bitmap, int sensorOrientation) {
        if(bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            inputImage.load(convertBitmapToARGB8888(bitmap));
        } else {
            inputImage.load(bitmap);
        }

        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        int numRotation = sensorOrientation / 90;

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                .add(new ResizeOp(modelInputWidth, modelInputHeight, NEAREST_NEIGHBOR))
                .add(new Rot900p(numRotation))
                .add(new NormalizeOp(0.0f, 255.0f))
                .build();

        return imageProcessor.process(inputImage);
    }

    public Pair<String, Float> classify(Bitmap image, int sensorOrientation) {
        inputImage = loadImage(image, sensorOrientation);

        Object[] inputs = new Object[]{inputImage.getBuffer()};
        Map<Integer, Object> outputs = new HashMap();
        outputs.put(0, outputBuffer.getBuffer().rewind());

        model.run(inputs, outputs);

        Map<String, Float> output = new TensorLabel(labels, outputBuffer).getMapWithFloatValue();

        return argmax(output);
    }

    public Pair<String, Float> classify(Bitmap image) {
        return classify(image, 0);
    }

}
