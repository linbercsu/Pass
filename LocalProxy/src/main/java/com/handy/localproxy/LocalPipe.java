package com.handy.localproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class LocalPipe {
    public interface Listener {
        void onFinish(LocalPipe localPipe);
    }
    private final Socket remote;
    Socket socket;
    private final Listener listener;
    private final String targetHost;
    private final int targetPort;

    public LocalPipe(Socket remote, Listener listener, String targetHost, int targetPort) {
        this.remote = remote;
        this.listener = listener;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean callback = true;
                try {
                    Source source = Okio.source(remote);
                    Buffer buffer = new Buffer();
                    long read;
                    Sink sink = null;
                    do {
                        read = source.read(buffer, 8192);
                        System.out.println("local read " + read);
                        if (read > 0) {
                            if (socket == null) {
//                                timeout(source);

                                socket = new Socket(targetHost, targetPort, null, 0);
                                sink = Okio.sink(socket);

                                callback = false;
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            pipe(socket, remote);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        try {
                                            socket.close();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        closeRemote();

                                        listener.onFinish(LocalPipe.this);
                                    }
                                }).start();
                            }

                            sink.write(buffer, read);
                        }

                        buffer.clear();
                    }while (read > 0);

                    if (sink != null) {
                        sink.flush();
                    }

                } catch (Exception e) {
                    System.out.println("local exception " + e.getMessage());
                    e.printStackTrace();
                }

                if (callback) {
                    closeRemote();
                    listener.onFinish(LocalPipe.this);
                }
            }
        }).start();

    }

    private void closeRemote() {
        try {
            remote.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void timeout(Source source) {
        source.timeout().timeout(10, TimeUnit.SECONDS);
    }

    private void pipe(Socket sourceSocket, Socket targetSocket) throws IOException {
        Source source = Okio.source(sourceSocket);
        timeout(source);
        Sink sink = Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, 8192);
            System.out.println("local read2 " + read);
            if (read > 0) {
                sink.write(buffer, read);
            }
            buffer.clear();
        } while (read > 0);

        sink.flush();
    }
}
