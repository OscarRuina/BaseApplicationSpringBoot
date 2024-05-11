package com.organization.application.dtos.response;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Builder;
import lombok.Getter;

@JsonRootName("post")
@Getter
@Builder
public class PostResponseDTO {

    private Integer id;
    private Integer idUser;
    private String title;
    private String body;
}
