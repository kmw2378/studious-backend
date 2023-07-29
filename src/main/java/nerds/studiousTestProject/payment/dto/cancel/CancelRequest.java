package nerds.studiousTestProject.payment.dto.cancel;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelRequest {

    @NotNull(message = "취소 사유가 없습니다.")
    private String cancelReason;

    @Nullable
    private Integer canselAmount;

}
