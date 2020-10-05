package br.com.vertitecnologia.vmpay.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;

public class StringUtil {
    @NonNull
    public static String escape(@NonNull String value) {
        String result = "";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\')
                result += "\\\\";
            else if (c >= ' ' && c <= '~' || c >= '\u00a0' && c <= '\u00ff')
                result += c;
            else if (c == '\r')
                result += "\\r";
            else if (c == '\n')
                result += "\\n";
            else
                result += String.format("\\u%04x", (int) c);
        }
        return result;
    }

    @Nullable
    public static String byteArrayToString(@NonNull byte[] value, @NonNull  Charset charset) {
        CharsetDecoder decoder = charset.newDecoder();
        int len = (int) (value.length * decoder.maxCharsPerByte());
        char[] chars = new char[len];
        CharBuffer out = CharBuffer.wrap(chars);
        CoderResult cr;
        cr = decoder.decode(ByteBuffer.wrap(value), out, true);
        if (!cr.isUnderflow())
            return null;
        cr = decoder.flush(out);
        if (!cr.isUnderflow())
            return null;
        return new String(chars, 0, out.position());
    }

    @Nullable
    public static byte[] stringToByteArray(@NonNull String value, @NonNull  Charset charset) {
        CharsetEncoder encoder = charset.newEncoder();
        int len = (int) (value.length() * encoder.maxBytesPerChar());
        byte[] bytes = new byte[len];
        ByteBuffer out = ByteBuffer.wrap(bytes);
        CoderResult cr;
        cr = encoder.encode(CharBuffer.wrap(value), out, true);
        if (!cr.isUnderflow())
            return null;
        cr = encoder.flush(out);
        if (!cr.isUnderflow())
            return null;
        return Arrays.copyOfRange(bytes, 0, out.position());
    }

    @NonNull
    public static String intToHex(int n, int nBytes) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < nBytes; i += 1) {
            str.append(String.format("%02x", (n >> 8 * i) & 0xff));
        }
        return str.toString();
    }
}
