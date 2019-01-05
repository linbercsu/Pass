package com.handy.remoteproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class RemotePipe {
    final Socket client;

    public RemotePipe(Socket client) {
        this.client = client;
    }


    public void pipe(final Socket socket) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pipe(socket, RemotePipe.this.client);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pipe(RemotePipe.this.client, socket);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    private void pipe(Socket sourceSocket, Socket targetSocket) throws IOException {
        Source source = Okio.source(sourceSocket);
//        timeout(source);
        Sink sink = Okio.sink(targetSocket);
        Buffer buffer = new Buffer();
        long read;
        do {
            read = source.read(buffer, 8192);
            System.out.println("remote read = " + read);
            if (read > 0) {
                sink.write(buffer, read);
            }
            buffer.clear();
        } while (read > 0);

        sink.flush();
    }

    private void timeout(Source source) {
        source.timeout().timeout(10, TimeUnit.SECONDS);
    }
}
