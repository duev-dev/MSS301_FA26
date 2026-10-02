package com.fudn.orderservice.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Cac kich ban gia lap Inventory Service cho integration test.
 */
public final class InventoryStubs {

    private InventoryStubs() {
    }

    /** Inventory tra ve true (con hang). */
    public static void stubInventoryCall(String skuCode, Integer quantity) {
        stubInventory(skuCode, quantity, true);
    }

    /** Inventory tra ve false (het hang). */
    public static void stubInventoryOutOfStock(String skuCode, Integer quantity) {
        stubInventory(skuCode, quantity, false);
    }

    private static void stubInventory(String skuCode, Integer quantity, boolean inStock) {
        stubFor(get(urlEqualTo("/api/inventory?skuCode=" + skuCode + "&quantity=" + quantity))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.valueOf(inStock))));
    }
}
