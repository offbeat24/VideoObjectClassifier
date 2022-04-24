package com.example.sunflower;

import static org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod.NEAREST_NEIGHBOR;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;

import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassifierWithModel {
    private static final String MODEL_NAME = "mobilenet_imagenet_model.tflite";
    private static final String LABEL_FILE = "labels.txt";

    Context context;
    Model model;
    int modelInputWidth, modelInputHeight, modelInputChannel;
    TensorImage inputImage;
    TensorBuffer outputBuffer;
    private List<String> labels;

    public ClassifierWithModel(Context context) {
        this.context = context;
    }

    public void init() throws IOException {
        model = Model.createModel(context, MODEL_NAME);

        initModelShape();
        labels = FileUtil.loadLabels(context, LABEL_FILE);
    }

    private void initModelShape() {
        Tensor inputTensor = model.getInputTensor(0);
        int[] shape = inputTensor.shape();
        modelInputChannel = shape[0];
        modelInputWidth = shape[1];
        modelInputHeight = shape[2];

        inputImage = new TensorImage(inputTensor.dataType());

        Tensor outputTensor = model.getOutputTensor(0);
        outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType());
    }



    private Pair<String, Float> argmax(Map<String, Float> map) {
        String maxKey = "";
        float maxVal = -1;
        for (Map.Entry<String, Float> entry : map.entrySet()) {
            float f = entry.getValue();
            if (f > maxVal) {
                maxKey = entry.getKey();
                maxVal = f;
            }
        }
        return new Pair<>(maxKey, maxVal);
    }

    private Bitmap convertBitmapToARGB8888(Bitmap bitmap) {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true);
    }

    private TensorImage loadImage(final Bitmap bitmap) {
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            inputImage.load(convertBitmapToARGB8888(bitmap));
        } else {
            inputImage.load(bitmap);
        }

        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(modelInputWidth, modelInputHeight, NEAREST_NEIGHBOR))
                        .add(new NormalizeOp(0.0f, 255.0f))
                        .build();
        return imageProcessor.process(inputImage);
    }


    public Pair<String, Float> classify(Bitmap image) {
        inputImage = loadImage(image);

        Object[] inputs = new Object[]{inputImage.getBuffer()};
        Map<Integer, Object> outputs = new HashMap();
        outputs.put(0,outputBuffer.getBuffer().rewind());

        model.run(inputs, outputs);

        Map<String, Float> output =
                new TensorLabel(labels, outputBuffer).getMapWithFloatValue();

        return argmax(output);
    }

    public void finish() {
        if (model != null)
            model.close();
    }
}
