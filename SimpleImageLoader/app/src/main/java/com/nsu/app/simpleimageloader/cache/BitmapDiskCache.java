package com.nsu.app.simpleimageloader.cache;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import com.nsu.app.simpleimageloader.utils.ImageReSizer;
import com.nsu.app.simpleimageloader.utils.MyUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


import com.nsu.app.simpleimageloader.disklrucache.DiskLruCache;
import com.nsu.app.simpleimageloader.disklrucache.IOUtil;

/**
 * Created by Anthony on 2016/3/10.
 * Class Note:
 */
public class BitmapDiskCache implements BitmapCache {
    /**
     * 1MB
     */
    private static final int MB = 1024 * 1024;
    private static final int DISK_CACHE_SIZE = 50 * MB;
    /**
     * cache dir
     */
    private static final String IMAGE_DISK_CACHE = "bitmap";
    private static final int IO_BUFFER_SIZE = 8 * 1024;
    private static final int DISK_CACHE_INDEX = 0;
    /**
     * Disk LRU Cache
     */
    public DiskLruCache mDiskLruCache;
    /**
     * Disk Cache Instance
     */
    private static BitmapDiskCache mBitmapDiskCache;

    public boolean mIsDiskLruCacheCreated = false;
    private ImageReSizer mImageResizer;
    private int reqWidth;


    private int reqHeight;

    public BitmapDiskCache(Context context) {
        initDiskCache(context);
        mImageResizer = new ImageReSizer();
    }

    /**
     * initialize the diskCache
     *
     * @param context
     */
    private void initDiskCache(Context context) {
        try {
            File cacheDir = getDiskCacheDir(context, IMAGE_DISK_CACHE);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            if (MyUtils.getUsableSpace(cacheDir) >= DISK_CACHE_SIZE) {
                mDiskLruCache = DiskLruCache
                        .open(cacheDir, getAppVersion(context), 1, DISK_CACHE_SIZE);
                mIsDiskLruCacheCreated = true;
            } else {
                Log.d("BitmapDiskCache", "DiskLruCache not created!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * get cache directory
     *
     * @param context
     * @param name
     * @return
     */
    public File getDiskCacheDir(Context context, String name) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.d("", "### context : " + context + ", dir = " + context.getExternalCacheDir());
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + name);
    }

    /**
     * get app version name
     *
     * @param context
     * @return
     */
    private int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }


    @Override
    public Bitmap get(final String key) {
        Bitmap bitmap = null;
        DiskLruCache.Snapshot snapShot = null;
        try {
            snapShot = mDiskLruCache.get(key);
            if (snapShot != null) {
                FileInputStream fileInputStream = (FileInputStream) snapShot.getInputStream(DISK_CACHE_INDEX);
                FileDescriptor fileDescriptor = fileInputStream.getFD();
                bitmap = mImageResizer.decodeSampledBitmapFromFileDescriptor(fileDescriptor,
                        reqWidth, reqHeight);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private InputStream getInputStream(String md5) {
        DiskLruCache.Snapshot snapshot;
        try {
            snapshot = mDiskLruCache.get(md5);
            if (snapshot != null) {
                return snapshot.getInputStream(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void put(String key, Bitmap value) {
        DiskLruCache.Editor editor = null;
        try {
            // 如果没有找到对应的缓存，则准备从网络上请求数据，并写入缓存
            editor = mDiskLruCache.edit(key);
            if (editor != null) {
                OutputStream outputStream = editor.newOutputStream(0);
                if (writeBitmapToDisk(value, outputStream)) {
                    // 写入disk缓存
                    editor.commit();
                } else {
                    editor.abort();
                }
                IOUtil.closeQuietly(outputStream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private boolean writeBitmapToDisk(Bitmap bitmap, OutputStream outputStream) {
        BufferedOutputStream bos = new BufferedOutputStream(outputStream, 8 * 1024);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
        boolean result = true;
        try {
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        } finally {
            IOUtil.closeQuietly(bos);
        }

        return result;
    }
    @Override
    public void remove(String key) {
        try {
            mDiskLruCache.remove(MyUtils.toMD5(key));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void setReqHeight(int reqHeight) {
        this.reqHeight = reqHeight;
    }

    public void setReqWidth(int reqWidth) {
        this.reqWidth = reqWidth;
    }

    public int getReqHeight() {
        return reqHeight;
    }

    public int getReqWidth() {
        return reqWidth;
    }

}
