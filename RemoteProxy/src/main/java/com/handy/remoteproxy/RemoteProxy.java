package com.handy.remoteproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class RemoteProxy implements SocketServer.Listener {

    SocketServer server;
    private ExecutorService executorService;
    private SocketServer connectionSocket;
//    private RemotePipe local;
    private LinkedBlockingQueue<RemotePipe> localList = new LinkedBlockingQueue<>();

    private final int publicPort;
    private final int privatePort;

    public RemoteProxy(int publicPort, int privatePort) {
        this.publicPort = publicPort;
        this.privatePort = privatePort;
    }

    public static void main(String[] args) throws IOException {
        int publicPort = 8098;
        int privatePort = 8100;

        if (args.length > 1) {
            publicPort = Integer.valueOf(args[0]);
            privatePort = Integer.valueOf(args[1]);
        }
        new RemoteProxy(publicPort, privatePort).start();
    }

    private void start() throws IOException {
        System.out.println("remote start");
        new Thread(new Runnable() {
            @Override
            public void run() {
                connectionSocket = new SocketServer(privatePort, new SocketServer.Listener() {
                    @Override
                    public void onNewConnection(Socket client) {
                        System.out.println("remote new client " + localList.size());
                        RemotePipe local = new RemotePipe(client);
                        localList.add(local);
                    }
                });
                try {
                    connectionSocket.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        executorService = Executors.newCachedThreadPool();
        server = new SocketServer(publicPort, this);
        server.start();


    }

    @Override
    public void onNewConnection(final Socket socket) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    handleRequest(socket);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void handleRequest(Socket socket) throws IOException, InterruptedException {
        forward(socket);
    }

    private void forward(Socket socket) throws IOException, InterruptedException {
        System.out.println("remote new connection");
        RemotePipe local = localList.take();
        local.pipe(socket);
    }
}