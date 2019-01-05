package com.handy.localproxy;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class LocalProxy implements LocalPipe.Listener {

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
        String remoteHost = "192.168.199.102";
        int remotePort = 8100;
        String targetHost = "18.223.238.245";
        int targetPort = 8098;
        int max = 10;
        if (args.length >= 4) {
            remoteHost = args[0];
            remotePort = Integer.valueOf(args[1]);
            targetHost = args[2];
            targetPort = Integer.valueOf(args[3]);
        }

        if (args.length >= 5) {
            max = Integer.valueOf(args[4]);
        }

        new LocalProxy(remoteHost, remotePort, targetHost, targetPort,  max).start();
    }

    private void start() throws IOException {
        System.out.println("local start");

        while (true) {
            if (works.size() < max) {
                try {
                    Socket socket = new Socket(remoteHost, remotePort, null, 0);
                    System.out.println("local new socket");
                    LocalPipe localPipe = new LocalPipe(socket, this, targetHost, targetPort);
                    works.add(localPipe);
                    localPipe.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onFinish(LocalPipe localPipe) {
        System.out.println("local socket finish");
        works.remove(localPipe);
    }
}
