ALTER TABLE loan
    DROP CHECK ck_loan_renew_count,
    ADD CONSTRAINT ck_loan_renew_count CHECK (renew_count BETWEEN 0 AND 1);
