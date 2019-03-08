package com.kt.narle.imageserver.zookeeper;

import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ZKStat {

    @Autowired
    private ZooKeeperConnection connection;

    private Stat stat;


}
