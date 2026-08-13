package com.fitwallet.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자주 찾는 장소 한 건. {@code payment_transaction ⋈ user_card ⋈ store ⋈ category}
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FrequentPlaceResponse {

    private Long storeId;
    private String storeName;
    private String address;
    private String categoryName;
}
