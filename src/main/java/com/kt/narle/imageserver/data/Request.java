package com.kt.narle.imageserver.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotEmpty;


@Getter @Setter @Builder
@AllArgsConstructor @NoArgsConstructor
public class Request {

    @NotEmpty
    @JsonProperty("userId")
    private String userId;

    @NotEmpty
    @JsonProperty("image_name")
    private MultipartFile image;
}
