package com.handy.remoteproxy;

import com.handy.common.Logger;

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
    private RemoteControl remoteControl;

    public RemoteProxy(int publicPort, int privatePort) {
        this.publicPort = publicPort;
        this.privatePort = privatePort;
    }

    //java -jar RemoteProxy-all.jar 8098 8100
    public static void main(String[] args) throws IOException, InterruptedException {
        Logger.init(Logger.D);

        int publicPort = 8098;
        int privatePort = 8100;

        if (args.length > 1) {
            publicPort = Integer.valueOf(args[0]);
            privatePort = Integer.valueOf(args[1]);
        }
        new RemoteProxy(publicPort, privatePort).start();
    }

    private void start() throws IOException, InterruptedException {
        Logger.d("remote start");
        remoteControl = new RemoteControl(8563);
        remoteControl.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                connectionSocket = new SocketServer(privatePort, new SocketServer.Listener() {
                    @Override
                    public void onNewConnection(Socket client) {
                        Logger.d("remote new client %d", localList.size());
                        RemotePipe local = new RemotePipe(client);
                        localList.add(local);
                    }
                });
                try {
                    connectionSocket.start();
                } catch (IOException e) {
                    Logger.e(e);
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
                    Logger.e(e);
                } catch (InterruptedException e) {
                    Logger.e(e);
                }
            }
        });
    }

    private void handleRequest(Socket socket) throws IOException, InterruptedException {
        forward(socket);
    }

    private void forward(Socket socket) throws IOException, InterruptedException {
        Logger.d("remote new connection");

        if (localList.size() < 5) {
            remoteControl.requestWork(5);
        }

        RemotePipe local = localList.take();
        local.pipe(socket);
    }
}
