package com.kt.narle.imageserver.queue;

import com.kt.narle.imageserver.data.Request;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.LinkedList;

@Component
@Getter @Setter
public class controllerQueue {

    private LinkedList<Request>reqQueue;

    @PostConstruct
    public void init() {
        reqQueue = new LinkedList<>();
    }
}
