package com.kt.narle.imageserver.zookeeper;

import com.kt.narle.imageserver.exception.ZKException;
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

    /**
     * zookeeper getData, multi-thread synchronized
     * @param path
     * @return
     * @throws InterruptedException
     * @throws KeeperException
     */
    public synchronized String getData(String path) throws InterruptedException, KeeperException {
        final CountDownLatch connectedSignal = new CountDownLatch(1);
        byte[] bData = null;
        stat = Optional.ofNullable(isExists(path))
                .orElseThrow(() -> new ZKException("ZNode가 존재하지 않습니다"));

        try {
            bData = this.conn.getZoo().getData(path, watchedEvent -> {

                if (watchedEvent.getType() == Watcher.Event.EventType.None) {
                    switch (watchedEvent.getState()) {
                        case Expired:
                            connectedSignal.countDown();
                            break;
                    }
                } else {/*Watcher.Event.EventType.NodeCreated, Watcher.Event.EventType.NodeDataChanged
                          Watcher.Event.EventType.NodeDeleted
                          Watcher.Event.EventType.NodeChildrenChanged 등 이벤트 발생시
                          zookeeper cluster가 없어 아직 사용하지 않음.*/
                    try {
                        byte[] bn = conn.getZoo().getData(path, false, null);
                        String data = new String(bn, "UTF-8");
                        connectedSignal.countDown();
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                }}, stat);
        } catch(KeeperException e){
            throw new ZKException("getData Error : " + e.getMessage());
        }
        return byteArrayToString(bData);
    }

    /**
     * zookeeper로부터 가장 작은 서버를 가져옴. multi-thread synchronized
     * @param path
     * @return path에 쓰여진 데이터중 가장 작은 key값을 리턴한다. key는 ip:port다.
     * @throws KeeperException
     * @throws InterruptedException
     */
    public synchronized String getMinKeyFromZookeeper(String path) throws KeeperException, InterruptedException{
        String whole_data = Optional.ofNullable(this.getData(path)).
                orElseThrow(() -> new ZKException("Data가 없습니다"));
        StringTokenizer st = new StringTokenizer(whole_data,"\n");
        String [] urlAndCount = st.nextToken().split(" ");
        if(urlAndCount.length != 2) throw new ZKException("ZNode의 데이터가 정확하지 않습니다.");
        if(Integer.valueOf(urlAndCount[1]) < 20) {//data는 정렬되어져 있음. 0번째 데이터만 확인
            return urlAndCount[0];
        }
        return "full";
    }

    /**
     * zookeeper update, multi-thread synchronized
     * @param httpMethod
     * @param key
     * @param path
     * @return
     * @throws KeeperException
     * @throws InterruptedException
     */
    public synchronized boolean update(HttpMethod httpMethod, String key, String path) throws KeeperException, InterruptedException {
        String whole_data = this.getData(path);
        LinkedHashMap<String, Integer> data_map = new LinkedHashMap<>();

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
                    if (httpMethod.equals(HttpMethod.POST))  //POST의 경우 :+1
                        data_map.computeIfPresent(key, (k, v) -> v + 1);
                    else if (httpMethod.equals(HttpMethod.GET))  //GET의 경우 response를 받아온 것 :-1
                        data_map.computeIfPresent(key, (k, v) -> (v > 0 ? v = v - 1 : 0));
                } else
                    data_map.put(key, 0); //최초로 들어온 url인 경우 최초 데이터는 추가.
            }
        } else { //ZNode도 없는경우는 만든다.
            this.createZNode(path, (key + " " + "0").getBytes());
            return true;
        }

        try {
            this.conn.getZoo().setData(path, toByteArray(data_map), -1);
        } catch (Exception e) {
            throw new ZKException("setData 오류 :" + e.getMessage());
        }
        return true;
    }

    /**
     * LinkedHashMap to Byte
     * @param data_map
     * @return
     */
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

    /**
     * byte[] to String
     * @param bData
     * @return
     */
    public String byteArrayToString(byte[] bData) {
        try {
            return new String(bData, "UTF-8");
        }catch(UnsupportedEncodingException e){
            throw new ZKException(e.getMessage());
        }
    }

    /**
     * ZNode생
     * @param path
     * @param data
     * @throws KeeperException
     * @throws InterruptedException
     */
    public void createZNode(String path, byte [] data) throws KeeperException, InterruptedException {
        this.conn.getZoo().create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    /**
     * path에 ZNode가 존재하는지 여부
     * @param path
     * @return
     * @throws KeeperException
     * @throws InterruptedException
     */
    public Stat isExists(String path) throws KeeperException, InterruptedException {
        return this.conn.getZoo().exists(path, true);
    }

}
