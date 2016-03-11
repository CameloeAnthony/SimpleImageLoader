package com.nsu.app.simpleimageloader.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;

import com.nsu.app.simpleimageloader.R;
import com.nsu.app.simpleimageloader.cache.BitmapDiskCache;
import com.nsu.app.simpleimageloader.cache.BitmapMemoryCache;
import com.nsu.app.simpleimageloader.utils.ImageReSizer;
import com.nsu.app.simpleimageloader.utils.MyUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.nsu.app.simpleimageloader.disklrucache.DiskLruCache;
import com.nsu.app.simpleimageloader.disklrucache.IOUtil;

/**
 * Created by Anthony on 2016/3/11.
 * Class Note:ImageLoader
 */
public class ImageLoader {

    private static ImageLoader sInstance;

    private static final String TAG = "ImageLoader";

    public static final int MESSAGE_POST_RESULT = 1;

    private static final int CPU_COUNT = Runtime.getRuntime()
            .availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEP_ALIVE = 10L;
    private static final int TAG_KEY_URI = R.id.imageloader_uri;
    private static final int IO_BUFFER_SIZE = 8 * 1024;
    private static final int DISK_CACHE_INDEX = 0;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "ImageLoader#" + mCount.getAndIncrement());
        }
    };

    public static final Executor THREAD_POOL_EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
            KEEP_ALIVE, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), sThreadFactory);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LoaderResult result = (LoaderResult) msg.obj;
            ImageView imageView = result.imageView;
            String uri = (String) imageView.getTag(TAG_KEY_URI);
            if (uri.equals(result.uri)) {
                imageView.setImageBitmap(result.bitmap);
            } else {
                Log.w(TAG, "set image bitmap,but url has changed, ignored!");
            }
        }
    };

    private BitmapMemoryCache mBitmapMemCache;
    private BitmapDiskCache mBitmapDiskCache;

    private ImageLoader(Context context) {
        mBitmapMemCache = new BitmapMemoryCache();
        mBitmapDiskCache = new BitmapDiskCache(context);
    }


    /**
     * singleton instance
     */
    public static ImageLoader getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ImageLoader.class) {
                if (sInstance == null) {
                    sInstance = new ImageLoader(context);
                }
            }
        }
        return sInstance;
    }

//    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
//        if (mBitmapMemCache.get(key) == null) {
//            mBitmapMemCache.put(key, bitmap);
//        }
//    }

    private void addBitmapToDiskCache(String key, Bitmap bitmap) {
        if (mBitmapDiskCache.get(key) == null) {
            mBitmapDiskCache.put(key, bitmap);
        }
    }

    /**
     * load bitmap from memory cache or disk cache or network async,
     * then bind imageView and bitmap.
     * NOTE THAT: should run in UI Thread
     *
     * @param uri       http url
     * @param imageView bitmap's bind object
     */
    public void loadBitmap(final String uri, final ImageView imageView) {
        loadBitmap(uri, imageView, 0, 0);
    }

    public void loadBitmap(final String uri, final ImageView imageView,
                           final int reqWidth, final int reqHeight) {
        imageView.setTag(TAG_KEY_URI, uri);// TODO: 2016/3/11

        Runnable loadBitmapTask = new Runnable() {

            @Override
            public void run() {
                Bitmap bitmap = loadBitmap(uri, reqWidth, reqHeight);
                if (bitmap != null) {
                    LoaderResult result = new LoaderResult(imageView, uri, bitmap);
                    mMainHandler.obtainMessage(MESSAGE_POST_RESULT, result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(loadBitmapTask);
    }

    /**
     * load bitmap from memory cache or disk cache or network.
     *
     * @param uri       http url
     * @param reqWidth  the width ImageView desired
     * @param reqHeight the height ImageView desired
     * @return bitmap, maybe null.
     */
    private Bitmap loadBitmap(String uri, int reqWidth, int reqHeight) {
        String key = MyUtils.toMD5(uri);
        Bitmap bitmap = loadBitmapFromMemCache(key);
        if (bitmap != null) {
            Log.d(TAG, "1 in memory,loadBitmapFromMemCache,url:" + uri);
            return bitmap;
        }
        try {
            Log.d(TAG, "2.1 not in memory ,loadBitmapFromDisk,url:" + uri);
            bitmap = loadBitmapFromDiskCache(key);
            if (bitmap != null) {
                if (mBitmapMemCache.get(key) == null) {//not in memory cache
                    mBitmapMemCache.put(key, bitmap);
                    Log.d(TAG, "2.2 already loadBitmapFromDisk,then addBitmapToMemoryCache,url:" + uri);
                }
                return bitmap;
            }
            bitmap = downloadBitmapFromUrl(uri);
            Log.d(TAG, "3.1 not in memory,not in disk, downloadBitmapFromUrl,url:" + uri);
            if (bitmap != null) {
                if (mBitmapDiskCache.get(key) == null) {
                    mBitmapDiskCache.put(key, bitmap);
                    Log.d(TAG, "3.2 already downloadBitmapFromUrl,addBitmapToDiskCache url:" + uri);
                }
                if (mBitmapMemCache.get(key) == null) {
                    mBitmapMemCache.put(key, bitmap);
                    Log.d(TAG, "3.3 already downloadBitmapFromUrl,addBitmapToMemoryCache url:" + uri);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bitmap == null && !mBitmapDiskCache.mIsDiskLruCacheCreated) {
            Log.w(TAG, "4 encounter error, DiskLruCache is not created.");
            bitmap = downloadBitmapFromUrl(uri);
        }
        return bitmap;
    }


    private Bitmap loadBitmapFromMemCache(String key) {
        Bitmap bitmap = mBitmapMemCache.get(key);
        return bitmap;
    }

    private Bitmap loadBitmapFromDiskCache(String url) throws IOException {

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "load bitmap from UI Thread, it's not recommended!");
        }
        if (mBitmapDiskCache == null) {
            return null;
        }
        Bitmap bitmap = mBitmapDiskCache.get(url);
        return bitmap;
    }


    private Bitmap downloadBitmapFromUrl(String urlString) {
        Bitmap bitmap = null;
        HttpURLConnection urlConnection = null;
        BufferedInputStream in = null;

        try {
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(urlConnection.getInputStream(),
                    IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (final IOException e) {
            Log.e(TAG, "Error in downloadBitmap: " + e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            IOUtil.closeQuietly(in);
        }
        return bitmap;
    }


    private static class LoaderResult {
        public ImageView imageView;
        public String uri;
        public Bitmap bitmap;

        public LoaderResult(ImageView imageView, String uri, Bitmap bitmap) {
            this.imageView = imageView;
            this.uri = uri;
            this.bitmap = bitmap;
        }
    }
}
