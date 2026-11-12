package com.mujinyu.shopping_user_customer_api.controller;

import com.mujinyu.shopping_common.pojo.ShoppingUser;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.MessageService;
import com.mujinyu.shopping_common.service.ShoppingUserService;
import com.mujinyu.shopping_common.util.RandomUtil;
import com.mujinyu.shopping_user_customer_api.dto.RegisterDTO;
import jakarta.validation.Valid;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 商城用户
 */
@RestController
@RequestMapping ("/user/shoppingUser")
public class ShoppingUserController {

    @DubboReference
    private MessageService messageService;

    @DubboReference
    private ShoppingUserService shoppingUserService;

    /**
     * 发送注册短信
     *
     * @param phone 注册的手机号
     * @return 操作结果
     */
    @GetMapping ("/sendMessage")
    public BaseResult sendMessage(@RequestParam ("phone") String phone) {
        // 1、生成随机六位数验证码
        String checkCode = RandomUtil.buildCheckCode(6);
        // 2、发送短信
        BaseResult result = messageService.sendMessage(phone, checkCode);
        // 3、发送成功，将验证码保存到 redis 中；发送失败，返回发送结果
        if (result != null && Integer.valueOf(200).equals(result.getCode())) {
            shoppingUserService.saveRegisterCheckCode(phone, checkCode);
            return BaseResult.ok();
        } else {
            return result;
        }
    }

    /**
     * 验证用户注册验证码
     *
     * @param phone     手机号
     * @param checkCode 验证码
     * @return 200 验证成功，605 验证码不正确
     */
    @GetMapping ("/registerCheckCode")
    public BaseResult registerCheckCode(@RequestParam ("phone") String phone,
                                        @RequestParam ("checkCode") String checkCode) {
        shoppingUserService.registerCheckCode(phone, checkCode);
        return BaseResult.ok();
    }

    /**
     * 用户注册
     *
     * @param dto       注册信息
     * @param checkCode 注册验证码（也允许放在请求体里，请求体优先）
     */
    @PostMapping ("/register")
    public BaseResult register(@Valid @RequestBody RegisterDTO dto,
                               @RequestParam (value = "checkCode", required = false) String checkCode) {
        // 兼容两种传参方式：请求体里的 checkCode 优先，其次取 URL 参数；都没有则按参数异常处理
        String code = dto.getCheckCode() != null && !dto.getCheckCode().isBlank()
                ? dto.getCheckCode() : checkCode;
        if (code == null || code.isBlank()) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        ShoppingUser shoppingUser = new ShoppingUser();
        shoppingUser.setUsername(dto.getUsername());
        shoppingUser.setPassword(dto.getPassword());
        shoppingUser.setPhone(dto.getPhone());
        shoppingUser.setNickName(dto.getNickName());
        shoppingUserService.register(shoppingUser, code);
        return BaseResult.ok();
    }

    /**
     * 用户名密码登录
     *
     * @param shoppingUser 用户对象
     * @return 登录令牌
     */
    @PostMapping ("/loginPassword")
    public BaseResult loginPassword(@RequestBody ShoppingUser shoppingUser) {
        String sign = shoppingUserService.loginPassword(shoppingUser.getUsername(), shoppingUser.getPassword());
        return BaseResult.ok(sign);
    }

    /**
     * 发送登录短信验证码
     *
     * @param phone 手机号
     */
    @GetMapping ("/sendLoginCheckCode")
    public BaseResult sendLoginCheckCode(@RequestParam ("phone") String phone) {
        // 1、判断用户手机号是否存在，状态是否正常
        shoppingUserService.checkPhone(phone);
        // 2、生成随机六位数验证码
        String checkCode = RandomUtil.buildCheckCode(6);
        // 3、发送短信
        BaseResult result = messageService.sendMessage(phone, checkCode);
        // 4、发送成功，将验证码保存到 redis 中
        if (result != null && Integer.valueOf(200).equals(result.getCode())) {
            shoppingUserService.saveLoginCheckCode(phone, checkCode);
            return BaseResult.ok();
        }
        return result;
    }

    /**
     * 手机号验证码登录
     *
     * @param phone     手机号
     * @param checkCode 验证码
     * @return 登录令牌
     */
    @PostMapping ("/loginCheckCode")
    public BaseResult loginCheckCode(@RequestParam ("phone") String phone,
                                     @RequestParam ("checkCode") String checkCode) {
        String sign = shoppingUserService.loginCheckCode(phone, checkCode);
        return BaseResult.ok(sign);
    }

    /**
     * 获取登录的用户名
     *
     * @param authorization 令牌（Authorization: Bearer xxx）
     */
    @GetMapping ("/getName")
    public BaseResult<String> getName(@RequestHeader ("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        String name = shoppingUserService.getName(token);
        return BaseResult.ok(name);
    }
}
