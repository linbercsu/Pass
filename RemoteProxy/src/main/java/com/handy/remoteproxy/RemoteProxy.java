package com.handy.remoteproxy;

import com.handy.common.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoteProxy implements SocketServer.Listener, RemotePipe.Listener {

    SocketServer server;
    private ExecutorService executorService;
    private SocketServer connectionSocket;
//    private RemotePipe local;
    private LinkedBlockingQueue<RemotePipe> localList = new LinkedBlockingQueue<>();

    private final int publicPort;
    private final int privatePort;
    private final int controlPort;
    private RemoteControl remoteControl;

    public RemoteProxy(int publicPort, int privatePort, int controlPort) {
        this.publicPort = publicPort;
        this.privatePort = privatePort;
        this.controlPort = controlPort;
    }

    //java -jar RemoteProxy-all.jar 8098 8100
    public static void main(String[] args) throws IOException, InterruptedException {
        Logger.init(Logger.D);

        int publicPort = 8098;
        int privatePort = 8100;
        int controlPort = 8563;

        if (args.length > 1) {
            publicPort = Integer.valueOf(args[0]);
            privatePort = Integer.valueOf(args[1]);
        }

        if (args.length > 2) {
            controlPort = Integer.valueOf(args[2]);
        }
        new RemoteProxy(publicPort, privatePort, controlPort).start();
    }

    private void start() throws IOException, InterruptedException {
        Logger.d("remote start");
        remoteControl = new RemoteControl(controlPort);
        remoteControl.start();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                connectionSocket = new SocketServer(privatePort, new SocketServer.Listener() {
                    @Override
                    public void onNewConnection(Socket client) {
                        Logger.d("remote new client %d", localList.size());
                        if (localList.size() > 10) {
                            try {
                                client.close();
                            }catch (Exception e) {

                            }

                            return;
                        }

                        RemotePipe local = new RemotePipe(RemoteProxy.this, client);
                        localList.add(local);
                    }
                });
                try {
                    connectionSocket.start();
                } catch (IOException e) {
                    Logger.e(e);
                }
            }
        });
        thread.setName("control connection");
        thread.start();

        executorService = Executors.newCachedThreadPool(new ThreadFactory() {
            AtomicInteger count = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("connection " + count.incrementAndGet());
                return thread;
            }
        });
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
                } catch (Exception e) {
                    Logger.e(e);
                    try {
                        socket.close();
                    } catch (Exception e2) {

                    }
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

        RemotePipe local = localList.poll(10, TimeUnit.SECONDS);
        if (local == null) {
            throw new IOException("No client proxy before timeout.");
        }
        local.pipe(socket);
    }

    @Override
    public void onPipeError(RemotePipe pipe) {
        localList.remove(pipe);
    }
}
