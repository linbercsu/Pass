package com.handy.remoteproxy;

import com.handy.common.Logger;
import com.handy.common.TimeoutSocket;

import java.io.IOException;
import java.net.Socket;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class RemotePipe {
    final Listener listener;
    final Socket worker;
    private Socket client;
    private volatile int finish;

    public interface Listener {
        void onPipeError(RemotePipe pipe);
    }

    public RemotePipe(final Listener listener, final Socket socket) {
        this.listener = listener;
        this.worker = new TimeoutSocket(socket);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waitToPipeToClient();
                } catch (IOException e) {
                    Logger.e(e);
                    closeSafely(worker);
                    closeSafely(getClient());
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
        setClient(new TimeoutSocket(socket));
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pipeToWorker();
                } catch (IOException e) {
                    Logger.e(e);
                    closeSafely(getClient());
                    closeSafely(worker);
                }

                finish++;
                onFinish();
            }
        });
        thread.setName("pipe:" + this.hashCode());
        thread.start();
    }

    private synchronized void onFinish() {
        if (finish < 2)
            return;

        closeSafely(getClient());
        closeSafely(worker);
    }

    public synchronized void setClient(Socket client) {
        this.client = client;
    }

    public synchronized Socket getClient() {
        return client;
    }

    private void pipeToWorker() throws IOException {
        Socket sourceSocket = getClient();
        Socket targetSocket = worker;
        Source source = Okio.source(sourceSocket);
//        timeout(source);
        Sink sink = Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, 8192);
            Logger.v("read from client %d", read);
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
    private void waitToPipeToClient() throws IOException {
        Source source = Okio.source(worker);
        Sink sink = null;
//        Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, 8192);
            Logger.v("read from worker %d", read);
            if (read > 0) {
                if (sink == null) {
                    Socket socket = getClient();
                    if (socket == null) {
                        throw new IOException("got data from worker before pipe.");
                    } else {
                        sink = Okio.sink(socket);

                    }
                }
                sink.write(buffer, read);
            }
            buffer.clear();
        } while (read > 0);

        if (sink == null)
            throw new IOException("worker socket end before pipe.");

        sink.flush();

        if (read == -1) {
            closeOutput(getClient());
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
