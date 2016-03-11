package com.nsu.app.simpleimageloader.cache;

import android.graphics.Bitmap;


/**
 * Created by Anthony on 2016/3/10.
 * Class Note:
 */
public class BitmapNoCache implements BitmapCache {
    @Override
    public Bitmap get(String key) {
        return null;
    }

    @Override
    public void put(String key, Bitmap value) {

    }

    @Override
    public void remove(String key) {

    }
}
