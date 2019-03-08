package com.kt.narle.imageserver.validate;

import com.kt.narle.imageserver.exception.DataInvalidException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class dataValidator {

    public void isValidate(String userId) {
        if(userId == null && userId.isEmpty())
            throw new DataInvalidException("userId를 확인하세요.");
    }

}
