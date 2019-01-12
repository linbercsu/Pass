package com.handy.remoteproxy;

import com.handy.common.Logger;

import java.io.IOException;
import java.net.Socket;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class RemotePipe {
    final Listener listener;
    final Socket client;
    private Socket socket;
    private volatile int finish;

    public interface Listener {
        void onPipeError(RemotePipe pipe);
    }

    public RemotePipe(final Listener listener, final Socket client) {
        this.listener = listener;
        this.client = client;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waitToPipe(client);
                } catch (IOException e) {
                    Logger.e(e);
                    closeSafely(RemotePipe.this.client);
                    closeSafely(getSocket());
                    listener.onPipeError(RemotePipe.this);
                }

                finish++;
                onFinish();
            }
        });
        thread.setName("pipeWait:" + this.hashCode());
        thread.start();
    }


    public void pipe(final Socket socket) throws IOException {
        setSocket(socket);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pipe(socket, RemotePipe.this.client);
                } catch (IOException e) {
                    Logger.e(e);
                    closeSafely(socket);
                    closeSafely(client);
                }

                finish++;
                onFinish();
            }
        });
        thread.setName("pipe:" + this.hashCode());
        thread.start();
    }

    private void onFinish() {
        if (finish < 2)
            return;

        closeSafely(socket);
        closeSafely(client);
    }

    public synchronized void setSocket(Socket socket) {
        this.socket = socket;
    }

    public synchronized Socket getSocket() {
        return socket;
    }

    private void pipe(Socket sourceSocket, Socket targetSocket) throws IOException {
        Source source = Okio.source(sourceSocket);
//        timeout(source);
        Sink sink = Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, 8192);
            Logger.v("remote read1 %d", read);
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
    private void waitToPipe(Socket sourceSocket) throws IOException {
        Source source = Okio.source(sourceSocket);
        Sink sink = null;
//        Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, 8192);
            Logger.v("remote read2 %d", read);
            if (read > 0) {
                if (sink == null) {
                    Socket socket = getSocket();
                    if (socket == null) {
                        throw new IOException("got data before pipe.");
                    } else {
                        sink = Okio.sink(socket);

                    }
                }
                sink.write(buffer, read);
            }
            buffer.clear();
        } while (read > 0);

        if (sink == null)
            throw new IOException("socket end before pipe.");

        if (sink != null)
            sink.flush();

        if (read == -1) {
            closeOutput(socket);
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

    private void closeOutput(Socket socket) throws IOException {
        if (socket != null && !socket.isOutputShutdown())
            socket.shutdownOutput();
    }
}
