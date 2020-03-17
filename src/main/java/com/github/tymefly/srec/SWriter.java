package com.github.tymefly.srec;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;


/**
 * Write a 16 bit S-Record file
 */
public class SWriter implements AutoCloseable {
    private static final int BITS_IN_NIBBLE = 4;
    private static final int MASK_BYTE = 0xff;
    private static final int MASK_SHORT = 0xffff;
    private static final int MASK_LSN = 0xf;

    private static final char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7',
                                        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    /** Data class to hold individual Data records. */
    private static class Data {
        private final int address;
        private final byte[] bytes;

        private Data(int address, @Nonnull byte[] bytes, int start, int length) {
            this.address = address;
            this.bytes = Arrays.copyOfRange(bytes, start, start + length);
        }
    }

    private final File destination;
    private final List<String> headers;
    private final List<Data> data;


    /**
     * Create a writer object for 16-bit SRecord files
     * @param destination       File that will be created or overwritten.
     */
    public SWriter(@Nonnull File destination) {
        this.destination = destination;
        this.headers = new ArrayList<>();
        this.data = new ArrayList<>();
    }


    /**
     * Add a header to the SRecord file. Multiple headers can be added
     * @param header        Text in the header
     */
    public void withHeader(@Nonnull String header) {
        headers.add(header);
    }


    /**
     * Add a data line to the SRecord file. Multiple records can be added
     * @param address       Start address of the record
     * @param bytes         Data bytes
     * @param start         Index of first byte in {@code bytes} to be added to the SRecord
     * @param length        Number of bytes to be added to the SRecord
     * @throws ArrayIndexOutOfBoundsException if {@code start < 0} or {@code start + length > bytes.length}
     * @throws IllegalArgumentException if {@code length < 0}
     */
    public void withData(int address, @Nonnull byte[] bytes, int start, int length) {
        data.add(new Data(address, bytes, start, length));
    }


    @Override
    public void close() {
        try (
            Writer writer = new BufferedWriter(new FileWriter(destination))
        ) {
            int startAddress = Integer.MAX_VALUE;

            for (String header : headers) {
                writeHeader(writer, header);
            }

            for (Data line : data) {
                writeData(writer, line);
                startAddress = Math.min(startAddress, line.address);
            }

            if (data.isEmpty()) {
                startAddress = 0;
            }

            writeStartAddress(writer, startAddress);
        } catch (IOException e) {
            throw new SRecordException("Failed to write SRecord file " + destination.getAbsolutePath(), e);
        }
    }

    private void writeHeader(@Nonnull Writer writer, @Nonnull String header) throws IOException {
        writeLine(writer, SRecord.HEADER, 0, header.getBytes(StandardCharsets.US_ASCII));
    }


    private void writeData(@Nonnull Writer writer, @Nonnull Data data) throws IOException {
        writeLine(writer, SRecord.DATA_16, data.address, data.bytes);
    }


    private void writeStartAddress(@Nonnull Writer writer, int address) throws IOException {
        writeLine(writer, SRecord.START_ADDRESS_16, address, new byte[0]);
    }


    private void writeLine(@Nonnull Writer writer,
                           @Nonnull SRecord recordType,
                           int address,
                           @Nonnull byte[] data) throws IOException {
        StringBuilder line = new StringBuilder("S");
        int byteCount = data.length + 3;                 // 2 bytes for address + 1 for checksum
        int checksum = 0;

        address &= MASK_SHORT;

        line.append(recordType.getType());
        checksum += appendHex(line, byteCount, 1);
        checksum += appendHex(line, address, 2);
        checksum += appendHex(line, data);

        appendHex(line, ~checksum, 1);

        writer.write(line.toString());
        writer.write(System.lineSeparator());
    }


    /**
     * Append a number of hex digits to the {@code line} from an int
     * @param line      buffer to extend
     * @param value     An integer
     * @param bytes     The number of bytes to write: 1 for a single byte, 2 for a short, 4 for the full integer
     * @return          Sum of all the bytes written to the buffer
     */
    private int appendHex(@Nonnull StringBuilder line, int value, int bytes) {
        int sum = 0;

        while (bytes-- != 0 ) {
            int n = (value >> (bytes << 3)) & MASK_BYTE;
            char msb = HEX[n >> BITS_IN_NIBBLE];
            char lsb = HEX[n & MASK_LSN];

            line.append(msb).append(lsb);
            sum += n;
        }

        return sum;
    }


    /**
     * Append the hex values of {@code data} to {@code line}
     * @param line      buffer to extend
     * @param data      Data to write to buffer
     * @return          Sum of all the values written to the buffer
     */
    private int appendHex(@Nonnull StringBuilder line, @Nonnull byte[] data) {
        int sum = 0;

        for (byte n : data) {
            char msb = HEX[(n >> BITS_IN_NIBBLE) & MASK_LSN];
            char lsb = HEX[n & MASK_LSN];

            line.append(msb).append(lsb);
            sum += n;
        }

        return sum;
    }
}
