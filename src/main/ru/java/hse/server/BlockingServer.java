package ru.java.hse.server;

import ru.java.hse.Utils;
import ru.java.hse.message.IntArray;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingServer extends Server {
    private ExecutorService serverSocketService;
    private ConcurrentLinkedQueue<ClientData> clients;
    private ExecutorService workerThreadPool;
    private ServerSocket serverSocket;

    private volatile boolean isWorking;

    public void start(int port) throws ServerException {
        workerThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2);
        clients = new ConcurrentLinkedQueue<>();
        serverSocketService = Executors.newSingleThreadExecutor();
        isWorking = true;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new ServerException(e);
        }
        serverSocketService.submit(() -> acceptClients(serverSocket));
    }

    public void shutdown() {
        isWorking = false;
        serverSocketService.shutdown();
        workerThreadPool.shutdown();
        clients.forEach(ClientData::close);
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptClients(ServerSocket serverSocket) {
        try (ServerSocket ignored = serverSocket) {
            while (isWorking) {
                Socket socket = serverSocket.accept();
                ClientData clientData = new ClientData(socket);
                clients.add(clientData);
                clientData.processClient();
            }
        } catch (IOException ignored) {
        }
    }

    private class ClientData {
        private final Socket socket;
        private final ExecutorService responseWriter = Executors.newSingleThreadExecutor();
        private final ExecutorService requestReader = Executors.newSingleThreadExecutor();

        private final int clientId;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile boolean working = true;

        public ClientData(Socket socket) throws IOException {
            this.socket = socket;
            clientId = currentClientId++;
        }

        public void sendResponse(IntArray array) {
            responseWriter.submit(() -> {
                try {
                    Utils.writeArray(socket.getOutputStream(), array);
                } catch (IOException ignored) {
                    stopCollectingStatistics();
                }
            });
        }

        public void processClient() {
            requestReader.submit(() -> {
                try {
                    while (working) {
                        IntArray data = Utils.readArray(socket.getInputStream());
                        final int id = data.id();
                        startMeasure(id, clientId);
                        workerThreadPool.submit(() -> {
                            IntArray newData = IntArray.sort(data);
                            sendResponse(newData);
                            endMeasure(id, clientId);
                        });
                    }
                } catch (IOException ignored) {
                } finally {
                    stopCollectingStatistics();
                    close();
                }
            });
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                working = false;
                responseWriter.shutdown();
                requestReader.shutdown();
                if (!socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

}
