package com.kt.narle.imageserver.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.narle.imageserver.data.Request;
import com.kt.narle.imageserver.data.Response;
import com.kt.narle.imageserver.exception.StorageFileNotFoundException;
import com.kt.narle.imageserver.queue.controllerQueue;
import com.kt.narle.imageserver.service.StorageService;
import com.kt.narle.imageserver.validate.dataValidator;
import com.kt.narle.imageserver.zookeeper.ZKCreate;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Optional;

@RestController
@CrossOrigin("*")
public class ImageController {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private String path = "/firstNode";

    @Autowired
    private StorageService storageService;

    @Autowired
    private dataValidator reqValidator;

    @Autowired
    private controllerQueue requestQueue;

    @Autowired
    private ZKCreate zk;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/upload/test/client/images")
    public ResponseEntity<?> getTest( @RequestParam("image")MultipartFile image, @RequestParam("userId") String userId) {

        Optional.ofNullable(image).orElseThrow(()
                -> new StorageFileNotFoundException("파일을 로드할 수 없습니다."));
        reqValidator.isValidate(userId);

        Request request = Request.builder()
                                    .userId(userId)
                                    .image(image)
                                    .build();

        //TODO <0> (완료)
        // 1. Queue에는 userId 이름과, image 넣는다.
        // 2. 큐 사이즈가 10개 이상이면 store한다. 일단 다 들자.
        // 3.
        this.requestQueue.getReqQueue().offer(request);
        //this.storageService.store(image); //file은 storage에 일단 넣어놓고,
        //TODO <1> ####<진행중>####
        // 0. git Config를 이용해 서버 리스트를 받아온다.
        // 1. init()시, 서버 리스트를 받아오고 주키퍼에 없는 데이터는 추가한다.(key가 있으면 놔두고, 없으면 추가) -> git file 변동시 다시 읽어서 씀. //로직 구현 후
        ResponseEntity<Response> resultFromServer = null;

        //TODO <2> (완료)
        // 1. 이 이미지를 쏴주는 컨트롤러는 요청이 들어오면, 주키퍼에 접근해 path에서 데이터를 가져온다. (완료) -> ?10개씩 가득 찼을때만 저장하는 방법도 좋을듯?
        // 2. 읽어온 데이터 중에서 number가 가장 작은 키를 꺼낸다.(완료)
        try {
            String key = zk.getMinKeyFromZookeeper(path); //가장 작은 number를 가진 count 10 이하 , key를 가져옴.
            logger.info("지금 URL : " + key);

            if(key.equals("full")) { //15개씩 전부 요청이 갔을때는.
                throw new RuntimeException("잠시 후 다시 시도해주세요.");
            }
            //TODO <3> (완료)
            // 1.TO.DO <0> 에서 담아둔 Queue에서 데이터를 꺼낸다. (완료)
            // 2. ATC server에 전송할 리퀘스트 객체 생성. (완료)
            if (key != null && !key.isEmpty()) {
                if (!this.requestQueue.getReqQueue().isEmpty()) {
                    Request queRequest = this.requestQueue.getReqQueue().poll();
                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    //body.add("image", this.storageService.loadAsResource(queRequest.getImageName())); //image와d
                    body.add("image", queRequest.getImage().getResource());
                    body.add("userId", queRequest.getUserId());

                    HttpHeaders header = new HttpHeaders();
                    header.setContentType(MediaType.MULTIPART_FORM_DATA);

                    RestTemplate restTemplate = new RestTemplate();
                    HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(body, header);

                    try {
                        //TODO <3-1> (완료)
                        // 1. 주키퍼에 number - 1 -> setData(POST, key(uri), path)
                        // 2. restTemplate Or Feign으로 http Call 실행.(완료)
                        // 2. server Ip는 TO.DO<2> 에서 받아온 키, 이미지를 POST하고 response를 받는다. (완료)
                        // 3. 트랜잭션 묶어야 함. (진행)

                        if (zk.update(HttpMethod.POST, key, path)) { //보낸 key를 + 1 하고,
                            URI uri = new URI("http://" + key + "/upload");
                            try {
                                resultFromServer = restTemplate
                                        .exchange(uri, HttpMethod.POST, requestEntity, Response.class); // 리퀘스트를 요청한다.
                                //TODO <4> Response가 왔다면, Zookeeper에 write, 이 서버 입장에서는 GET 한것.
                                //  1. number - 1 -> setData(POST, (key)url , path)를 진행한다. (완료)
                                if (resultFromServer.getStatusCode() == HttpStatus.OK && resultFromServer.getBody().getFilename() != null) {
                                    zk.update(HttpMethod.GET, key, path); //요청 실패, 성공 둘다 카운트 감소해야함 .
                                    return ResponseEntity.ok(resultFromServer.getBody()); //정상 수행.
                                }
                            } catch (Exception e) {
                                zk.update(HttpMethod.GET, key, path); //요청 실패, 성공 둘다 카운트 감소해야함 .
                                logger.info("ATC Server Error :" + e.getMessage());
                            }
                            //this.storageService.delete(queRequest.getImageName()); //보낸 사진 지운다. (10개 넘었을때 디렉토리에 있는거 지움) -> 큐에 넣어지면 지움.
                        } else {
                            throw new RuntimeException("zkUpdate Exception");
                        }
                    } catch (Exception e) {
                        logger.info(e.getMessage());
                    }
                }
            }
        }catch(KeeperException e) {
            logger.info("getMinKeyFromZookeeper KeeperException Message :" + e.getMessage());
        }
        catch(InterruptedException e){
            logger.info("getMinKeyFromZookeeper InterruptException Message :" + e.getMessage());
        }

        return new ResponseEntity<>("\n\nError\n\n", HttpStatus.INTERNAL_SERVER_ERROR);

    }

    @GetMapping("/zookeeper")
    public String makerZooke() {
        String path = "/firstNode";
        byte [] data = ("183.98.154.56:4001 0").getBytes();

        try{
            this.zk.createZNode(path, data);
        }catch(Exception e) {
            logger.info(e.getMessage());
        }
        return "aaa";
    }

}
