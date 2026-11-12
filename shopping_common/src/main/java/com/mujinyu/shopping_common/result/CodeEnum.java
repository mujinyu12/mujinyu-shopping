package com.mujinyu.shopping_common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 返回状态码的枚举类
 * <p>
 * 601 之后的状态码与课程原版保持一致（前端有硬编码判断，不要随意改号）
 */
@Getter
@AllArgsConstructor
public enum CodeEnum {

    // 正常
    SUCCESS(200, "成功"),
    // 用户未登录
    NOT_LOGIN(401, "用户未登录"),
    // 账号或密码错误
    ACCOUNT_OR_PASSWORD_ERROR(402, "账号或密码错误"),
    // 权限不足
    PERMISSION_DENIED(403, "权限不足"),
    // 接口不存在
    NOT_FOUND(404, "接口不存在"),
    // 系统异常
    SYSTEM_ERROR(500, "系统异常"),
    // 业务异常
    PARAMETER_ERROR(601, "参数异常"),
    // 添加商品类型异常
    INSERT_PRODUCT_TYPE_ERROR(602, "三级商品类型不能添加子类型"),
    // 删除商品类型异常
    DELETE_PRODUCT_TYPE_ERROR(603, "商品类型下存在子类型，禁止删除"),
    // 文件上传异常
    UPLOAD_FILE_ERROR(604, "文件上传异常"),
    // 注册验证码错误
    REGISTER_CODE_ERROR(605, "注册验证码错误"),
    // 注册手机号重复
    REGISTER_REPEAT_PHONE_ERROR(606, "注册手机号重复"),
    // 注册用户名重复
    REGISTER_REPEAT_NAME_ERROR(607, "注册用户名重复"),
    // 用户名或密码错误
    LOGIN_NAME_PASSWORD_ERROR(608, "用户名或密码错误"),
    // 登录验证码错误
    LOGIN_CODE_ERROR(609, "验证码错误"),
    // 该手机号没有注册
    LOGIN_NOPHONE_ERROR(610, "该手机号没有注册"),
    // 该用户状态异常
    LOGIN_USER_STATUS_ERROR(611, "该用户状态异常"),
    // 文件删除失败
    DELETE_FILE_ERROR(612, "文件删除失败"),
    // 订单状态异常（只有未付款的订单才能发起支付）
    ORDER_STATUS_ERROR(613, "订单状态异常，无法发起支付"),
    // 支付失败
    PAYMENT_ERROR(614, "支付失败"),
    // 支付二维码生成失败
    QR_CODE_ERROR(615, "支付二维码生成失败"),
    // 支付回调验签失败
    CHECK_SIGN_ERROR(616, "支付回调验签失败"),
    // 商品库存不足
    NO_STOCK_ERROR(617, "商品库存不足"),
    // 订单异常
    ORDER_EXPIRED_ERROR(618, "订单不存在或订单超时"),
    // 邮件发送失败
    MAIL_SEND_ERROR(619, "邮件发送失败"),
    // 无权限访问
    NO_PERMISSION(620, "无权限访问"),
    // 退款失败（微信侧拒绝、网络异常等）
    REFUND_ERROR(621, "退款失败，请稍后重试"),
    // 退款状态异常（当前状态不允许申请/审批）
    REFUND_STATUS_ERROR(622, "退款状态异常"),
    // 退款金额异常（超过实付金额或金额非法）
    REFUND_AMOUNT_ERROR(623, "退款金额异常"),
    // 流量异常（Sentinel 限流）
    FLOW_LIMIT(701, "流量异常"),
    // 限流兜底（降级、热点参数、授权、系统保护等）
    BLOCK_ERROR(702, "服务繁忙，请稍后再试"),
    ;

    private final Integer code;

    private final String message;
}
