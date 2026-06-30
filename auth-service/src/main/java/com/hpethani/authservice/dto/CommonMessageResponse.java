package com.hpethani.authservice.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CommonMessageResponse {

    private String message;
}