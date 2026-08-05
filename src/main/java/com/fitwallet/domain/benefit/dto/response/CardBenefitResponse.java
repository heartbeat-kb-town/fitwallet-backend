package com.fitwallet.domain.benefit.dto.response;

import com.fitwallet.domain.benefit.dto.CardBenefitStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드 한 장의 예상 혜택 판정 결과. {@code status=AVAILABLE}이면 {@code reason}은 null이고,
 * {@code status}가 {@code NO_BENEFIT}이거나 사유가 {@code PREV_SPEND_NOT_MET}이면 {@code benefit}은 null이다.
 */
@ApiModel(description = "카드 한 장의 예상 혜택 판정 결과")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardBenefitResponse {

    @ApiModelProperty(value = "보유 카드 ID(카드 상품 ID가 아니다). 결제 요청에 그대로 쓴다", example = "1")
    private Long userCardId;

    @ApiModelProperty(value = "카드 이름", example = "KB국민 청춘대로 톡톡카드")
    private String cardName;

    @ApiModelProperty(value = "카드사 이름", example = "KB국민카드")
    private String cardCompanyName;

    @ApiModelProperty(value = "카드 이미지 URL(카드사 CDN 주소가 그대로 내려간다). 등록된 이미지가 없으면 null",
            example = "https://img1.kbcard.com/ST/img/cxc/kbcard/upload/img/product/09222_img.png")
    private String cardImageUrl;

    @ApiModelProperty(value = """
            판정 결과. 카드 항목 화면 분기의 기준이다.
            - `AVAILABLE`: 조건을 만족하는 혜택이 있고 한도도 남아 있다 — `benefit`이 채워지고 `reason`은 null
            - `CONDITION_NOT_MET`: 걸리는 혜택은 있으나 전월실적 미달이거나 한도 소진 — `reason`이 채워진다
            - `NO_BENEFIT`: 이 가맹점에 걸리는 혜택이 아예 없다 — `reason`만 채워지고 `benefit`은 null""",
            example = "AVAILABLE")
    private CardBenefitStatus status;

    @ApiModelProperty(value = "혜택을 받지 못하는 사유. status가 AVAILABLE이면 null")
    private BenefitReasonResponse reason;

    @ApiModelProperty(value = """
            적용될(또는 한도가 소진된) 혜택. status가 NO_BENEFIT이거나 사유가 PREV_SPEND_NOT_MET이면 null""")
    private BenefitDetailResponse benefit;
}
