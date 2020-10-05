package br.com.vertitecnologia.vmpay.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Base32768Util {
    private static class Bits {
        private final int value;
        private final int count;

        Bits(int value, int count) {
            this.value = value;
            this.count = count;
        }

        int getValue() {
            return value;
        }

        int getCount() {
            return count;
        }
    }

    private static class BitReader {
        private final InputStream is;
        private int value = 0;
        private int count = 0;

        BitReader(InputStream is) {
            this.is = is;
        }

        Bits readBits(int n) throws IOException {
            int result = 0;
            int p = 0;

            while (n > 0) {
                if (count == 0) {
                    int v = is.read();
                    if (v == -1)
                        break;
                    value = v;
                    count = 8;
                }

                int c = Math.min(n, count);
                int m = (1 << c) - 1;
                result = result | (value & m) << p;
                p += c;
                n -= c;
                value = value >> c;
                count -= c;
            }

            return new Bits(result, p);
        }
    }

    private static class BitWriter {
        private final OutputStream os;
        private final int bufferSize;
        private long value = 0;
        private int count = 0;

        BitWriter(OutputStream os, int bufferSize) {
            this.os = os;
            this.bufferSize = Math.max(bufferSize, 0);
        }

        void flush(int pad) throws IOException {
            if (pad >= count || (count - pad) % 8 != 0)
                throw new IllegalStateException("Bad padding");
            count -= pad;
            flushBuffer(0);
            value = 0;
            count = 0;
        }

        void writeBits(int value, int n) throws IOException {
            if (bufferSize + n + 8 > 64)
                throw new IllegalArgumentException("Bit buffer would overflow");

            this.value = this.value | ((long) value << count);
            count += n;
            flushBuffer(bufferSize);
        }

        private void flushBuffer(int n) throws IOException {
            while (count >= n + 8) {
                os.write((byte) this.value);
                this.value = this.value >> 8;
                count -= 8;
            }
        }
    }

    public static class InvalidInputException extends Exception {
        InvalidInputException(String message) {
            super(message);
        }
    }

    private static final int DM = 181;
    private static final int BITS = 15;
    private static final int NBASE = DM * DM - (1 << BITS);

    private static final int[] TAB = new int[] {
            0, 37,
            43, 1,
            61, 1,
            127, 34,
            173, 1
    };

    private static int encodeByte(int value) {
        for (int i = 0; i < TAB.length; i += 2) {
            if (value >= TAB[i])
                value += TAB[i + 1];
        }
        return value;
    }

    private static int decodeByte(int value) {
        int result = value;
        for (int i = TAB.length; i > 0; i -= 2) {
            if (result >= TAB[i - 2] + TAB[i - 1])
                result -= TAB[i - 1];
            else if (result >= TAB[i - 2])
                throw new IllegalArgumentException(
                        String.format("Invalid byte value 0x%02x", value));
        }
        return result;
    }

    private static int encodeInteger(int value) {
        value += NBASE;
        int rem = value % DM;
        int mod = (rem + DM) % DM;
        int div = (value - mod + rem) / DM;
        return encodeByte(mod + 1) + (encodeByte(div + 1) << 8);
    }

    private static int decodeInteger(int value) {
        int p = decodeByte(value >> 8);
        int q = decodeByte(value & 0xff);

        int result = (p - 1) * DM + (q - 1) - NBASE;

        if (result <= -BITS)
            throw new IllegalArgumentException(String.format(
                    "Invalid byte sequence: 0x%02x 0x%02x", value & 0xff, value >> 8));

        return result;
    }

    public static void encode(InputStream is, OutputStream os) throws IOException {
        BitReader br = new BitReader(is);
        while (true) {
            Bits bits = br.readBits(BITS);
            int cnt = bits.getCount();

            if (cnt > 0) {
                int value = encodeInteger(bits.getValue());
                os.write((byte) (value & 0xff));
                os.write((byte) (value >> 8));
            }
            if (cnt > 0 && cnt < BITS) {
                int value = encodeInteger(cnt - BITS);
                os.write((byte) (value & 0xff));
                os.write((byte) (value >> 8));
            }
            if (cnt < BITS)
                return;
        }
    }

    public static void decode(InputStream is, OutputStream os)
            throws IOException, InvalidInputException {
        BitWriter bw = new BitWriter(os, BITS);
        while (true) {
            int b1 = is.read();
            int b2 = is.read();

            try {
                if (b1 != -1 && b2 != -1) {
                    int value = decodeInteger(b1 + (b2 << 8));

                    if (value >= 0) {
                        bw.writeBits(value, BITS);
                    } else {
                        bw.flush(-value);
                        if (is.read() != -1)
                            throw new InvalidInputException("Data remaining after padding");
                        return;
                    }
                } else if (b1 != -1 || b2 != -1) {
                    throw new InvalidInputException("Non-even char count");
                } else {
                    bw.flush(0);
                    return;
                }
            } catch (IllegalStateException e) {
                throw new InvalidInputException(e.getMessage());
            }
        }
    }
}
