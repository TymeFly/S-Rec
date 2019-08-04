package me.tymefly.srec;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;


/**
 * Read a 16 bit S-Record file
 */
public class SReader {
    private static final int HEX_RADIX = 16;
    private static final int DIGITS_IN_BYTE = 2;

    // Indexes into SRecord lines
    private static final int INDEX_S = 0;
    private static final int INDEX_TYPE = 1;
    private static final int INDEX_COUNT = 2;
    private static final int INDEX_ADDRESS = 4;
    private static final int INDEX_DATA = 8;

    private static final int SIZE_COUNT = 2;
    private static final int SIZE_ADDRESS = 4;
    private static final int SIZE_CHECKSUM = 2;


    /** Data class that holds individual lines of data */
    private static class DataRecord {
        private final int start;
        private final byte[] data;

        DataRecord(int start, byte[] data) {
            this.start = start;
            this.data = data;
        }
    }

    private final String fileName;
    private boolean hasTerminated = false;
    private List<String> headers = new ArrayList<>();
    private int start = Integer.MAX_VALUE;
    private int end = 0;
    private byte[] dataBuffer;


    private SReader(@Nonnull String fileName) {
        this.fileName = fileName;
    }


    /**
     * Create a new SReader from data in the {@code source} file
     * @param source        File to read
     * @return SReader containing data from the {@code source} file
     */
    @Nonnull
    public static SReader load(@Nonnull File source) {
        return load(source.getAbsolutePath());
    }


    /**
     * Create a new SReader from data in the {@code source} file
     * @param source        File to read
     * @return SReader containing data from the {@code source} file
     */
    @Nonnull
    public static SReader load(@Nonnull String source) {
        SReader reader = new SReader(source);
        List<DataRecord> records = reader.load();

        reader.parse(records);
        reader.headers = Collections.unmodifiableList(reader.headers);
        reader.start = (reader.start == Integer.MAX_VALUE ? 0 : reader.start);

        return reader;
    }


    /**
     * Returns a list of all the header information in the SRecord file
     * @return a list of all the header information in the SRecord file
     */
    @Nonnull
    public List<String> getHeaders() {
        return Collections.unmodifiableList(headers);
    }


    /**
     * Returns the address of the first byte in the SRecord file
     * @return the address of the first byte in the SRecord file
     */
    public short getStartAddress() {
        return (short) start;
    }


    /**
     * Returns the address of the last byte in the SRecord file
     * @return the address of the last byte in the SRecord file
     */
    public short getEndAddress() {
        return (short) end;
    }


    /**
     * Returns the number of bytes in the SRecord file
     * @return the number of bytes in the SRecord file
     */
    public int size() {
        return dataBuffer.length;
    }


    /**
     * Returns the data in the SRecord file.
     * The first byte of the buffer will be at address {@link #getStartAddress()}, the last byte of the
     * buffer will be at address {@link #getEndAddress()} and the size of the buffer is given by {@link #size()}
     * @return the data in the SRecord file.
     */
    @Nonnull
    public byte[] getData() {
        return dataBuffer.clone();
    }


    @Nonnull
    private List<DataRecord> load() {
        List<DataRecord> records = new ArrayList<>();

        try {
            Path path = Paths.get(fileName);
            List<String> lines = Files.readAllLines(path, StandardCharsets.US_ASCII);
            int lineNumber = 0;

            for (String line : lines) {
                String data = line.trim().toUpperCase();

                lineNumber++;
                if (!data.isEmpty()) {
                    parseLine(records, data, lineNumber);
                }
            }

            if (!hasTerminated) {
                throw new SRecordException("Unexpected EOF");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return records;
    }


    /**
     * Parse a single SRecord. This can be any type of record.
     * @param records       Data from all parsed data records. This method may append data records.
     * @param line          An individual SRecord. This is guaranteed to have at least one character
     * @param lineNumber    The line number we are parsing
     */
    private void parseLine(@Nonnull List<DataRecord> records, @Nonnull String line, int lineNumber) {
        char start = line.charAt(INDEX_S);
        int type = parseHex(line, lineNumber, INDEX_TYPE, 1);
        int count = parseHex(line, lineNumber, INDEX_COUNT, SIZE_COUNT);
        int dataSize = count - 3;
        int address = parseHex(line, lineNumber, INDEX_ADDRESS, SIZE_ADDRESS);
        byte[] data = parseData(line, lineNumber, INDEX_DATA, (dataSize * DIGITS_IN_BYTE));
        byte checksum = (byte) parseHex(line, lineNumber, INDEX_DATA + (dataSize * DIGITS_IN_BYTE), SIZE_CHECKSUM);

        int actualChecksum = calculateChecksum(line, lineNumber);
        boolean valid = !hasTerminated && (start == 'S');
        valid = valid && (checksum == actualChecksum);
        valid = valid && (line.length() == (count * DIGITS_IN_BYTE) + SIZE_ADDRESS);

        if (!valid) {
            throw new SRecordException("Invalid record on line %d", lineNumber);
        }

        SRecord recordType = SRecord.fromType(type);

        switch (recordType) {
            case HEADER:
                parseHeader(data);
                break;

            case DATA_16:
                parseData(records, address, data);
                break;

            case DATA_24:
            case DATA_32:
                throw new SRecordException("Unsupported address size on line %d", lineNumber);

            case RESERVED:
                throw new SRecordException("S4 records are reserved. See line %d", lineNumber);

            case COUNT_16:
            case COUNT_24:
                parseCount(address, records.size());
                break;

            case START_ADDRESS_32:
            case START_ADDRESS_24:
                throw new SRecordException("Unsupported address termination type on line %d", lineNumber);

            case START_ADDRESS_16:
                hasTerminated = true;
                break;

            default:
                throw new SRecordException("Invalid SRecord type %d on line %d", type, lineNumber);
        }
    }


    private void parseHeader(@Nonnull byte[] data) {
        try {
            int index = data.length;

            while (index > 0) {
                if (data[index - 1] != 0) {
                    break;
                } else {
                    index--;
                }
            }

            String header = new String(data, 0, index, StandardCharsets.US_ASCII.name());

            headers.add(header);
        } catch (UnsupportedEncodingException e) {          // This should never happen
            e.printStackTrace();
        }
    }


    private void parseData(@Nonnull List<DataRecord> records, int start, @Nonnull byte[] data) {
        DataRecord record = new DataRecord(start, data);
        int end = start + data.length - 1;

        this.start = Math.min(this.start, start);
        this.end = Math.max(this.end, end);

        records.add(record);
    }


    private void parseCount(int expected, int actual) {
        if (expected != actual) {
            throw new SRecordException("Unexpected count. Got 0x%02x, expected 0x%02x", actual, expected);
        }
    }


    /**
     * Read a Hex number from withing the {@code line}
     * @param line          Line of data that contains a Hex value
     * @param lineNumber    The line of the SRecord file we are parsing
     * @param start         Index into string of the most significant hex digit to read
     * @param length        Number of hex digits to read; 1 for a nibble, 2 for a byte... Maximum value is 8
     * @return              Value of hex string
     */
    private int parseHex(@Nonnull String line, int lineNumber, int start, int length) {
        int end = start + length;

        if (end > line.length()) {
            throw new SRecordException("Record on line %d has been truncated", lineNumber);
        }

        String digits = line.substring(start, end);
        int value = Integer.parseInt(digits, HEX_RADIX);

        return value;
    }


    @Nonnull
    private byte[] parseData(@Nonnull String line, int lineNumber, int start, int length) {
        int size = length / DIGITS_IN_BYTE;
        byte[] data = new byte[size];
        int index = 0;

        while (index < size) {
            int value = parseHex(line, lineNumber, start, DIGITS_IN_BYTE);

            data[index++] = (byte) value;
            start += DIGITS_IN_BYTE;
        }

        return data;
    }


    private byte calculateChecksum(@Nonnull String line, int lineNumber) {
        int count = (line.length() / DIGITS_IN_BYTE) - 1;
        int index = 0;
        int value = 0;

        while (--count != 0) {
            index += DIGITS_IN_BYTE;
            value += parseHex(line, lineNumber, index, DIGITS_IN_BYTE);
        }

        value = ~value;

        return (byte) value;
    }


    /**
     * Populate the {@link #dataBuffer} with the content of the {code records}
     * @param records       Data records from the file
     */
    private void parse(@Nonnull List<DataRecord> records) {
        int size = (records.isEmpty() ? 0 : end - start + 1);

        this.dataBuffer = new byte[size];

        for (DataRecord record : records) {
            int offset = record.start - this.start;

            System.arraycopy(record.data, 0, this.dataBuffer, offset, record.data.length);
        }
    }
}
