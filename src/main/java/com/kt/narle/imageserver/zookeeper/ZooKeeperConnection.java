package com.kt.narle.imageserver.zookeeper;

import lombok.Getter;
import lombok.Setter;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

@Getter @Setter
@Service
public class ZooKeeperConnection {

    private ZooKeeper zoo;

    @PostConstruct
    public void init() {
        try {
            this.connect("localhost"); //connection만 만들어 놓는다.
        }catch(Exception e) {
            throw new RuntimeException("ZooKeeper 연동 에러: " +  e.getMessage());
        }
    }

    final CountDownLatch connectedSignal = new CountDownLatch(1);

    //Method to connect zookeeper ensemble
    public ZooKeeper connect(String host) throws IOException, InterruptedException {

        zoo = new ZooKeeper(host, 5000, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {

                if(watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                    connectedSignal.countDown();
                }
            }
        });
        connectedSignal.await();
        return zoo;
    }

    public void close() throws InterruptedException {
        zoo.close();
    }

}
