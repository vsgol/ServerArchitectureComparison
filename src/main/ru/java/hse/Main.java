package ru.java.hse;

import org.jetbrains.annotations.NotNull;
import ru.java.hse.client.Client;
import ru.java.hse.server.AsynchronousServer;
import ru.java.hse.server.BlockingServer;
import ru.java.hse.server.Server;
import ru.java.hse.server.ServerException;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        StringBuilder sb = new StringBuilder();

        // Enter the server architecture
        ServerType serverType = getServerType(scanner);
        Server server = serverType.getServer();
        sb.append("Type of server: ").append(serverType).append(System.lineSeparator());

        // Enter the number of requests from each client
        int countRequests = getPositiveNumber(scanner, "number of requests from each client");
        sb.append("Number of requests from each client: ").append(countRequests).append(System.lineSeparator());

        // Enter the parameter that will be changed
        Parameter parameter = getParameter(scanner);
        sb.append("Current testing parameter: ").append(parameter).append(System.lineSeparator());

        // Enter the bounds
        Bounds bounds = getParameterBounds(scanner, parameter);
        sb.append("Bounds of ").append(parameter).append(": ").append(bounds).append(System.lineSeparator());

        // Where to write
        PrintStream out = getPrintStream(scanner);

        int arraySize = 0, numberOfClients = 0, timeDelta = 0;
        for (Parameter par : Parameter.values()) {
            if (par == parameter) {
                continue;
            }
            int value = getPositiveNumber(scanner, par.toString());
            switch (par) {
                case ARRAY_SIZE -> arraySize = value;
                case NUMBER_OF_CLIENTS -> numberOfClients = value;
                case TIME_BETWEEN_REQUESTS -> timeDelta = value;
            }
            sb.append(par).append(' ').append(value).append(System.lineSeparator());
        }
        sb.append('\n');
        for (int i : bounds) {
            switch (parameter) {
                case ARRAY_SIZE -> arraySize = i;
                case NUMBER_OF_CLIENTS -> numberOfClients = i;
                case TIME_BETWEEN_REQUESTS -> timeDelta = i;
            }
            long time = testServer(server, countRequests, arraySize, numberOfClients, timeDelta);
            sb.append(i).append(' ').append(time).append(System.lineSeparator());
            System.out.println(i + " " + time);
        }
        out.print(sb);
    }

    private static @NotNull PrintStream getPrintStream(@NotNull Scanner scanner) {
        while (true) {
            System.out.println("Select the file to output, leave the line blank to output to the terminal");
            System.out.print(">> ");
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                return System.out;
            }
            try {
                return new PrintStream(input);
            } catch (FileNotFoundException ignored) {
                System.out.println("Unable to open file");
            }
        }
    }

    private static @NotNull Parameter getParameter(@NotNull Scanner scanner) {
        Parameter parameter;
        while (true) {
            System.out.print("Select the parameter that will be changed: ");
            System.out.println(String.join(", ", Arrays.stream(Parameter.values())
                    .map(Enum::toString)
                    .toList()));
            System.out.print(">> ");
            String input = scanner.nextLine();
            parameter = Parameter.parseInput(input);
            if (parameter == null) {
                System.out.println("There is no such parameter");
                continue;
            }
            return parameter;
        }
    }

    private static @NotNull Bounds getParameterBounds(@NotNull Scanner scanner, @NotNull Parameter parameter) {
        while (true) {
            int lower, upper, step;
            System.out.println("Enter the bounds and step for " + parameter);
            System.out.print(">> ");
            try {
                lower = scanner.nextInt();
                upper = scanner.nextInt();
                step = scanner.nextInt();
            } catch (InputMismatchException e) {
                scanner.next();
                System.out.println("Enter a positive numbers");
                continue;
            }
            if (lower < 0 || upper < 0) {
                System.out.println("Bounds must be positive");
                continue;
            }
            if ((lower <= upper && step > 0) || (lower >= upper && step < 0)) {
                scanner.nextLine();
                return new Bounds(lower, upper, step);
            }
            System.out.println("Wrong parameter boundaries");
        }
    }

    private static int getPositiveNumber(@NotNull Scanner scanner, @NotNull String s) {
        int value;
        while (true) {
            System.out.println("Enter the " + s);
            System.out.print(">> ");
            try {
                value = scanner.nextInt();
            } catch (InputMismatchException e) {
                scanner.next();
                System.out.println("Enter a positive number");
                continue;
            }
            if (value < 0) {
                System.out.println("The number must be positive");
                continue;
            }
            scanner.nextLine();
            return value;
        }
    }

    private static @NotNull ServerType getServerType(@NotNull Scanner scanner) {
        while (true) {
            System.out.print("Select the type of server: ");
            System.out.println(String.join(", ", Arrays.stream(ServerType.values())
                    .map(Enum::toString)
                    .toList()));
            System.out.print(">> ");
            String input = scanner.nextLine();
            ServerType type = ServerType.parseInput(input);
            if (type == null) {
                System.out.println("There is no such architecture");
                continue;
            }
            return type;
        }
    }

    private static long testServer(Server server, int countRequests, int arraySize, int numberOfClients, int timeDelta) {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try {
            server.start(Constants.PORT);
            List<Future<Void>> futures = threadPool.invokeAll(
                    Stream.generate(() -> new Client(Constants.PORT, arraySize, timeDelta, countRequests))
                            .limit(numberOfClients)
                            .collect(Collectors.toList()));
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ServerException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            server.shutdown();
            threadPool.shutdown();
        }
        long result = server.getAverageTime();
        server.reset();
        return result;
    }

    private enum ServerType {
        BLOCKING {
            @Override
            public @NotNull Server getServer() {
                return new BlockingServer();
            }

            @Override
            public String toString() {
                return "blocking";
            }
        },
        ASYNCHRONOUS {
            @Override
            public @NotNull Server getServer() {
                return new AsynchronousServer();
            }

            @Override
            public String toString() {
                return "asynchronous";
            }
        };

        static ServerType parseInput(String input) {
            switch (input) {
                case "b", "blocking" -> {
                    return ServerType.BLOCKING;
                }
                case "a", "asynchronous" -> {
                    return ServerType.ASYNCHRONOUS;
                }
                default -> {
                    return null;
                }
            }
        }

        public abstract @NotNull Server getServer();
    }

    private enum Parameter {
        ARRAY_SIZE {
            @Override
            public String toString() {
                return "array size";
            }
        },
        NUMBER_OF_CLIENTS {
            @Override
            public String toString() {
                return "number of clients";
            }
        },
        TIME_BETWEEN_REQUESTS {
            @Override
            public String toString() {
                return "time between requests";
            }
        };


        static Parameter parseInput(String input) {
            switch (input) {
                case "size", "array size" -> {
                    return Parameter.ARRAY_SIZE;
                }
                case "number", "number of clients" -> {
                    return Parameter.NUMBER_OF_CLIENTS;
                }
                case "time", "time between requests" -> {
                    return Parameter.TIME_BETWEEN_REQUESTS;
                }
                default -> {
                    return null;
                }
            }
        }
    }

    record Bounds(int lower, int upper, int step) implements Iterable<Integer> {
        @Override
        public String toString() {
            return "from " + lower + " to " + upper + " with step " + step;
        }

        @NotNull
        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int current = lower;

                @Override
                public boolean hasNext() {
                    if (step > 0) {
                        return current <= upper;
                    } else {
                        return current >= upper;
                    }
                }

                @Override
                public Integer next() {
                    if (hasNext()) {
                        int tmp = current;
                        current += step;
                        return tmp;
                    } else {
                        throw new NoSuchElementException("Range reached the end");
                    }
                }
            };
        }
    }

}
