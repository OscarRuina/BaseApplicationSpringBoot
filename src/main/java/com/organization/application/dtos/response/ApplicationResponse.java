package com.organization.application.dtos.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonPropertyOrder({"data", "message"})
@Data
@NoArgsConstructor
public class ApplicationResponse<T>{

    private T data;

    private String message;

    public ApplicationResponse(T data, String message){
        this.data = data;
        this.message = message;
    }
}
