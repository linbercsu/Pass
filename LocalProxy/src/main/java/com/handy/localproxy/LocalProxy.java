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

    private static String findParameter(String[] args, String key) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals(key)) {
                return args[i+1];
            }
        }

        return "";
    }

    private static String findParameter(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--" + key)) {
                return args[i+1];
            }
        }

        return defaultValue;
    }

    //java -jar LocalProxy-all.jar --remote 18.223.238.245 --remote-port 8100 --target localhost --target-port 8098 --control-port 8564 --log-level 2

    public static void main(String[] args) throws IOException {
        int logLevel = Integer.parseInt(findParameter(args, "--log-level", "2"));
        Logger.init(logLevel);

        String remoteHost = findParameter(args, "--remote");
        int remotePort = Integer.parseInt(findParameter(args, "--remote-port"));
        String targetHost = findParameter(args, "--target");
        int targetPort = Integer.parseInt(findParameter(args, "--target-port"));
        int controlPort = Integer.parseInt(findParameter(args, "--control-port"));

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
