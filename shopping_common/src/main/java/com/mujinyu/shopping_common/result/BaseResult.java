package com.mujinyu.shopping_common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseResult<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    // 状态码
    private Integer code;
    // 提示消息
    private String message;
    // 返回数据
    private T data;

    // 构建没有数据的成功结果
    public static <T> BaseResult<T> ok() {
        return new BaseResult<>(CodeEnum.SUCCESS.getCode(), CodeEnum.SUCCESS.getMessage(), null);
    }

    // 构建带有数据的成功结果
    public static <T> BaseResult<T> ok(T data) {
        return new BaseResult<>(CodeEnum.SUCCESS.getCode(), CodeEnum.SUCCESS.getMessage(), data);
    }
}
