package ru.java.hse;

import com.google.protobuf.ByteString;
import ru.java.hse.message.IntArray;
import ru.java.hse.message.IntArrayProtos;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.stream.IntStream;

public class Utils {
    public static IntArray readArray(InputStream inputStream) throws IOException {
        var dataInputStream = new DataInputStream(inputStream);
        int size = dataInputStream.readInt();
        var intArray = IntArrayProtos.IntArray.parseFrom(dataInputStream.readNBytes(size));

        int[] data = intArray.getArrayList().stream().mapToInt(x -> x).toArray();
        int id = intArray.getId();
        return new IntArray(id, data);
    }

    public static IntArray readArray(ByteBuffer inputBuffer) throws IOException {
        var intArray = IntArrayProtos.IntArray.parseFrom(inputBuffer);

        int[] data = intArray.getArrayList().stream().mapToInt(x -> x).toArray();
        int id = intArray.getId();
        return new IntArray(id, data);
    }

    public static void writeArray(OutputStream outputStream, IntArray data) throws IOException {
        var dataOutputStream = new DataOutputStream(outputStream);
        var intArray = IntArrayProtos.IntArray.newBuilder()
                .addAllArray(() -> IntStream.of(data.data()).iterator())
                .setId(data.id())
                .build();

        ByteString toWrite = intArray.toByteString();
        dataOutputStream.writeInt(toWrite.size());
        toWrite.writeTo(dataOutputStream);
        dataOutputStream.flush();
    }

    public static ByteBuffer writeArray(IntArray data) {
        var intArray = IntArrayProtos.IntArray.newBuilder()
                .addAllArray(() -> IntStream.of(data.data()).iterator())
                .setId(data.id())
                .build();

        int size = intArray.getSerializedSize();
        var byteBuffer = ByteBuffer.allocate(size + Integer.BYTES);
        byteBuffer.putInt(size);
        byteBuffer.put(intArray.toByteArray());
        byteBuffer.flip();
        return byteBuffer;
    }


}
