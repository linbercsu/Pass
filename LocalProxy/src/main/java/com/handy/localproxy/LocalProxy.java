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
    private final int max;
    private Set<LocalPipe> works = Collections.synchronizedSet(new HashSet<LocalPipe>());

    private LinkedBlockingQueue<Integer> enableList = new LinkedBlockingQueue<>();

    public LocalProxy(String remoteHost, int remotePort, String targetHost, int targetPort, int max) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.max = max;
    }

    //java -jar LocalProxy-all.jar 18.223.238.245 8100 localhost 8098

    public static void main(String[] args) throws IOException {
        Logger.init(Logger.D);

        String remoteHost = "192.168.199.102";
        int remotePort = 8100;
        String targetHost = "18.223.238.245";
        int targetPort = 8098;
        int max = 1;
        if (args.length >= 4) {
            remoteHost = args[0];
            remotePort = Integer.valueOf(args[1]);
            targetHost = args[2];
            targetPort = Integer.valueOf(args[3]);
        }

        if (args.length >= 5) {
            max = Integer.valueOf(args[4]);
        }

        new LocalProxy(remoteHost, remotePort, targetHost, targetPort, max).start();
    }

    private void start() throws IOException {
        Logger.d("local start");

        new LocalControl(remoteHost, 8563, this).start();
        while (true) {
            if (works.size() < max) {
                try {
                    createWorker();
                } catch (Exception e) {
                    Logger.e(e);
                }
            }
        }
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
        try {
            for (int i = 0; i < count; i++) {
                createWorker();
            }
        } catch (Exception e) {
            Logger.e(e);
        }
    }
}
