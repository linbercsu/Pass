package com.handy.remoteproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class RemoteControl implements Runnable, SocketServer.Listener {
    public static final long MAGIC_NUMBER = 1234567890l;
    private final int port;
    private Socket client;
    private final ExecutorService executorService;
    private final Object lock;

    public RemoteControl(int port) {
        this.port = port;
        executorService = Executors.newSingleThreadExecutor();
        lock = new Object();
    }

    public void requestWork(final int count) {
        System.out.println("requestWork " + count);
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Socket client;
                    synchronized (lock) {
                        while (RemoteControl.this.client == null) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        client = RemoteControl.this.client;
                    }


                    try {
                        requestWorkInternal(client, count);
                    } catch (Exception e) {
                        e.printStackTrace();

                        synchronized (lock) {
                            if (client == RemoteControl.this.client) {
                                try {
                                    RemoteControl.this.client.close();
                                } catch (Exception e1) {
                                    e1.printStackTrace();
                                }

                                RemoteControl.this.client = null;
                            }
                        }

                        continue;
                    }

                    break;

                }
            }
        });
    }

    private void requestWorkInternal(Socket socket, int count) throws IOException {
        System.out.println("requestWorkInternal " + count);
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
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onNewConnection(Socket socket) {
        synchronized (lock) {
            client = socket;
            lock.notifyAll();
        }
    }
}
