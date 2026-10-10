package com.example.library.reservation;

import com.example.library.loan.Loan;

public record ReservationFulfillmentResult(Loan loan, boolean expired) {

    public ReservationFulfillmentResult {
        if (expired == (loan != null)) {
            throw new IllegalArgumentException("取书结果必须且只能包含成功借阅或已过期状态");
        }
    }

    public static ReservationFulfillmentResult fulfilled(Loan loan) {
        if (loan == null) throw new IllegalArgumentException("成功取书必须包含借阅记录");
        return new ReservationFulfillmentResult(loan, false);
    }

    public static ReservationFulfillmentResult expiredResult() {
        return new ReservationFulfillmentResult(null, true);
    }
}
