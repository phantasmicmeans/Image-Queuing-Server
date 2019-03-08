package com.kt.narle.imageserver.zookeeper;

import lombok.Getter;
import lombok.Setter;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Getter @Setter
@Service
public class ZKCreate { //service

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static Stat stat = null;

    @Autowired
    private ZooKeeperConnection conn;

    public void createZNode(String path, byte [] data) throws KeeperException, InterruptedException {
        this.conn.getZoo().create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    public Stat isExists(String path) throws KeeperException, InterruptedException {
        return this.conn.getZoo().exists(path, true);
    }

    public String getData(String path) throws InterruptedException, KeeperException {
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        byte[] bData = null;
        stat = isExists(path);

        try {
            if (stat != null) { //zkNode가 있다면
                bData = this.conn.getZoo().getData(path, watchedEvent -> {

                    if (watchedEvent.getType() == Watcher.Event.EventType.None) {
                        switch (watchedEvent.getState()) {
                            case Expired:
                                connectedSignal.countDown();
                                break;
                        }
                    } else {// 무언가 이벤트 발생시
                    /*
                    Watcher.Event.EventType.NodeCreated
                    Watcher.Event.EventType.NodeDataChanged
                    Watcher.Event.EventType.NodeDeleted
                    Watcher.Event.EventType.NodeChildrenChanged 등
                     */
                        try {
                            byte[] bn = conn.getZoo().getData(path, false, null);
                            String data = new String(bn, "UTF-8");
//                            System.out.println("DATA: " + data);
                            connectedSignal.countDown();

                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }, stat);
            }else
            {
                throw new RuntimeException("zkNode가 존재하지 않습니다");
            }
        }
        catch(KeeperException e){
            throw new RuntimeException(e.getMessage());
        }

        String data = "";
        try {
            data = new String(bData, "UTF-8");
        }catch(UnsupportedEncodingException e) {
            data = "false";
        }
        return data;
    }

    public String getMinKeyFromZookeeper(String path) throws KeeperException, InterruptedException{
        //path에 쓰여진 데이터중 가장 작은 key값을 리턴한다. key는 ip:port다.
        String whole_data = Optional.ofNullable(this.getData(path)).
                                    orElseThrow(() -> new RuntimeException("data가 없습니다"));
        StringTokenizer st = new StringTokenizer(whole_data,"\n");
        String [] urlAndCount = st.nextToken().split(" ");
        if(Integer.valueOf(urlAndCount[1]) <= 20) {//data는 정렬되어져 있음. 0번째 데이터만 확인하면 됌.
            return urlAndCount[0];
        }
        return "full";

        /*
        String key = "";
        int min = Integer.MAX_VALUE;
        while(st.hasMoreTokens()) {
            String[] urlAndCount = st.nextToken().split(" ");
            int count = Integer.valueOf(urlAndCount[1]);
            if(count < min && count <= 10) { //10개 까지만.
                min = count;
                key = urlAndCount[0];
            }
        }
        return key;
        */
    }
    public boolean update(HttpMethod httpMethod, String key, String path) throws KeeperException, InterruptedException {
        String whole_data = this.getData(path);
        LinkedHashMap<String, Integer> data_map = new LinkedHashMap<>(); //url / count

        if (whole_data != null) {
            if (whole_data.isEmpty()) {
                data_map.put(key, 0);
            } else {
                String[] data_list = whole_data.split("\n");

                IntStream.range(0, data_list.length)
                        .forEach(idx -> {
                            String[] data_array = data_list[idx].split(" ");
                            data_map.put(data_array[0], Integer.valueOf(data_array[1]));
                        });

                if (data_map.containsKey(key)) {
                    if (httpMethod == HttpMethod.POST) { //POST의 경우 :+1
                        data_map.computeIfPresent(key, (k, v) -> v + 1);
                    } else if (httpMethod == HttpMethod.GET) { //GET의 경우 response를 받아온 것 :-1
                        data_map.computeIfPresent(key, (k, v) -> (v > 0 ? v - 1 : 0));
                    }
                } else { //최초로 들어온 url인 경우
                    data_map.put(key, 0); //최초 데이터는 추가.
                }
            }
        } else { //아예 ZNode도 없는경우는 만든다.
            this.createZNode(path, (key + " " + "0").getBytes());
            return true;
        }

        try {
            this.conn.getZoo().setData(path, toByteArray(data_map), -1);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("setData 오류 :" + e.getMessage());
        }
    }

    public byte[] toByteArray(LinkedHashMap<String, Integer> data_map) {
        LinkedHashMap<String, Integer> sorted =
                data_map.entrySet().stream()
                                    .sorted(Map.Entry.comparingByValue())
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            Map.Entry::getValue,
                                            (x,y) ->{ throw new AssertionError();},
                                            LinkedHashMap::new
                                    ));
        Iterator<String> iterator = sorted.keySet().iterator();
        List<String> keyList = new ArrayList<>();
        iterator.forEachRemaining(keyList::add);

        String value = "";
        for(int i = 0;i < sorted.size(); i++)
            value += keyList.get(i) + " " + sorted.get(keyList.get(i)) +"\n";
        return value.getBytes();
    }
}
