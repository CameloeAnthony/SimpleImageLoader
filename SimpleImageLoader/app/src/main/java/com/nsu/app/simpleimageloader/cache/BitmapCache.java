package com.nsu.app.simpleimageloader.cache;

import android.graphics.Bitmap;



/**
 * Created by Anthony on 2016/3/10.
 * Class Note:
 * abstract class for images,
 * concrete class:NoCache,BitmapMemoryCache,BitmapDiskCache,DoubleCache
 */
public interface BitmapCache  {
    Bitmap get(String key);
    void put(String key,Bitmap value);
    void remove(String key);
}
