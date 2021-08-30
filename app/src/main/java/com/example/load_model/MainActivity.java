package com.example.load_model;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.SNPE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {


    TextView textView1;
    TextView textView2;
    TextView textView3;
    Button button1;
    Button button2;
    ImageView imageView;
    private static final String GROUND_TRUTH_FILE_NAME = "ground truths.txt";
    private static final String MODEL_DLC_FILE_NAME = "model.dlc";
   // private static final String MODEL_DLC_FILE_NAME = "repvgg_a2.dlc";
    private static final String LABELS_FILE_NAME = "labels.txt";
    private static final String LAYERS_FILE_NAME = "layers.txt";
    private static final String IMAGES_FOLDER_NAME = "images";
    private static final boolean MNETSSD_NEEDS_CPU_FALLBACK = true;

    final Model[] model = new Model[1];
    private NeuralNetwork mNeuralNetwork;
    private static final int TOP_K = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView1 = findViewById(R.id.textView_1);
        textView2 = findViewById(R.id.textView_2);
        textView3 = findViewById(R.id.textView_3);
        button1 = findViewById(R.id.btnReadText);
        button2 = findViewById(R.id.move_to_fragment);
        imageView = findViewById(R.id.imageView);
        button2.setVisibility(View.INVISIBLE);
        button1.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {
                button2.setVisibility(View.VISIBLE);

                model[0] = loadModel();
                textView1.setText(model[0].name);
                textView2.setText(model[0].inputLayer);
                textView3.setText(model[0].outputLayer);

                int random_int = (int) Math.floor(Math.random() * (model[0].jpgImages.size()));
                InputStream inputstream = null;
                try {
                    inputstream = getAssets().open("images/"
                            + model[0].jpgImages.get(random_int));
                    Drawable drawable = Drawable.createFromStream(inputstream, null);
                    imageView.setImageDrawable(drawable);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        disposeNeuralNetwork();
        new Thread() {
            public void run() {
                try {
                    createModel(model[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private Model loadModel() {

        final Model model = new Model();
        model.name = MODEL_DLC_FILE_NAME;

        String[] images = new String[0];
        try {
            images = getAssets().list(IMAGES_FOLDER_NAME);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        ArrayList<String> listImages = new ArrayList<String>(Arrays.asList(images));

        model.jpgImages = listImages;

        try {
            InputStream assetInputStream = getAssets().open(MODEL_DLC_FILE_NAME);
            model.file = assetInputStream;
        } catch (IOException e) {
            e.printStackTrace();
        }

        model.labels = loadData(LABELS_FILE_NAME);
        model.groundTruths = loadData(GROUND_TRUTH_FILE_NAME);
        try {
            String[] layers = loadLayers(LAYERS_FILE_NAME);
            model.inputLayer = layers[0];
            model.outputLayer = layers[1];
            model.mean = layers[2];
            model.isMeanImage = model.mean.equals("mean");
          return model;
        } catch (IOException e) {
            e.printStackTrace();
        }

     return model;
    }
    private String[] loadData(String file_name) {
        BufferedReader reader = null;
        final List<String> list = new LinkedList<>();
        try {
            reader = new BufferedReader(
                    new InputStreamReader(getAssets().open(file_name), "UTF-8"));

            String mLine;
            while ((mLine = reader.readLine()) != null) {
                list.add(mLine);
            }
        } catch (IOException e) {
            //log the exception
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //log the exception
                }
            }
        }
        return list.toArray(new String[list.size()]);
    }
    private String[] loadLayers(String file_name) throws IOException {
        final List<String> list = new ArrayList<>();
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getAssets().open(file_name), "UTF-8"));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            list.add(line);
        }

        if (list.size() != 3) {
            throw new IOException();
        } else {
            return list.toArray(new String[list.size()]);
        }
    }
    private void createModel(Model model) throws IOException {
        mNeuralNetwork = loadNetWork(model,NeuralNetwork.Runtime.GPU, MNETSSD_NEEDS_CPU_FALLBACK);
        NeuralNetwork.Runtime selectedCore = NeuralNetwork.Runtime.GPU_FLOAT16;

        // load the network
        try {
            mNeuralNetwork = loadNetWork(model,selectedCore, MNETSSD_NEEDS_CPU_FALLBACK);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // if it didn't work, retry on CPU
        if (mNeuralNetwork == null) {
            Log.d("Check", "Error loading the DLC network on the " + selectedCore + " core. Retrying on CPU.");
            try {
                mNeuralNetwork = loadNetWork(model, NeuralNetwork.Runtime.CPU, MNETSSD_NEEDS_CPU_FALLBACK);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mNeuralNetwork == null) {
            Log.d("Check","Error loading the DLC network on the " + selectedCore + " core. Retrying on CPU.");
                try {
                    mNeuralNetwork = loadNetWork(model,NeuralNetwork.Runtime.DSP, MNETSSD_NEEDS_CPU_FALLBACK);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }
    private NeuralNetwork loadNetWork(Model model, NeuralNetwork.Runtime selectedRuntime, boolean needsCpuFallback) throws IOException {
        NeuralNetwork network;
        InputStream assetInputStream = getAssets().open(MODEL_DLC_FILE_NAME);
        network = new SNPE.NeuralNetworkBuilder(getApplication())
                .setDebugEnabled(false)
              // .setOutputLayers(model.outputLayer)
                .setModel(assetInputStream, assetInputStream.available())
                .setPerformanceProfile(NeuralNetwork.PerformanceProfile.DEFAULT)
                .setRuntimeOrder(selectedRuntime) // Runtime.DSP, Runtime.GPU_FLOAT16, Runtime.GPU, Runtime.CPU
                .setCpuFallbackEnabled(needsCpuFallback)
                .build();

        return network;
    }
    public void disposeNeuralNetwork() {
        if (mNeuralNetwork == null)
            return;
        mNeuralNetwork.release();
        mNeuralNetwork = null;

    }
    private void classify_task(Model model, NeuralNetwork neural, Bitmap image) {
        final long start = System.currentTimeMillis();

        final List<String> result = new LinkedList<>();

        final FloatTensor tensor = mNeuralNetwork.createFloatTensor(mNeuralNetwork.getInputTensorsShapes().get(model.inputLayer));

        final int[] dimensions = tensor.getShape();

        final boolean isGrayScale = (dimensions[dimensions.length - 1] == 1);
        if (!isGrayScale) {
            writeRgbBitmapAsFloat(image, tensor);
        } else {
            writeGrayScaleBitmapAsFloat(image, tensor);
        }

        final Map<String, FloatTensor> inputs = new HashMap<>();
        inputs.put(model.inputLayer, tensor);

        final Map<String, FloatTensor> outputs = mNeuralNetwork.execute(inputs);

        for (Map.Entry<String, FloatTensor> output : outputs.entrySet()) {
            if (output.getKey().equals(model.outputLayer)) {
                for (Pair<Integer, Float> pair : topK(TOP_K, output.getValue())) {
                    result.add(model.labels[pair.first]);
                    result.add(String.valueOf(pair.second));
                }
            }
        }

        final long end = System.currentTimeMillis();
        long classifyTime = end - start;
        Log.d("Check",  mNeuralNetwork.getRuntime() + ": " + classifyTime);

    }
    private void writeRgbBitmapAsFloat(Bitmap image, FloatTensor tensor) {

        final int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int rgb = pixels[y * image.getWidth() + x];

                float[] pixelFloats = parseImage(rgb,model[0].isMeanImage);

                tensor.write(pixelFloats, 0, pixelFloats.length, y, x);
            }
        }
    }
    private float[] parseImage(int rgb, boolean isMean) {
        if (isMean) {
            float b = (((rgb) & 0xFF) - MeanImage.MEAN);
            float g = (((rgb >> 8) & 0xFF) - MeanImage.MEAN);
            float r = (((rgb >> 16) & 0xFF) - MeanImage.MEAN);
            return new float[]{b, g, r};
        } else {
            float imageStd = 128.0f;
            float b = (((rgb) & 0xFF) - MeanImage.MEAN_B) / imageStd;
            float g = (((rgb >> 8) & 0xFF) - MeanImage.MEAN_G) / imageStd;
            float r = (((rgb >> 16) & 0xFF) - MeanImage.MEAN_R) / imageStd;
            return new float[]{b, g, r};
        }
    }
    private void writeGrayScaleBitmapAsFloat(Bitmap image, FloatTensor tensor) {
        int imageMean = 128;
        final int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int rgb = pixels[y * image.getWidth() + x];
                final float b = ((rgb) & 0xFF);
                final float g = ((rgb >> 8) & 0xFF);
                final float r = ((rgb >> 16) & 0xFF);
                float grayscale = (float) (r * 0.3 + g * 0.59 + b * 0.11);
                grayscale -= imageMean;
                tensor.write(grayscale, y, x);
            }
        }
    }
    private Pair<Integer, Float>[] topK(int k, FloatTensor tensor) {
        final float[] array = new float[tensor.getSize()];
        tensor.read(array, 0, array.length);

        final boolean[] selected = new boolean[tensor.getSize()];
        final Pair<Integer, Float>[] topK = new Pair[k];
        int count = 0;
        while (count < k) {
            final int index = top(array, selected);
            selected[index] = true;
            topK[count] = new Pair<>(index, array[index]);
            count++;
        }
        return topK;
    }
    private int top(float[] array, boolean[] selected) {
        int index = 0;
        float max = -1.f;
        for (int i = 0; i < array.length; i++) {
            if (selected[i]) {
                continue;
            }
            if (array[i] > max) {
                max = array[i];
                index = i;
            }
        }
        return index;
    }

}

