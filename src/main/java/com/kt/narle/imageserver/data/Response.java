package com.kt.narle.imageserver.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter @Setter
@Builder @ToString
public class Response implements Serializable {

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("gender")
    private Integer gender;

    @JsonProperty("glasses_shape")
    private Integer glassShape;

    @JsonProperty("glasses_type")
    private Integer glassType;

    @JsonProperty("men_hair_length")
    private Integer menHairLength;

    @JsonProperty("men_hair_part")
    private Integer menHairPart;

    @JsonProperty("men_hair_wave")
    private Integer menHairWave;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("women_hair_front")
    private Integer womenHairFront;

    @JsonProperty("women_hair_length")
    private Integer womenHairLength;

    @JsonProperty("women_hair_wave")
    private Integer womenHairWave;

    public Response(
            @JsonProperty("filename")  String filename,
            @JsonProperty("gender")    Integer gender,
            @JsonProperty("glasses_shape")  Integer glassShape,
            @JsonProperty("glasses_type") Integer glassType,
            @JsonProperty("men_hair_length") Integer menHairLength,
            @JsonProperty("men_hair_part") Integer menHairPart,
            @JsonProperty("men_hair_wave") Integer menHairWave,
            @JsonProperty("userId")String userId,
            @JsonProperty("women_hair_front")Integer womenHairFront,
            @JsonProperty("women_hair_length") Integer womenHairLength,
            @JsonProperty("women_hair_wave") Integer womenHairWave)
    {
        this.filename = filename;
        this.gender = gender;
        this.glassShape = glassShape;
        this.glassType = glassType;
        this.menHairLength = menHairLength;
        this.menHairPart = menHairPart;
        this.menHairWave = menHairWave;
        this.userId = userId;
        this.womenHairFront = womenHairFront;
        this.womenHairLength = womenHairLength;
        this.womenHairWave = womenHairWave;

    }
}
