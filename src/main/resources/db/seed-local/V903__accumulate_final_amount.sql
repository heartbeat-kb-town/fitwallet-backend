-- 공통 V11보다 뒤에 적재되는 로컬 데모 거래에도 같은 청구액 규칙을 적용한다.
UPDATE payment_transaction pt
    JOIN benefit_service bs
      ON bs.service_id = pt.applied_benefit_service_id
SET pt.final_amount = pt.amount
WHERE bs.benefit_type = 'ACCUMULATE'
  AND pt.final_amount <> pt.amount;
