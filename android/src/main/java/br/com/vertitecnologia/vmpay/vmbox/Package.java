package br.com.vertitecnologia.vmpay.vmbox;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import br.com.vertitecnologia.vmpay.utils.CRC16Util;

import static java.lang.Integer.parseInt;

public class Package {
    static final private String SEP = "$";
    static final private String EOF = "\r\n";

    private int number;
    private String opCode;
    private List<String> data = new ArrayList<>();
    private String crc;

    public Package(int number, String opCode) {
        this.number = number;
        this.opCode = opCode;
    }

    public Package(int number, String opCode, List<String> data) {
        this.number = number;
        this.opCode = opCode;
        this.data = data;
    }

    private String getHeader() {
        return String.format("V1%02x%02x$", number & 0xff, (number >> 8) & 0xff);
    }

    @NonNull
    private String getPayload() {
        StringBuilder resp = new StringBuilder(opCode + SEP);
        if (data.isEmpty()) resp.append(SEP);
        else {
            for (String p : data) {
                resp.append(p).append(SEP);
            }
        }
        return resp.toString();
    }

    @NonNull
    private String getHeaderAndPayload() {
        return getHeader() + getPayload();
    }

    private void calculateCRC() {
        crc = CRC16Util.calculate(getHeaderAndPayload());
    }

    public String toCommand() {
        calculateCRC();
        return getHeaderAndPayload() + crc + EOF;
    }

    // V10100$C0$9a62745b$31$c4bf695b$00000000$8525
    @NonNull
    public static Package fromCommand(String cmd) {
        String[] parts = cmd.split("$", -1);

        int number = 0;
        number = number | parseInt(parts[0].substring(2, 4), 16);
        number = number | parseInt(parts[0].substring(4, 6), 16) << 8;

        String opCode = parts[1];

        List<String> data = new ArrayList<>();
        for (int i = 2; i < parts.length - 1; i++) {
            data.add(parts[i]);
        }

        return new Package(number, opCode, data);
    }

    public boolean isLastPackage() {
        return data.get(0).equals("FFFF");
    }

    public List<String> getData() {
        return data;
    }
}
