package com.handy.common;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import okio.AsyncTimeout;

public class TimeoutSocket extends Socket {

    private final Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final AsyncTimeout asyncTimeout;

    public TimeoutSocket(Socket socket) {
        this.socket = socket;
        asyncTimeout = new AsyncTimeout() {
            @Override
            protected void timedOut() {
                Logger.e("timeout socket timeout.");
                try {
                    TimeoutSocket.this.socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        asyncTimeout.timeout(5, TimeUnit.MINUTES);
        asyncTimeout.enter();
    }

    public synchronized void timeoutForClose(int seconds) {
        if (isClosed())
            return;

        asyncTimeout.exit();

        asyncTimeout.timeout(seconds, TimeUnit.SECONDS);

        asyncTimeout.enter();
    }

    private synchronized void markUnTimeout() {
        asyncTimeout.exit();
        asyncTimeout.enter();
    }

    @Override
    public synchronized InputStream getInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = new TimeoutInputStream(socket.getInputStream());

        }
        return inputStream;
    }

    @Override
    public synchronized OutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = new TimeoutOutputStream(socket.getOutputStream());
        }
        return outputStream;
    }

    @Override
    public boolean isInputShutdown() {
        return socket.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return socket.isOutputShutdown();
    }

    @Override
    public synchronized void close() throws IOException {
        asyncTimeout.exit();
        socket.close();
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    private class TimeoutInputStream extends FilterInputStream {


        protected TimeoutInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public int read(byte[] bytes, int i, int i1) throws IOException {
            int read = super.read(bytes, i, i1);
            markUnTimeout();
            return read;
        }

        @Override
        public int read() throws IOException {
            int read = super.read();
            markUnTimeout();
            return read;
        }
    }

    private class TimeoutOutputStream extends FilterOutputStream {

        public TimeoutOutputStream(OutputStream outputStream) {
            super(outputStream);
        }

        @Override
        public void write(byte[] bytes, int i, int i1) throws IOException {
            super.write(bytes, i, i1);
            markUnTimeout();
        }

        @Override
        public void write(int i) throws IOException {
            super.write(i);
            markUnTimeout();
        }
    }
}
