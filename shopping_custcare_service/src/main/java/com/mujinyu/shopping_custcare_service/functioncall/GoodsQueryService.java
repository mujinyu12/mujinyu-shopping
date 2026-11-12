package com.mujinyu.shopping_custcare_service.functioncall;

import java.util.List;
import java.util.function.Function;

import com.mujinyu.shopping_common.pojo.GoodsES;
import com.mujinyu.shopping_common.pojo.GoodsSearchParam;
import com.mujinyu.shopping_common.service.SearchService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class GoodsQueryService implements Function<GoodsQueryService.Request, GoodsQueryService.Response> {
    // check = false：搜索服务没启动时不要让整个 AI 客服跟着起不来，
    // 真正调用时连不上会抛异常，由下面的 catch 转成一句人话交给模型。
    @DubboReference(check = false, timeout = 5000, retries = 0)
    private SearchService searchService;

    /**
     * 应用
     * @param request the function argument
     * @return 商品查询响应
     */
    @Override
    public GoodsQueryService.Response apply(Request request) {
        Integer size = request.size == null ? 1 : request.size; // 默认返回 1 个结果
        String keyword = request.keyword == null ? "" : request.keyword; // 默认返回空字符串
        GoodsSearchParam goodsSearchParam = new GoodsSearchParam();
        goodsSearchParam.setPage(1);
        goodsSearchParam.setSize(size);
        goodsSearchParam.setKeyword(keyword);
        List<GoodsES> records;
        try {
            records = searchService.search(goodsSearchParam).getGoodsPage().getRecords();
        } catch (Exception e) {
            // 搜索服务不可用时给模型一句能转述的话，而不是把堆栈丢给用户
            throw new IllegalStateException("商品搜索服务暂时不可用，请稍后再试", e);
        }
        return new Response(records);
    }

    public record Request(String keyword, Integer size) {}

    public record Response(List<GoodsES> goodsESList) {}
}
