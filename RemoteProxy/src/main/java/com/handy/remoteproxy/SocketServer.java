package com.handy.remoteproxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer {

    public static interface Listener {
        void onNewConnection(Socket socket);
    }

    final int port;
    private ServerSocket serverSocket;
    private final Listener listener;

    public SocketServer(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        while (true) {
            Socket socket = serverSocket.accept();
            listener.onNewConnection(socket);
        }
    }
}
