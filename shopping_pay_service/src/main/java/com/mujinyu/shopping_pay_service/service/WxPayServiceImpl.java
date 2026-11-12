package com.mujinyu.shopping_pay_service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mujinyu.shopping_common.pojo.Orders;
import com.mujinyu.shopping_common.pojo.Payment;
import com.mujinyu.shopping_common.pojo.WxRefundNotify;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.WxPayService;
import com.mujinyu.shopping_pay_service.config.WxPayProperties;
import com.mujinyu.shopping_pay_service.mapper.PaymentMapper;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 微信支付服务实现
 * 微信支付 APIv3（Native 扫码支付）。
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class WxPayServiceImpl implements WxPayService {

    /**
     * 支付结果回调路径，与 BFF 里 PaymentController 的映射保持一致
     */
    public static final String NATIVE_NOTIFY_PATH = "/user/payment/native/notify";

    private static final String REFUND_NOTIFY_PATH = "/user/payment/refund/notify";

    private final WxPayProperties properties;
    private final RSAAutoCertificateConfig rsaAutoCertificateConfig;
    private final NotificationParser notificationParser;
    private final PaymentMapper paymentMapper;
    private final RefundService refundService;

    @Override
    public String nativePay(Orders orders) {
        // 1. 订单必须存在
        if (orders == null) {
            throw new BusException(CodeEnum.NOT_FOUND);
        }
        // 2. 只有未付款（status = 1）的订单才能下单，防止重复支付
        if (orders.getStatus() == null || orders.getStatus() != 1) {
            throw new BusException(CodeEnum.ORDER_STATUS_ERROR);
        }
        if (orders.getPayment() == null) { // 防止订单金额为 0
            throw new BusException(CodeEnum.PAYMENT_ERROR);
        }
        // 调用微信支付下单接口
        NativePayService service = new NativePayService.Builder()
                .config(rsaAutoCertificateConfig)
                .build();

        // 3. 金额单位是「分」，数据库里存的是「元」，这里乘 100
        Amount amount = new Amount();
        amount.setTotal(orders.getPayment().multiply(new BigDecimal("100")).intValueExact());

        PrepayRequest request = new PrepayRequest();
        request.setAppid(properties.getAppid()); // 应用ID
        request.setMchid(properties.getMchId()); // 商户号
        request.setDescription("百战商城订单 " + orders.getId()); // 订单描述
        request.setOutTradeNo(orders.getId()); // 订单号
        request.setNotifyUrl(properties.getNotifyDomain() + NATIVE_NOTIFY_PATH); // 通知地址
        request.setAmount(amount); // 金额

        try {
            PrepayResponse response = service.prepay(request);
            String codeUrl = response.getCodeUrl();
            if (codeUrl == null || codeUrl.isEmpty()) {
                log.warn("微信下单未返回 code_url，订单：{}", orders.getId());
                throw new BusException(CodeEnum.QR_CODE_ERROR);
            }
            log.info("微信下单成功，订单：{}，code_url：{}", orders.getId(), codeUrl);
            return codeUrl;
        } catch (ServiceException e) {
            // 微信返回非 2xx 时抛出，带 HTTP 状态码和微信错误码
            log.error("微信下单失败，订单：{}，HTTP：{}，微信错误码：{}，信息：{}",
                    orders.getId(), e.getHttpStatusCode(), e.getErrorCode(), e.getErrorMessage());
            throw new BusException(CodeEnum.QR_CODE_ERROR);
        }
    }

    @Override
    public Payment notify(String body, String serial, String nonce, String signature, String timestamp) {
        Transaction transaction;
        try {
            // 1. 验签 + 解密，签名不对会抛异常
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(body)
                    .build();
            transaction = notificationParser.parse(requestParam, Transaction.class);
        } catch (Exception e) {
            log.error("微信支付回调验签失败", e);
            throw new BusException(CodeEnum.CHECK_SIGN_ERROR);
        }

        log.info("收到微信支付通知，订单：{}，交易状态：{}，微信支付单号：{}",
                transaction.getOutTradeNo(), transaction.getTradeState(), transaction.getTransactionId());

        // 2. 只处理支付成功的通知，其它状态直接忽略（但要回 SUCCESS，避免微信反复重试）
        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            return null;
        }
        // 3. 校验商户号，防止伪造通知
        if (!properties.getMchId().equals(transaction.getMchid())) {
            log.error("支付通知商户号不匹配，订单：{}，通知商户号：{}", transaction.getOutTradeNo(), transaction.getMchid());
            throw new BusException(CodeEnum.CHECK_SIGN_ERROR);
        }

        // 4. 组装支付记录
        return buildPayment(transaction, body);
    }

    /**
     * 把微信的交易对象转成我们的支付记录
     */
    private Payment buildPayment(Transaction transaction, String content) {
        Payment payment = new Payment();
        payment.setOrderId(transaction.getOutTradeNo());
        payment.setTransactionId(transaction.getTransactionId());
        payment.setTradeType(transaction.getTradeType() == null ? null : transaction.getTradeType().name());
        payment.setTradeState(transaction.getTradeState().name());
        if (transaction.getAmount() != null && transaction.getAmount().getPayerTotal() != null) {
            // 分 → 元
            payment.setPayerTotal(BigDecimal.valueOf(transaction.getAmount().getPayerTotal()).movePointLeft(2));
        }
        payment.setContent(content);
        payment.setCreateTime(new Date());
        return payment;
    }

    @Override
    public void addPayment(Payment payment) {
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            log.info("支付记录已存在（唯一键冲突），订单：{}", payment.getOrderId());
        }
    }

    @Override
    public Payment findPaymentByOrderId(String orderId) {
        if (orderId == null) {
            return null;
        }
        List<Payment> list = paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Payment syncOrder(String orderId) {
        // 主动查微信侧交易：本地已有记录直接返回；否则查微信，支付成功就把记录补录回来
        Payment exists = findPaymentByOrderId(orderId);
        if (exists != null) {
            return exists;
        }
        NativePayService service = new NativePayService.Builder()
                .config(rsaAutoCertificateConfig)
                .build();
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getMchId());
        request.setOutTradeNo(orderId);
        try {
            Transaction transaction = service.queryOrderByOutTradeNo(request);
            if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
                log.info("订单 {} 微信侧状态为 {}，无需补单", orderId, transaction.getTradeState());
                return null;
            }
            Payment payment = buildPayment(transaction, "主动查单补录");
            // 并发场景下支付回调可能已经写入同一订单的记录，唯一键冲突按已处理（addPayment 内部吞掉）
            addPayment(payment);
            log.info("主动查单补录成功，订单：{}，微信支付单号：{}", orderId, payment.getTransactionId());
            return payment;
        } catch (ServiceException e) {
            log.error("主动查单失败，订单：{}，微信错误码：{}，信息：{}",
                    orderId, e.getErrorCode(), e.getErrorMessage());
            return null;
        }
    }

    @Override
    public String queryOrder(String orderId) {
        NativePayService service = new NativePayService.Builder()
                .config(rsaAutoCertificateConfig)
                .build();
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(properties.getMchId());
        request.setOutTradeNo(orderId);
        try {
            Transaction transaction = service.queryOrderByOutTradeNo(request);
            return transaction.getTradeState() == null ? "ERROR" : transaction.getTradeState().name();
        } catch (ServiceException e) {
            log.error("查询微信订单失败，订单：{}，微信错误码：{}", orderId, e.getErrorCode());
            return "ERROR";
        }
    }

    @Override
    public void closeOrder(String orderId) {
        NativePayService service = new NativePayService.Builder()
                .config(rsaAutoCertificateConfig)
                .build();
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(properties.getMchId());
        request.setOutTradeNo(orderId);
        try {
            service.closeOrder(request);
            log.info("关闭微信订单成功，订单：{}", orderId);
        } catch (ServiceException e) {
            log.error("关闭微信订单失败，订单：{}，微信错误码：{}，信息：{}",
                    orderId, e.getErrorCode(), e.getErrorMessage());
            throw new BusException(CodeEnum.PAYMENT_ERROR);
        }
    }

    @Override
    public String refund(String orderId, String outRefundNo, BigDecimal amount, String reason) {
        Payment payment = findPaymentByOrderId(orderId); // 根据订单ID查询支付记录
        if (payment == null) {
            throw new BusException(CodeEnum.PAYMENT_ERROR); // 支付记录不存在，抛出异常
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusException(CodeEnum.REFUND_AMOUNT_ERROR); // 如果退款金额无效，抛出异常
        }
        // 以微信实收金额为上限，防止业务侧算错把钱多退了
        if (payment.getPayerTotal() != null && amount.compareTo(payment.getPayerTotal()) > 0) {
            throw new BusException(CodeEnum.REFUND_AMOUNT_ERROR); // 退款金额超过微信实收金额，抛出异常
        }

        long refundFen = amount.multiply(new BigDecimal("100")).intValueExact(); // 将退款金额转换为分
        Long totalFen = payment.getPayerTotal() == null ? null :
                payment.getPayerTotal().multiply(new BigDecimal("100")).longValueExact(); // 将微信实收金额转换为分

        CreateRequest request = new CreateRequest(); // 创建退款请求对象
        request.setOutTradeNo(orderId); // 设置订单号
        request.setOutRefundNo(outRefundNo); // 设置退款单号
        request.setReason(reason == null || reason.isBlank() ? "订单退款" : reason); // 设置退款原因
        request.setNotifyUrl(properties.getNotifyDomain() + REFUND_NOTIFY_PATH); // 设置退款通知URL

        AmountReq amountReq = new AmountReq(); // 创建退款金额对象
        amountReq.setRefund(refundFen); // 设置退款金额
        amountReq.setTotal(totalFen); // 设置订单总金额
        amountReq.setCurrency("CNY"); // 设置货币类型
        request.setAmount(amountReq); // 设置退款金额对象

        try {
            Refund result = refundService.create(request); // 发起退款请求
            log.info("微信退款受理成功 outRefundNo={} refundId={} status={}", outRefundNo, result.getRefundId(), result.getStatus());
            return result.getRefundId();
        } catch (ServiceException e) {
            log.error("微信退款接口失败 outRefundNo={}", outRefundNo, e);
            throw new BusException(CodeEnum.PAYMENT_ERROR); // 抛出异常
        }

    }

    @Override
    public WxRefundNotify notifyRefund(String body, String serial, String nonce, String signature, String timestamp) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial) // 序列号
                .nonce(nonce) // 随机字符串
                .signature(signature) // 签名
                .timestamp(timestamp) // 时间戳
                .body(body) // 通知内容
                .build();

        RefundNotification notification = notificationParser.parse(requestParam, RefundNotification.class);

        WxRefundNotify result = new WxRefundNotify();
        result.setOutRefundNo(notification.getOutRefundNo()); // 退款订单号
        result.setRefundId(notification.getRefundId()); // 退款ID
        result.setOutTradeNo(notification.getOutTradeNo()); // 原始交易号
        result.setStatus(notification.getRefundStatus() == null ? null : notification.getRefundStatus().name()); // 退款状态
        if (notification.getAmount() != null && notification.getAmount().getRefund() != null) {
            // 退款金额，做了位运算，进行元转分的处理。
            result.setRefundAmount(BigDecimal.valueOf(notification.getAmount().getRefund()).movePointLeft(2));
        }

        return result;
    }
}
