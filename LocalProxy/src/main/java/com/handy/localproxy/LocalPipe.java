package com.handy.localproxy;

import com.handy.common.Logger;

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
                        Logger.v("local read %d", read);
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
                                            Logger.e(e);
                                            closeSafely(socket);
                                            closeSafely(remote);
                                        }

                                        listener.onFinish(LocalPipe.this);
                                    }
                                }).start();
                            }

                            sink.write(buffer, read);
                        }

                        buffer.clear();
                    } while (read > 0);

                    if (sink != null) {
                        sink.flush();
                    }

                    if (read == -1) {
                        closeOutput(socket);
                    }

                } catch (Exception e) {
                    Logger.e(e);
                }

                if (callback) {
                    listener.onFinish(LocalPipe.this);
                }
            }
        }).start();

    }

    private void closeOutput(Socket socket) throws IOException {
        if (socket != null && !socket.isOutputShutdown())
            socket.shutdownOutput();
    }

    private void closeSafely(Socket socket) {
        try {
            socket.close();
        } catch (Exception e) {
            Logger.e(e);
        }
    }

    private void timeout(Source source) {
        source.timeout().timeout(10, TimeUnit.SECONDS);
    }

    private void pipe(Socket sourceSocket, Socket targetSocket) throws IOException {
        Source source = Okio.source(sourceSocket);
//        timeout(source);
        Sink sink = Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, 8192);
            Logger.v("local read2 %d", read);
            if (read > 0) {
                sink.write(buffer, read);
            }
            buffer.clear();
        } while (read > 0);

        sink.flush();

        if (read == -1) {
            closeOutput(targetSocket);
        }
    }
}
