package br.com.vertitecnologia.vmpay.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import br.com.vertitecnologia.vmpay.vmbox.Writer;

public class PackageUtil {
    private final static int BLOCK_SIZE = 180;

    public static ArrayList<String> buildPackagesForFile(int type, String path, int pkg) throws IOException {
        ArrayList<String> packages = new ArrayList<>();
        byte[] fileData = FileUtil.readBytes(path);

        final int dataLength = fileData.length;
        final int packageSize = (int) Math.ceil((double) dataLength / BLOCK_SIZE);

        packages.add(firstPackage(type, pkg++));
        for (int i = 0; i < packageSize; i++) {
            int from = i * BLOCK_SIZE;
            int to = dataLength - from > BLOCK_SIZE ? from + BLOCK_SIZE : dataLength;

            byte[] packageData = Arrays.copyOfRange(fileData, from, to);
            packages.add(buildPackage(pkg++, (i + 1), packageData));
        }
        packages.add(lastPackage(pkg++));

        return packages;
    }

    @Nullable
    public static ArrayList<String> buildBlocksForZipFile(String path) throws IOException {
        ArrayList<String> blocks = new ArrayList<>();
        byte[] zip = FileUtil.readBytes(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ByteArrayInputStream bis = new ByteArrayInputStream(zip);
        ZipInputStream is = new ZipInputStream(bis);

        while (true) {
            ZipEntry entry = is.getNextEntry();
            if (entry == null) return null;
            if (!entry.getName().endsWith(".bin")) continue;

            FileUtil.copyStream(is, bos);
            break;
        }
        byte[] fileData = bos.toByteArray();

        final int dataLength = fileData.length;
        final int blockCount = fileData.length / BLOCK_SIZE;

        for (int i = 0; i <= blockCount; i++) {
            int from = i * BLOCK_SIZE;

            byte[] blockData = Arrays.copyOfRange(fileData, from, Math.min(dataLength, from + BLOCK_SIZE));
            blocks.add(buildBlock(i + 1, blockData));
        }

        return blocks;
    }

    public static ArrayList<String> buildBlocksForFile(String path) throws IOException {
        ArrayList<String> blocks = new ArrayList<>();
        byte[] fileData = FileUtil.readBytes(path);

        final int dataLength = fileData.length;
        final int blockCount = fileData.length / BLOCK_SIZE;

        for (int i = 0; i < blockCount; i++) {
            int from = i * BLOCK_SIZE;

            byte[] blockData = Arrays.copyOfRange(fileData, from, Math.min(dataLength, from + BLOCK_SIZE));
            blocks.add(buildBlock(i + 1, blockData));
        }

        return blocks;
    }

    public static int packagesSize(ArrayList<String> packages) {
        int size = 0;
        for (String pkg : packages) {
            size += pkg.length();
        }
        return size;
    }

    @NonNull
    public static byte[] getBytes(String chunk) {
        return chunk.getBytes(Charset.forName("ISO-8859-1"));
    }

    @NonNull
    public static String firstPackage(int type, int pkg) {
        String cmd =  String.format("V1%02x%02x$07$0000$%02x$", pkg & 0xff, (pkg >> 8) & 0xff, type & 0xff);
        return cmd + CRC16Util.calculate(cmd) + "\r\n";
    }

    @NonNull
    public static String lastPackage(int pkg) {
        String cmd = String.format("V1%02x%02x$07$FFFF$$", pkg & 0xff, (pkg >> 8) & 0xff);
        return cmd + CRC16Util.calculate(cmd) + "\r\n";
    }

    @NonNull
    private static String buildPackage(int pkg, int block, byte[] data) {
        String header = String.format("V1%02x%02x$07", pkg & 0xff, (pkg >> 8) & 0xff);
        Writer writer = new Writer();
        writer.writeString(header);
        writer.writeShort(block);
        writer.writeBytes(data);

        String crc = CRC16Util.calculate(writer.toString() +  "$");
        writer.writeString(crc);

        return writer.toString() + "\r\n";
    }

    @NonNull
    public static String buildPackage(int pkg, String payload) {
        String cmd = String.format("V1%02x%02x$07$%s$", pkg & 0xff, (pkg >> 8) & 0xff, payload);
        String crc = CRC16Util.calculate(cmd);
        return cmd + crc + "\r\n";
    }

    @NonNull
    public static ArrayList<String> getChunks(String cmd) {
        ArrayList<String> chunks = new ArrayList<>();
        for (int i = 0; i < cmd.length(); i += 20) {
            chunks.add(cmd.substring(i, Math.min(cmd.length(), i + 20)));
        }
        return chunks;
    }

    @NonNull
    private static String buildBlock(int block, byte[] data) {
        Writer writer = new Writer();
        writer.writeShort(block);
        writer.writeBytes(data);
        return writer.toString();
    }
}
