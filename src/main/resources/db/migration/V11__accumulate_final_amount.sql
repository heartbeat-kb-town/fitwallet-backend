-- 포인트 적립은 결제대금 할인이 아니므로 실제 청구액(final_amount)에서 차감하지 않는다.
-- 이미 올바른 행은 값이 같아 다시 실행해도 바뀌지 않는다.
UPDATE payment_transaction pt
    JOIN benefit_service bs
      ON bs.service_id = pt.applied_benefit_service_id
SET pt.final_amount = pt.amount
WHERE bs.benefit_type = 'ACCUMULATE'
  AND pt.final_amount <> pt.amount;
