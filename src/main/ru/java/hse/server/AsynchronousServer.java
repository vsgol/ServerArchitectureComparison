package ru.java.hse.server;

import ru.java.hse.Utils;
import ru.java.hse.message.IntArray;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AsynchronousServer extends Server {
    private ExecutorService workerThreadPool;
    private AsynchronousServerSocketChannel serverSocketChannel;

    @Override
    public void start(int port) throws ServerException {
        workerThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2);
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.accept(serverSocketChannel, new CompletionHandler<>() {
                @Override
                public void completed(AsynchronousSocketChannel channel, AsynchronousServerSocketChannel server) {
                    server.accept(server, this);
                    var client = new ClientHandler(channel, currentClientId++);
                    channel.read(client.sizeBuffer, client, client.readHandler);
                }

                @Override
                public void failed(Throwable throwable, AsynchronousServerSocketChannel unused) {
//                    System.out.println("Не удалось подключить клиента");
                }
            });
        } catch (IOException e) {
            throw new ServerException(e);
        }
    }

    @Override
    public void shutdown() {
        try {
            workerThreadPool.shutdown();
            serverSocketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler {
        private final AsynchronousSocketChannel channel;
        private final ByteBuffer sizeBuffer = ByteBuffer.allocate(Integer.BYTES);
        private final int clientId;
        private ByteBuffer arrayBuffer;
        private boolean readingSize = true;
        private final WriteData writeData = new WriteData();

        private final Lock queue = new ReentrantLock();

        private final AtomicBoolean closed = new AtomicBoolean(false);

        private class WriteData {
            private final AtomicBoolean writeWorking = new AtomicBoolean(false);
            private final Queue<Pair<ByteBuffer, Integer>> outputs = new LinkedList<>();
            private volatile ByteBuffer currentBuffer;
            private int currentId;

            public ByteBuffer getNextOutput() {
                var tmp = outputs.remove();
                currentBuffer = tmp.left;
                currentId = tmp.right;
                return currentBuffer;
            }
        }

        private record Pair<A, B>(A left, B right) {}

        public final CompletionHandler<Integer, WriteData> writeHandler = new CompletionHandler<>() {
            @Override
            public void completed(Integer count, WriteData writeData) {
                if (count < 0) {
                    close();
                    return;
                }
                if (writeData.currentBuffer.hasRemaining()) {
                    channel.write(writeData.currentBuffer, writeData, this);
                    return;
                }
                endMeasure(writeData.currentId, clientId);

                queue.lock();
                try {
                    if (!writeData.outputs.isEmpty()) {
                        channel.write(writeData.getNextOutput(), writeData, this);
                    } else {
                        writeData.writeWorking.set(false);
                    }
                } finally {
                    queue.unlock();
                }
            }

            @Override
            public void failed(Throwable throwable, WriteData writeData) {
                close();
            }
        };
        public final CompletionHandler<Integer, ClientHandler> readHandler = new CompletionHandler<>() {
            @Override
            public void completed(Integer count, ClientHandler client) {
                if (count < 0) {
                    client.close();
                    return;
                }
                if (client.readingSize) {
                    if (client.sizeBuffer.hasRemaining()) {
                        client.channel.read(client.sizeBuffer, client, this);
                        return;
                    }
                    // Начинаем читать сообщение
                    startReadArray(client);
                    return;
                }
                if (client.arrayBuffer.hasRemaining()) {
                    client.channel.read(client.arrayBuffer, client, this);
                    return;
                }
                // Надо отправить сообщение
                client.readingSize = true;
                ByteBuffer buffer = client.arrayBuffer;

                // Отправляем решаться задачу
                processTask(client, buffer);

                // Начинаем читать новое сообщение
                client.channel.read(client.sizeBuffer, client, this);
            }

            private void startReadArray(ClientHandler client) {
                client.readingSize = false;
                client.sizeBuffer.flip();
                int size = client.sizeBuffer.getInt();
                client.sizeBuffer.clear();
                client.arrayBuffer = ByteBuffer.allocate(size);
                client.channel.read(client.arrayBuffer, client, this);
            }

            @Override
            public void failed(Throwable throwable, ClientHandler clientHandler) {
                clientHandler.close();
            }

            private void processTask(ClientHandler client, ByteBuffer dataBuffer) {
                try {
                    dataBuffer.flip();
                    IntArray data = Utils.readArray(dataBuffer);
                    startMeasure(data.id(), client.clientId);
                    workerThreadPool.submit(() -> {
                        IntArray newData = IntArray.sort(data);
                        client.write(Utils.writeArray(newData), newData.id());
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    client.close();
                }
            }
        };

        public ClientHandler(AsynchronousSocketChannel channel, int clientId) {
            this.channel = channel;
            this.clientId = clientId;
        }

        public void write(ByteBuffer buffer, int id) {
            queue.lock();
            try {
                writeData.outputs.add(new Pair<>(buffer, id));
                if (writeData.writeWorking.compareAndSet(false, true)) {
                    channel.write(writeData.getNextOutput(), writeData, writeHandler);
                }
            } finally {
                queue.unlock();
            }
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                stopCollectingStatistics();
                try {
                    if (channel.isOpen()) {
                        channel.close();
                    }
                } catch (IOException ignored) {

                }
            }
        }
    }
}
