package com.handy.localproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class LocalControl implements Runnable {
    public static final long MAGIC_NUMBER = 1234567890l;
    private final String host;
    private final int port;
    private final Listener listener;
    private Socket socket;

    public interface Listener {
        void onRequestNewWorks(int count);
    }

    public LocalControl(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        new Thread(this).start();
    }

    @Override
    public void run() {
        try {

            while (true) {
                try {
                    listen();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                closeSafely(socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listen() throws IOException {
        socket = new Socket(host, port, null, 0);
        BufferedSource source = Okio.buffer(Okio.source(socket));
        source.timeout().timeout(2, TimeUnit.MINUTES);
        while (true) {
            long magic = source.readLong();
            if (magic != MAGIC_NUMBER) {
                throw new IOException("magic number error");
            }

            int count = source.readInt();
            if (count < 1) {
                throw new IOException("size number error");
            }

            listener.onRequestNewWorks(count);

            BufferedSink sink = Okio.buffer(Okio.sink(socket));
            sink.writeLong(MAGIC_NUMBER);
            sink.flush();
        }
    }

    private void closeSafely(Socket socket) {
        try {
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}