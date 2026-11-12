package com.mujinyu.shopping_user_customer_api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度在4到20之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度在8到32之间")
    private String password;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 注册验证码：可放在请求体里，也可放在 URL 参数上（二选一，以请求体优先） */
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String checkCode;

    @Size(max = 20, message = "昵称过长")
    private String nickName;
}
