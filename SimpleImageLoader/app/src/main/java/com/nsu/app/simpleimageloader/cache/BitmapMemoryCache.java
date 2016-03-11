package com.nsu.app.simpleimageloader.cache;

import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

/**
 * Created by Anthony on 2016/3/10.
 * Class Note:
 * the cache of the memory,key is the uri,value is the image
 */
public class BitmapMemoryCache implements BitmapCache {

    private LruCache<String, Bitmap> mMemoryCache;

    public BitmapMemoryCache() {
        //get the max memory
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // 1/4 of the max memory as the cache Size
        final int cacheSize = maxMemory / 4;
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {

            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };
    }

    @Override
    public Bitmap get(String key) {
        return mMemoryCache.get(key);
    }

    @Override
    public void put(String key, Bitmap value) {
        mMemoryCache.put(key, value);
    }

    @Override
    public void remove(String key) {
        mMemoryCache.remove(key);
    }
}
