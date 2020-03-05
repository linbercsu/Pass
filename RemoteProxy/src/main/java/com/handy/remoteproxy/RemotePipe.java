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
    public static final int BUFFER_SIZE = 8192 * 5;
    final Listener listener;
    final TimeoutSocket worker;
    private TimeoutSocket client;
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

    public synchronized void setClient(TimeoutSocket client) {
        this.client = client;
    }

    public synchronized TimeoutSocket getClient() {
        return client;
    }

    private void pipeToWorker() throws IOException {
        Socket sourceSocket = getClient();
        Socket targetSocket = worker;
        Source source = Okio.source(sourceSocket.getInputStream());
//        timeout(source);
        Sink sink = Okio.sink(targetSocket.getOutputStream());
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, BUFFER_SIZE);
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
        Source source = Okio.source(worker.getInputStream());
        Sink sink = null;
//        Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, BUFFER_SIZE);
            Logger.v("read from worker %d", read);
            if (read > 0) {
                if (sink == null) {
                    Socket socket = getClient();
                    if (socket == null) {
                        throw new IOException("got data from worker before pipe.");
                    } else {
                        sink = Okio.sink(socket.getOutputStream());

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
            /*
             *下行通道关闭后，如果上行通道没有数据交互，就关闭socket，
             * 如果不这样做，客户端可能复用这个处于半关闭状态下的socket，
             * 导致客户端长时间等待。
             */
            timeoutSafely(getClient());
        }
    }

    private void timeoutSafely(TimeoutSocket socket) {
        try {
            socket.timeoutForClose(2);
        } catch (Exception e) {

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
