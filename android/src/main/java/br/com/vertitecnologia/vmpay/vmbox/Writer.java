package br.com.vertitecnologia.vmpay.vmbox;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import br.com.vertitecnologia.vmpay.utils.Base32768Util;
import br.com.vertitecnologia.vmpay.utils.StringUtil;

public class Writer {
    @NonNull
    static private final Charset charset = Charset.forName("ISO8859-1");

    final private char delimiter;
    private String result = "";
    private boolean isEmpty = true;

    public Writer() {
        delimiter = '$';
    }

    public void writeByte(int value) {
        writeDelimiter();
        result += String.format("%02x", value & 0xff);
    }

    public void writeShort(int value) {
        writeDelimiter();
        result += String.format("%02x%02x", value & 0xff, (value >> 8) & 0xff);
    }

    public void writeLong(long value) {
        writeDelimiter();
        result += String.format("%02x%02x%02x%02x", value & 0xff, (value >> 8) & 0xff,
                (value >> 16) & 0xff, (value >> 24) & 0xff);
    }

    public void writeBytes(@NonNull byte[] value) {
        writeDelimiter();

        ByteArrayInputStream bis = new ByteArrayInputStream(value);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            Base32768Util.encode(bis, bos);
        } catch (IOException e) {
            throw new RuntimeException(e); // should never happen
        }

        result += StringUtil.byteArrayToString(bos.toByteArray(), charset);
    }

    public void writeString(@NonNull String value) {
        writeDelimiter();
        result += value;
    }

    @Override
    public String toString() {
        return result;
    }

    private void writeDelimiter() {
        if (!isEmpty)
            result += delimiter;
        isEmpty = false;
    }
}