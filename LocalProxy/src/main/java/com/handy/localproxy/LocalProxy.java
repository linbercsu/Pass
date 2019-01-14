package com.handy.localproxy;

import com.handy.common.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class LocalProxy implements LocalPipe.Listener, LocalControl.Listener {

    private final String remoteHost;
    private final int remotePort;
    private String targetHost;
    private final int targetPort;
    private final int controlPort;
    private Set<LocalPipe> works = Collections.synchronizedSet(new HashSet<LocalPipe>());

    private LinkedBlockingQueue<Integer> enableList = new LinkedBlockingQueue<>();

    public LocalProxy(String remoteHost, int remotePort, String targetHost, int targetPort, int controlPort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.controlPort = controlPort;
    }

    //java -jar LocalProxy-all.jar 18.223.238.245 8100 localhost 8098

    public static void main(String[] args) throws IOException {
        Logger.init(Logger.V);

        String remoteHost = "192.168.8.135";
        int remotePort = 8100;
        String targetHost = "192.168.8.135";
        int targetPort = 22;
        int controlPort = 8563;
        if (args.length >= 4) {
            remoteHost = args[0];
            remotePort = Integer.valueOf(args[1]);
            targetHost = args[2];
            targetPort = Integer.valueOf(args[3]);
        }

        if (args.length >= 5) {
            controlPort = Integer.valueOf(args[4]);
        }



        new LocalProxy(remoteHost, remotePort, targetHost, targetPort, controlPort).start();
    }

    private void start() throws IOException {
        Logger.d("local start");

        new LocalControl(remoteHost, controlPort, this).start();
//        while (true) {
//            if (works.size() < 1) {
//                try {
//                    createWorker();
//                } catch (Exception e) {
//                    Logger.e(e);
//                }
//            }
//        }
    }

    private void createWorker() throws IOException {
        Socket socket = new Socket(remoteHost, remotePort, null, 0);
        Logger.d("local new socket");
        LocalPipe localPipe = new LocalPipe(socket, this, targetHost, targetPort);
        works.add(localPipe);
        localPipe.start();
    }

    @Override
    public void onFinish(LocalPipe localPipe) {
        Logger.d("local socket finish");
        works.remove(localPipe);
    }

    @Override
    public void onRequestNewWorks(int count) {
        Logger.d("onRequestNewWorks %d", count);
        try {
            for (int i = 0; i < count; i++) {
                createWorker();
            }
        } catch (Exception e) {
            Logger.e(e);
        }
    }
}
