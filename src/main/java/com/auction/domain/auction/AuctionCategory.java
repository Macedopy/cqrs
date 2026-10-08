package com.auction.domain.auction;

/**
 * Common auction categories. Any other free-form category is also allowed.
 */
public final class AuctionCategory {

    public static final String SINGLE_ITEM = "SINGLE_ITEM";
    public static final String LOT_OF_GOODS = "LOT_OF_GOODS";
    public static final String LIVESTOCK = "LIVESTOCK";
    public static final String VEHICLE = "VEHICLE";
    public static final String AIRCRAFT = "AIRCRAFT";
    public static final String REAL_ESTATE = "REAL_ESTATE";

    private AuctionCategory() {
    }
}
