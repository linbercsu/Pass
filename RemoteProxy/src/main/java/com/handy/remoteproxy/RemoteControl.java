package com.handy.remoteproxy;

import com.handy.common.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class RemoteControl implements Runnable, SocketServer.Listener {
    public static final long MAGIC_NUMBER = 1234567890l;
    private final int port;
    private Socket client;
    private final ExecutorService executorService;
    private final Object lock;
    private volatile boolean requesting;

    public RemoteControl(int port) {
        this.port = port;
        executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("control");
                return thread;
            }
        });
        lock = new Object();
    }

    public void requestWork(final int count) {
        Logger.d("requestWork %d %b", count, requesting);
        if (requesting)
            return;

        synchronized (lock) {
            if (client == null)
                return;
        }

        requesting = true;
        executorService.submit(new Runnable() {
            @Override
            public void run() {

                Socket client;
                synchronized (lock) {
                    if (RemoteControl.this.client == null)
                        return;

                    client = RemoteControl.this.client;
                }

                try {
                    requestWorkInternal(client, count);
                } catch (Exception e) {
                    Logger.e(e);
                    synchronized (lock) {
                        closeSafely(client);
                    }
                }

                requesting = false;
            }
        });
    }

    private void requestWorkInternal(Socket socket, int count) throws IOException {
        Logger.d("requestWorkInternal %d", count);
        BufferedSink sink = Okio.buffer(Okio.sink(socket));
        sink.writeLong(MAGIC_NUMBER);

        sink.writeInt(count);
        sink.flush();

        BufferedSource source = Okio.buffer(Okio.source(socket));
        long magic = source.readLong();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("magic number error");
        }
    }

    public void start() throws InterruptedException {
        new Thread(this).start();
        synchronized (this) {
            this.wait();
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            this.notify();
        }

        try {
            new SocketServer(port, this).start();
        } catch (IOException e) {
            Logger.e(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onNewConnection(Socket socket) {
        synchronized (lock) {
            closeSafely(client);
            client = socket;
            lock.notifyAll();
        }
    }

    private void closeSafely(Socket socket) {
        try {
            if (socket != null)
                socket.close();
        }catch (Exception e) {
//            Logger.e(e);
        }
    }
}
