package com.example.load_model;


import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

public class Model {

    public InputStream file;
    public String name;
    public ArrayList<String> jpgImages;

    public String[] labels;
    public String[] groundTruths;

    public String inputLayer;
    public String outputLayer;

    public String mean;
    public boolean isMeanImage;


}



