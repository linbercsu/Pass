package com.handy.remoteproxy;

import com.handy.common.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class RemoteControl implements Runnable, SocketServer.Listener {
    public static final long MAGIC_NUMBER = 1234567890l;
    private final int port;
    private List<Socket> clients = Collections.synchronizedList(new LinkedList<Socket>());
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
        Logger.e("requestWork %d %b", count, requesting);
        if (requesting)
            return;

        synchronized (lock) {
            if (clients.isEmpty()) {
                Logger.e("requestWork no client");
                return;
            }
        }

        requesting = true;
        executorService.submit(new Runnable() {
            @Override
            public void run() {

                try {
                    while (true) {
                        Socket client;
                        synchronized (lock) {
                            if (clients.isEmpty())
                                return;

                            int index = clients.size() - 1;
                            client = RemoteControl.this.clients.get(index);
                        }

                        try {
                            requestWorkInternal(client, count);
                            break;
                        } catch (Exception e) {
                            Logger.e(e);
                            synchronized (lock) {
                                closeSafely(client);
                                RemoteControl.this.clients.remove(client);
                            }
                        }
                    }
                } finally {
                    requesting = false;
                }
            }
        });
    }

    private void requestWorkInternal(Socket socket, int count) throws IOException {
        Logger.e("requestWorkInternal %d", count);
        BufferedSink sink = Okio.buffer(Okio.sink(socket));
        sink.timeout().timeout(10, TimeUnit.SECONDS);

        sink.writeLong(MAGIC_NUMBER);

        sink.writeInt(count);
        sink.flush();

        BufferedSource source = Okio.buffer(Okio.source(socket));
        source.timeout().timeout(10, TimeUnit.SECONDS);
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
            clients.add(socket);
            if (clients.size() > 3) {
                Socket remove = clients.remove(0);
                closeSafely(remove);
            }
//            closeSafely(client);
//            client = socket;
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
