package com.bookuh.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShelfItemResult {

    private ShelfItemResponse response;
    private boolean created;
}