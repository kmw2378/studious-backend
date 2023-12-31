package nerds.studiousTestProject.studycafe.dto.modify.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import nerds.studiousTestProject.convenience.dto.modify.ModifyConvenienceRequest;
import nerds.studiousTestProject.refundpolicy.dto.modify.ModifyRefundPolicyRequest;

import java.util.List;

@Builder
@Data
public class ModifyStudycafeRequest {
    @NotBlank(message = "스터디카페 이름은 필수입니다.")
    private String name;    // 스터디카페 이름

    @NotNull(message = "스터디카페 주소는 필수입니다.")
    @Valid
    private ModifyAddressRequest address;

    @Size(min = 20, max = 100, message = "공간 소개 글은 최소 20자에서 최대 100자 사이여야 합니다.")
    private String introduction;

    @Size(min = 8, max = 8, message = "운영 시간은 필수입니다.")
    @Valid
    private List<ModifyOperationInfoRequest> operationInfos;

    @Size(min = 1, message = "공통 편의시설을 1개 이상 선택해주세요.")
    @Valid
    private List<ModifyConvenienceRequest> conveniences;

    @Size(min = 1, message = "스터디카페 사진을 1개 이상 등록해주세요.")
    private List<String> photos;

    @Size(min = 1, message = "유의 사항을 1개 이상 등록해주세요.")
    private List<String> notices;

    @Size(min = 9, max = 9, message = "환불 정책을 입력해주세요.")
    @Valid
    private List<ModifyRefundPolicyRequest> refundPolicies;
}
