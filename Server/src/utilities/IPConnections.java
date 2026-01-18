package utilities;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

public class IPConnections {
    public static void main(String[] args) {
        ConcurrentSkipListSet networkips = scan("192.168.1.1", 254);
        System.out.println(networkips);
        networkips.forEach(ip -> System.out.println(ip));
    }

    public static ConcurrentSkipListSet scan(String firstIpInNet, int numOfIps) {
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        final String networkId = firstIpInNet.substring(0, firstIpInNet.length() - 1);
        ConcurrentSkipListSet ipsSet = new ConcurrentSkipListSet();

        AtomicInteger ips = new AtomicInteger(0);
        while(ips.get() <= numOfIps) {
            String ip = networkId + ips.getAndIncrement();
            executorService.submit(() -> {
                try {
                    InetAddress inAdress = InetAddress.getByName(ip);
                    if(inAdress.isReachable(500)) {
                        System.out.println("found ip: " + ip);
                        ipsSet.add(ip);
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            });
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        }
        catch(InterruptedException e) {
            System.out.println(e.getMessage());
        }
        return ipsSet;
    }
}
