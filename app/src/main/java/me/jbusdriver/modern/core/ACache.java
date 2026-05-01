package me.jbusdriver.modern.core;

/**
 * Android 磁盘缓存实现，支持 String/JSON/Bitmap/Serializable 等多种数据类型。
 *
 * 使用场景：CacheLoader 的二级缓存（磁盘层），用于持久化缓存数据，应用重启后仍有效。
 * 通过时间戳机制支持缓存过期自动清理。
 *
 * 线程：内部使用 ConcurrentHashMap 保证线程安全。文件 I/O 操作应避免在主线程执行。
 *
 * 来源：基于 Michael Yang 杨福海的 ACache 开源库（Apache 2.0）
 *
 * Copyright (c) 2012-2013, Michael Yang 杨福海 (www.yangfuhai.com).
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ACache {
    public static final int TIME_HOUR = 60 * 60;
    public static final int TIME_DAY = TIME_HOUR * 24;
    public static final int MAX_SIZE = 1000 * 1000 * 99;
    public static final int MAX_COUNT = Integer.MAX_VALUE;
    private static Map<String, ACache> mInstanceMap = new HashMap<String, ACache>();
    private ACacheManager mCache;

    public static ACache get(Context ctx) {
        return get(ctx, "ACache");
    }

    public static ACache get(Context ctx, String cacheName) {
        File f = new File(ctx.getCacheDir(), cacheName);
        return get(f, MAX_SIZE, MAX_COUNT);
    }

    public static ACache get(Context ctx, String cacheName, long max_zise, int max_count) {
        File f = new File(ctx.getCacheDir(), cacheName);
        return get(f, max_zise, max_count);
    }

    public static ACache get(File cacheDir) {
        return get(cacheDir, MAX_SIZE, MAX_COUNT);
    }

    public static ACache get(Context ctx, long max_zise, int max_count) {
        File f = new File(ctx.getCacheDir(), "ACache");
        return get(f, max_zise, max_count);
    }

    public static ACache get(File cacheDir, long max_zise, int max_count) {
        ACache manager = mInstanceMap.get(cacheDir.getAbsoluteFile() + myPid());
        if (manager == null) {
            manager = new ACache(cacheDir, max_zise, max_count);
            mInstanceMap.put(cacheDir.getAbsolutePath() + myPid(), manager);
        }
        return manager;
    }

    private static String myPid() {
        return "_" + android.os.Process.myPid();
    }

    private ACache(File cacheDir, long max_size, int max_count) {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new RuntimeException("can't make dirs in " + cacheDir.getAbsolutePath());
        }
        mCache = new ACacheManager(cacheDir, max_size, max_count);
    }

    public void put(String key, String value) {
        File file = mCache.newFile(key);
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(file), 1024);
            out.write(value);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mCache.put(file);
        }
    }

    public void put(String key, String value, int saveTime) {
        put(key, Utils.newStringWithDateInfo(saveTime, value));
    }

    public String getAsString(String key) {
        File file = mCache.get(key);
        if (!file.exists()) return null;
        boolean removeFile = false;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            String readString = "";
            String currentLine;
            while ((currentLine = in.readLine()) != null) {
                readString += currentLine;
            }
            if (!Utils.isDue(readString)) {
                return Utils.clearDateInfo(readString);
            } else {
                removeFile = true;
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException e) { e.printStackTrace(); }
            }
            if (removeFile) remove(key);
        }
    }

    public void put(String key, JSONObject value) { put(key, value.toString()); }
    public void put(String key, JSONObject value, int saveTime) { put(key, value.toString(), saveTime); }
    public JSONObject getAsJSONObject(String key) {
        String JSONString = getAsString(key);
        if (!TextUtils.isEmpty(JSONString)) {
            try { return new JSONObject(JSONString); } catch (Exception e) { e.printStackTrace(); }
        }
        return null;
    }

    public void put(String key, JSONArray value) { put(key, value.toString()); }
    public void put(String key, JSONArray value, int saveTime) { put(key, value.toString(), saveTime); }
    public JSONArray getAsJSONArray(String key) {
        String JSONString = getAsString(key);
        try { return new JSONArray(JSONString); } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public void put(String key, byte[] value) {
        File file = mCache.newFile(key);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(value);
        } catch (Exception e) { e.printStackTrace(); }
        finally {
            if (out != null) {
                try { out.flush(); out.close(); } catch (IOException e) { e.printStackTrace(); }
            }
            mCache.put(file);
        }
    }

    public OutputStream put(String key) throws FileNotFoundException {
        return new xFileOutputStream(mCache.newFile(key));
    }

    public InputStream get(String key) throws FileNotFoundException {
        File file = mCache.get(key);
        if (!file.exists()) return null;
        return new FileInputStream(file);
    }

    public void put(String key, byte[] value, int saveTime) {
        put(key, Utils.newByteArrayWithDateInfo(saveTime, value));
    }

    public byte[] getAsBinary(String key) {
        RandomAccessFile RAFile = null;
        boolean removeFile = false;
        try {
            File file = mCache.get(key);
            if (!file.exists()) return null;
            RAFile = new RandomAccessFile(file, "r");
            byte[] byteArray = new byte[(int) RAFile.length()];
            RAFile.read(byteArray);
            if (!Utils.isDue(byteArray)) {
                return Utils.clearDateInfo(byteArray);
            } else {
                removeFile = true;
                return null;
            }
        } catch (Exception e) { e.printStackTrace(); return null; }
        finally {
            if (RAFile != null) { try { RAFile.close(); } catch (IOException e) { e.printStackTrace(); } }
            if (removeFile) remove(key);
        }
    }

    public void put(String key, Serializable value) { put(key, value, -1); }
    public void put(String key, Serializable value, int saveTime) {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(value);
            byte[] data = baos.toByteArray();
            if (saveTime != -1) put(key, data, saveTime);
            else put(key, data);
        } catch (Exception e) { e.printStackTrace(); }
        finally { try { if (oos != null) oos.close(); } catch (IOException e) {} }
    }

    public Object getAsObject(String key) {
        byte[] data = getAsBinary(key);
        if (data != null) {
            ByteArrayInputStream bais = null;
            ObjectInputStream ois = null;
            try {
                bais = new ByteArrayInputStream(data);
                ois = new ObjectInputStream(bais);
                return ois.readObject();
            } catch (Exception e) { e.printStackTrace(); }
            finally {
                try { if (bais != null) bais.close(); } catch (IOException e) {}
                try { if (ois != null) ois.close(); } catch (IOException e) {}
            }
        }
        return null;
    }

    public void put(String key, Bitmap value) { put(key, Utils.Bitmap2Bytes(value)); }
    public void put(String key, Bitmap value, int saveTime) { put(key, Utils.Bitmap2Bytes(value), saveTime); }
    public Bitmap getAsBitmap(String key) {
        if (getAsBinary(key) == null) return null;
        return Utils.Bytes2Bimap(getAsBinary(key));
    }

    public void put(String key, Drawable value) { put(key, Utils.drawable2Bitmap(value)); }
    public void put(String key, Drawable value, int saveTime) { put(key, Utils.drawable2Bitmap(value), saveTime); }
    public Drawable getAsDrawable(String key) {
        if (getAsBinary(key) == null) return null;
        return Utils.bitmap2Drawable(Utils.Bytes2Bimap(getAsBinary(key)));
    }

    public File file(String key) {
        File f = mCache.newFile(key);
        if (f.exists()) return f;
        return null;
    }

    public boolean remove(String key) { return mCache.remove(key); }
    public void clear() { mCache.clear(); }
    public File dir() { return mCache.cacheDir; }

    class xFileOutputStream extends FileOutputStream {
        File file;
        public xFileOutputStream(File file) throws FileNotFoundException { super(file); this.file = file; }
        public void close() throws IOException { super.close(); mCache.put(file); }
    }

    public class ACacheManager {
        private final AtomicLong cacheSize;
        private final AtomicInteger cacheCount;
        private final long sizeLimit;
        private final int countLimit;
        private final Map<File, Long> lastUsageDates = new ConcurrentHashMap<>();
        protected File cacheDir;

        private ACacheManager(File cacheDir, long sizeLimit, int countLimit) {
            this.cacheDir = cacheDir;
            this.sizeLimit = sizeLimit;
            this.countLimit = countLimit;
            cacheSize = new AtomicLong();
            cacheCount = new AtomicInteger();
            calculateCacheSizeAndCacheCount();
        }

        private void calculateCacheSizeAndCacheCount() {
            new Thread(() -> {
                int size = 0;
                int count = 0;
                File[] cachedFiles = cacheDir.listFiles();
                if (cachedFiles != null) {
                    for (File cachedFile : cachedFiles) {
                        size += calculateSize(cachedFile);
                        count += 1;
                        lastUsageDates.put(cachedFile, cachedFile.lastModified());
                    }
                    cacheSize.set(size);
                    cacheCount.set(count);
                }
            }).start();
        }

        private void put(File file) {
            int curCacheCount = cacheCount.get();
            while (curCacheCount + 1 > countLimit) {
                cacheSize.addAndGet(-removeNext());
                curCacheCount = cacheCount.addAndGet(-1);
            }
            cacheCount.addAndGet(1);
            long valueSize = calculateSize(file);
            long curCacheSize = cacheSize.get();
            while (curCacheSize + valueSize > sizeLimit) {
                curCacheSize = cacheSize.addAndGet(-removeNext());
            }
            cacheSize.addAndGet(valueSize);
            Long currentTime = System.currentTimeMillis();
            file.setLastModified(currentTime);
            lastUsageDates.put(file, currentTime);
        }

        private File get(String key) {
            File file = newFile(key);
            Long currentTime = System.currentTimeMillis();
            file.setLastModified(currentTime);
            lastUsageDates.put(file, currentTime);
            return file;
        }

        private File newFile(String key) { return new File(cacheDir, key.hashCode() + ""); }
        private boolean remove(String key) { return get(key).delete(); }

        private void clear() {
            lastUsageDates.clear();
            cacheSize.set(0);
            File[] files = cacheDir.listFiles();
            if (files != null) { for (File f : files) f.delete(); }
        }

        private long removeNext() {
            if (lastUsageDates.isEmpty()) return 0;
            Long oldestUsage = null;
            File mostLongUsedFile = null;
            synchronized (lastUsageDates) {
                for (Map.Entry<File, Long> entry : lastUsageDates.entrySet()) {
                    if (mostLongUsedFile == null) {
                        mostLongUsedFile = entry.getKey();
                        oldestUsage = entry.getValue();
                    } else if (entry.getValue() < oldestUsage) {
                        oldestUsage = entry.getValue();
                        mostLongUsedFile = entry.getKey();
                    }
                }
            }
            long fileSize = calculateSize(mostLongUsedFile);
            if (mostLongUsedFile.delete()) lastUsageDates.remove(mostLongUsedFile);
            return fileSize;
        }

        private long calculateSize(File file) { return file.length(); }
    }

    public static class Utils {
        public static boolean isDue(String str) { return isDue(str.getBytes()); }
        public static boolean isDue(byte[] data) {
            String[] strs = getDateInfoFromDate(data);
            if (strs != null && strs.length == 2) {
                String saveTimeStr = strs[0];
                while (saveTimeStr.startsWith("0")) saveTimeStr = saveTimeStr.substring(1);
                long saveTime = Long.valueOf(saveTimeStr);
                long deleteAfter = Long.valueOf(strs[1]);
                if (System.currentTimeMillis() > saveTime + deleteAfter * 1000) return true;
            }
            return false;
        }

        private static String newStringWithDateInfo(int second, String strInfo) { return createDateInfo(second) + strInfo; }
        private static byte[] newByteArrayWithDateInfo(int second, byte[] data2) {
            byte[] data1 = createDateInfo(second).getBytes();
            byte[] retdata = new byte[data1.length + data2.length];
            System.arraycopy(data1, 0, retdata, 0, data1.length);
            System.arraycopy(data2, 0, retdata, data1.length, data2.length);
            return retdata;
        }

        private static String clearDateInfo(String strInfo) {
            if (strInfo != null && hasDateInfo(strInfo.getBytes())) {
                strInfo = strInfo.substring(strInfo.indexOf(mSeparator) + 1);
            }
            return strInfo;
        }

        private static byte[] clearDateInfo(byte[] data) {
            if (hasDateInfo(data)) return copyOfRange(data, indexOf(data, mSeparator) + 1, data.length);
            return data;
        }

        private static boolean hasDateInfo(byte[] data) {
            return data != null && data.length > 15 && data[13] == '-' && indexOf(data, mSeparator) > 14 && indexOf(data, mSeparator) < 27;
        }

        private static String[] getDateInfoFromDate(byte[] data) {
            if (hasDateInfo(data)) {
                String saveDate = new String(copyOfRange(data, 0, 13));
                String deleteAfter = new String(copyOfRange(data, 14, indexOf(data, mSeparator)));
                return new String[]{saveDate, deleteAfter};
            }
            return null;
        }

        private static int indexOf(byte[] data, char c) {
            for (int i = 0; i < data.length; i++) if (data[i] == c) return i;
            return -1;
        }

        private static byte[] copyOfRange(byte[] original, int from, int to) {
            int newLength = to - from;
            if (newLength < 0) throw new IllegalArgumentException(from + " > " + to);
            byte[] copy = new byte[newLength];
            System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
            return copy;
        }

        private static final char mSeparator = ' ';
        private static String createDateInfo(int second) {
            String currentTime = System.currentTimeMillis() + "";
            while (currentTime.length() < 13) currentTime = "0" + currentTime;
            return currentTime + "-" + second + mSeparator;
        }

        private static byte[] Bitmap2Bytes(Bitmap bm) {
            if (bm == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return baos.toByteArray();
        }

        private static Bitmap Bytes2Bimap(byte[] b) {
            if (b.length == 0) return null;
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        }

        private static Bitmap drawable2Bitmap(Drawable drawable) {
            if (drawable == null) return null;
            int w = drawable.getIntrinsicWidth();
            int h = drawable.getIntrinsicHeight();
            Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            Bitmap bitmap = Bitmap.createBitmap(w, h, config);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, w, h);
            drawable.draw(canvas);
            return bitmap;
        }

        @SuppressWarnings("deprecation")
        private static Drawable bitmap2Drawable(Bitmap bm) {
            if (bm == null) return null;
            return new BitmapDrawable(bm);
        }
    }
}
