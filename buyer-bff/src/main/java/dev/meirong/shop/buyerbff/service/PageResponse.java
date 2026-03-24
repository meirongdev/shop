package dev.meirong.shop.buyerbff.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageResponse<T>(List<T> content,
                              int number,
                              int size,
                              long totalElements,
                              int totalPages) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
