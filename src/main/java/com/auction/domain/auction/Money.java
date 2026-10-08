package com.auction.domain.auction;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) {

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be null or negative");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount()), currency);
    }

    public boolean isGreaterThanOrEqual(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount()) >= 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount()) > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency())) {
            throw new IllegalArgumentException("Different currencies: " + currency + " vs " + other.currency());
        }
    }
}
