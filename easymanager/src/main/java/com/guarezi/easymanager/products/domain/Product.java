package com.guarezi.easymanager.products.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Product {
    private UUID id;
    private String name;
    private int amount;
    private String photoUrl;
    private String barcode;
}
