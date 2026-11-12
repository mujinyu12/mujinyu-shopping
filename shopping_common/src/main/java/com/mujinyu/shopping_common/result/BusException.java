package com.mujinyu.shopping_common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自定义业务异常
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BusException extends RuntimeException {
    // 业务异常码
    private Integer code;
    // 业务异常信息
    private String message;

    public BusException(CodeEnum codeEnum) {
        this.code = codeEnum.getCode();
        this.message = codeEnum.getMessage();
    }
}
