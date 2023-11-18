package com.organization.application.utils;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonPropertyOrder({"data", "message"})
@Data
@NoArgsConstructor
public class AppResponse <T>{

    private T data;

    private String message;

    public AppResponse(T data, String message){
        this.data = data;
        this.message = message;
    }

}
