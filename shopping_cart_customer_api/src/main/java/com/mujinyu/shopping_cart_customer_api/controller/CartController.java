package com.mujinyu.shopping_cart_customer_api.controller;

import com.mujinyu.shopping_common.pojo.CartGoods;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.security.LoginUser;
import com.mujinyu.shopping_common.service.CartService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车
 */
@RestController
@RequestMapping("/user/cart")
public class CartController {
    @DubboReference
    private CartService cartService;

    /**
     * 查询用户购物车列表
     * @param userId 令牌中携带的用户ID
     * @return 购物车列表
     */
    @GetMapping("/findCartList")
    public BaseResult<List<CartGoods>> findCartList(@LoginUser Long userId) {
        return BaseResult.ok(cartService.findCartList(userId));
    }

    /**
     * 新增商品到购物车
     * @param cartGoods 购物车商品
     * @param userId 令牌中携带的用户ID
     * @return 添加结果
     */
    @PostMapping("/addCart")
    public BaseResult addCart(@RequestBody CartGoods cartGoods, @LoginUser Long userId) {
        cartService.addCart(userId, cartGoods);
        return BaseResult.ok("添加购物车成功");
    }

    /**
     * 修改购物车商品数量
     * @param userId 令牌中携带的用户ID
     * @param goodId 商品ID
     * @param num 修改后的数量
     * @return 修改结果
     */
    @GetMapping("/handleCart")
    public BaseResult handleCart(@LoginUser Long userId, @RequestParam Long goodId, @RequestParam Integer num) {
        cartService.handleCart(userId, goodId, num);
        return BaseResult.ok("修改购物车成功");
    }

    /**
     * 删除购物车商品
     * @param userId 令牌中携带的用户ID
     * @param goodId 商品ID
     * @return 删除结果
     */
    @DeleteMapping("/deleteCart")
    public BaseResult deleteCart(@LoginUser Long userId, @RequestParam Long goodId) {
        cartService.deleteCartOption(userId, goodId);
        return BaseResult.ok("删除购物车成功");
    }
}
