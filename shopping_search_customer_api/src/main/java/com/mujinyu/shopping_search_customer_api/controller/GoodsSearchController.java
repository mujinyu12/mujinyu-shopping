package com.mujinyu.shopping_search_customer_api.controller;

import com.mujinyu.shopping_common.pojo.GoodsDesc;
import com.mujinyu.shopping_common.pojo.GoodsSearchParam;
import com.mujinyu.shopping_common.pojo.GoodsSearchResult;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.GoodsService;
import com.mujinyu.shopping_common.service.SearchService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户商品搜索
 */
@RestController
@RequestMapping("/user/goodsSearch")
public class GoodsSearchController {
    /** 每页条数上限 */
    private static final int MAX_PAGE_SIZE = 100;
    /** 深分页上限：page * size 不得超过该值 */
    private static final long MAX_PAGE_WINDOW = 10_000L;

    @DubboReference
    private SearchService searchService;
    @DubboReference
    private GoodsService goodsService;
    /**
     * 自动补齐关键字
     * @param keyword 被补齐的词
     * @return 补齐的关键词集合
     */
    @GetMapping("/autoSuggest")
    public BaseResult<List<String>> autoSuggest(@RequestParam ("keyword") String keyword) {
        // 关键字为空时没必要请求ES，直接返回空集合（缺陷 30）
        if (!StringUtils.hasText(keyword)) {
            return BaseResult.ok(List.of());
        }
        // 过长的关键字对前缀补全没有意义，直接截断，避免无谓的ES请求
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.length() > 50) {
            trimmedKeyword = trimmedKeyword.substring(0, 50);
        }
        List<String> keywords = searchService.autoSuggest(trimmedKeyword);
        return BaseResult.ok(keywords);
    }

    /**
     * 根据关键字搜索商品
     * @param goodsSearchParam 商品搜索参数
     * @return 商品搜索结果
     */
    @PostMapping("/search")
    public BaseResult<GoodsSearchResult> search(@RequestBody GoodsSearchParam goodsSearchParam) {
        // 分页参数先做一次快速失败校验，避免非法请求打到ES（缺陷 32）
        checkPaging(goodsSearchParam);
        GoodsSearchResult goodsSearchResult = searchService.search(goodsSearchParam);
        return BaseResult.ok(goodsSearchResult);
    }

    /**
     * 根据id查询商品详情（只允许查在售商品，下架商品返回 null）
     * @param id 商品id
     * @return 商品详情
     */
    @GetMapping("/findDesc")
    public BaseResult<GoodsDesc> findDesc(Long id) {
        if (id == null || id <= 0) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        // 顾客端只能看到在售商品，管理端编辑商品仍走 findDesc
        GoodsDesc goodsDesc = goodsService.findMarketableDesc(id);
        return BaseResult.ok(goodsDesc);
    }

    /**
     * 校验并规范化分页参数：page >= 1，1 <= size <= 100，page * size <= 10000
     * @param goodsSearchParam 查询参数，校验通过后会被回写为规范化后的值
     */
    private void checkPaging(GoodsSearchParam goodsSearchParam) {
        if (goodsSearchParam == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        // 页码缺失或非法时按第一页处理
        if (goodsSearchParam.getPage() == null || goodsSearchParam.getPage() < 1) {
            goodsSearchParam.setPage(1);
        }
        // 每页条数缺失或非法时按10条处理，超过上限则截断到上限
        if (goodsSearchParam.getSize() == null || goodsSearchParam.getSize() < 1) {
            goodsSearchParam.setSize(10);
        } else if (goodsSearchParam.getSize() > MAX_PAGE_SIZE) {
            goodsSearchParam.setSize(MAX_PAGE_SIZE);
        }
        // 深分页会拖垮ES，直接拒绝
        if ((long) goodsSearchParam.getPage() * goodsSearchParam.getSize() > MAX_PAGE_WINDOW) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
    }
}