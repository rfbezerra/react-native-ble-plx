package br.com.vertitecnologia.vmpay.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtil {
    public static File createDirectory(@NonNull File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Cannot create directory " + dir);
        }
        return dir;
    }

    @NonNull
    public static synchronized File prepareDirectory(@NonNull File parent, @NonNull String name) {
        File result = new File(parent, name);

        if (result.exists() && !result.isDirectory()) {
            if (!result.delete())
                throw new RuntimeException("Cannot delete file " + result);
        }

        return createDirectory(result);
    }

    public static synchronized void deleteDirectory(@NonNull File file) {
        deleteDirectoryRecursively(file);
    }

    @NonNull
    public static File getVMboxDirectory(@NonNull File root) {
        return FileUtil.prepareDirectory(root, "vmbox");
    }

    public static void copyStream(@NonNull InputStream is, @NonNull OutputStream os) throws IOException {
        while (true) {
            byte[] buffer = new byte[1024];
            int sz = is.read(buffer, 0, buffer.length);
            if (sz < 0)
                break;
            os.write(buffer, 0, sz);
        }
    }

    @NonNull
    public static byte[] readBytes(@NonNull File file) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            InputStream is = new FileInputStream(file);
            try {
                copyStream(is, bos);
            } finally {
                is.close();
            }
            return bos.toByteArray();
        } finally {
            bos.close();
        }
    }

    public static byte[] readBytes(String path) throws IOException {
        return FileUtil.readBytes(new File(path));
    }

    public static byte[] readBytesFromPackageUpdateZip(String path) throws IOException {
        byte[] zip = readBytes(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ByteArrayInputStream bis = new ByteArrayInputStream(zip);
        ZipInputStream is = new ZipInputStream(bis);

        while (true) {
            ZipEntry entry = is.getNextEntry();
            if (entry == null)
                return null;
            if (!entry.getName().endsWith(".bin"))
                continue;

            copyStream(is, bos);
            return bos.toByteArray();
        }
    }

    @NonNull
    public static String readString(@NonNull File file, @NonNull Charset charset)
            throws IOException {
        return new String(readBytes(file), charset);
    }

    public static void writeBytes(@NonNull File file, @NonNull byte[] value) throws IOException {
        OutputStream os = new FileOutputStream(file);
        try {
            os.write(value);
        } finally {
            os.close();
        }
    }

    public static void writeString(@NonNull File file, @NonNull Charset charset,
                                   @NonNull String value) throws IOException {
        writeBytes(file, value.getBytes(charset));
    }

    @Nullable
    public static File findFileBySuffix(@NonNull File dir, @NonNull String suffix) {
        for (File f : dir.listFiles()) {
            String s = f.getName();
            int pos = s.indexOf(suffix);
            if (pos >= 0)
                return f;
        }
        return null;
    }

    @NonNull
    public static String extractFilePrefix(@NonNull File file) {
        String s = file.getName();
        int pos = s.lastIndexOf('.');
        if (pos >= 0)
            return s.substring(0, pos);
        return s;
    }

    private static void deleteDirectoryRecursively(@NonNull File file)
    {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children)
                    deleteDirectoryRecursively(child);
            }
        }

        if (file.exists() && !file.delete())
            throw new RuntimeException("Cannot delete " + file);
    }
}
