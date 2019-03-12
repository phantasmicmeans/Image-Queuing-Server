package com.kt.narle.imageserver.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kt.narle.imageserver.data.Request;
import com.kt.narle.imageserver.data.Response;
import com.kt.narle.imageserver.exception.StorageFileNotFoundException;
import com.kt.narle.imageserver.exception.ZKException;
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
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
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

    /** Description
     *TODO <0> ####<진행중>####
     *   0. git Config를 이용해 서버 리스트를 받아온다.
     *   1. init()시, 서버 리스트를 받아오고 주키퍼에 없는 데이터는 추가한다.(key가 있으면 놔두고, 없으면 추가) -> git file 변동시 다시 읽어서 씀. //로직 구현 후
     *   ResponseEntity<Response> resultFromServer = null;

     *TODO <1> (완료)
     *   1. 이 이미지를 쏴주는 컨트롤러는 요청이 들어오면, 주키퍼에 접근해 path에서 데이터를 가져온다. (완료) -> ?10개씩 가득 찼을때만 저장하는 방법도 좋을듯?
     *   2. 읽어온 데이터 중에서 number가 가장 작은 키를 꺼낸다.(완료)
     *
     *TODO <2> (완료)
     *   1. Queue에는 userId 이름과, image 넣는다.
     *   2. 큐 사이즈가 10개 이상이면 store한다. 일단 다 들자.
     *
     *TODO <3> (완료)
     *   1.TO.DO <0> 에서 담아둔 Queue에서 데이터를 꺼낸다. (완료)
     *   2. ATC server에 전송할 리퀘스트 객체 생성. (완료)
     *
     *TODO <3-1> (완료)
     *   1. 주키퍼에 number - 1 -> setData(POST, key(uri), path)
     *   2. restTemplate Or Feign으로 http Call 실행.(완료)
     *   2. server Ip는 TO.DO<2> 에서 받아온 키, 이미지를 POST하고 response를 받는다. (완료)
     *
     *TODO <4> Response가 왔다면, Zookeeper에 write, 이 서버 입장에서는 GET 한것. (완료)
     *   1. 보낼때는 POST number + 1 setData(POST, key(url), path)
     *   2. 받을때는 GET number - 1 setData(GET, key(url), path)
     */

    @PostMapping("/upload/test/client/images")
    public ResponseEntity<?> getTest(HttpServletRequest servletRequest, @RequestParam("image")MultipartFile image, @RequestParam("userId") String userId) {
        logger.info("dfsdfsdfsdf");
        StopWatch stopWatch = new StopWatch("http");
        stopWatch.start("http");

        String id = servletRequest.getHeader("x-server-id");
        String timestamp = servletRequest.getHeader("x-auth-timestamp");
        String signature = servletRequest.getHeader("x-auth-signature");

        String testSignature = "ABCDE";

        if(id == null)
            return new ResponseEntity<>("x-server-id를 확인하세요.", HttpStatus.UNAUTHORIZED);
        if(!signature.equals(testSignature))
            return new ResponseEntity<>("권한이 없습니다", HttpStatus.UNAUTHORIZED);

        Optional.ofNullable(image).orElseThrow(()
                -> new StorageFileNotFoundException("파일을 로드할 수 없습니다."));
        reqValidator.isValidate(userId);

        if(image.getOriginalFilename() != null)
            return ResponseEntity.ok("created");

        Request request = Request.builder()
                .userId(userId)
                .image(image)
                .build();

        ResponseEntity<Response> resultFromServer = null;

        try {
            //TODO <1> (완료)
            String key = zk.getMinKeyFromZookeeper(path);
            if(key.equals("full"))
                return new ResponseEntity<>("잠시 후 다시 시도해주세요.", HttpStatus.INTERNAL_SERVER_ERROR);

            this.requestQueue.getReqQueue().offer(request); //TODO <2> (완료)

            if (key != null && !key.isEmpty()) {//TODO <3> (완료)
                if (!this.requestQueue.getReqQueue().isEmpty()) {
                    Request queRequest = this.requestQueue.getReqQueue().poll();
                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("image", queRequest.getImage().getResource());
                    body.add("userId", queRequest.getUserId());

                    HttpHeaders header = new HttpHeaders();
                    header.setContentType(MediaType.MULTIPART_FORM_DATA);

                    RestTemplate restTemplate = new RestTemplate();
                    HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(body, header);

                    try {
                        //TODO <3-1> (완료)
                        if (zk.update(HttpMethod.POST, key, path)) {
                            URI uri = new URI("http://" + key + "/upload");
                            try {
                                //TODO <4> (완료)
                                resultFromServer = restTemplate.exchange(uri, HttpMethod.POST, requestEntity, Response.class);
                                if (resultFromServer.getStatusCode().equals(HttpStatus.OK) && resultFromServer.getBody().getFilename() != null) {
                                    zk.update(HttpMethod.GET, key, path);
                                    stopWatch.stop();
                                    logger.info("total time :" + stopWatch.getTotalTimeSeconds());
                                    return ResponseEntity.ok(resultFromServer.getBody()); //정상 수행.
                                }
                            } catch (Exception e) {
                                zk.update(HttpMethod.GET, key, path); //요청 실패, 성공 둘다 카운트 감소해야함 .
                                return new ResponseEntity<>("ATC Error", HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                        } else {
                            logger.info("zkUpdate Exception :");
                            throw new ZKException("zkUpdate Exception");
                        }
                    } catch (URISyntaxException e) {
                        logger.info("URISyntaxException :" + e.getMessage());
                        return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }else
                    return new ResponseEntity<>("큐에 남아있는 작업이 없습니다", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }catch(KeeperException e) {
            logger.info("getMinKeyFromZookeeper KeeperException Message :" + e.getMessage());
            return new ResponseEntity<>("getMinKeyFromZookeeper KeeperException Message : " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        catch(InterruptedException e){
            logger.info("getMinKeyFromZookeeper InterruptException Message :" + e.getMessage());
            return new ResponseEntity<>("getMinKeyFromZookeeper InterruptException Message : " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>("\nUnknown ZK Error\n", HttpStatus.INTERNAL_SERVER_ERROR);
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
