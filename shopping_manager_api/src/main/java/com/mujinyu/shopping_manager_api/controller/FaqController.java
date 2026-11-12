package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.Faq;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.FaqService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * FAQ管理控制器（管理员端）
 */
@RestController
@RequestMapping("/faq")
public class FaqController {
    @DubboReference
    private FaqService faqService;

    /**
     * 分页查询FAQ
     * @param page 页码
     * @param size 每页条数
     * @param categoryId 分类ID（可选）
     * @return 查询结果
     */
    @GetMapping("/search")
    public BaseResult<Page<Faq>> search(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(required = false) Integer categoryId) {
        Page<Faq> faqPage = faqService.getFaqPage(page, size, categoryId);
        return BaseResult.ok(faqPage);
    }

    /**
     * 根据ID查询FAQ
     * @param id FAQ主键ID
     * @return 查询结果
     */
    @GetMapping("/findById")
    public BaseResult<Faq> findById(@RequestParam String id) {
        return BaseResult.ok(faqService.getFaqById(id));
    }

    /**
     * 添加FAQ
     * @param faq FAQ对象
     * @return 新增FAQ的ID
     */
    @PostMapping("/add")
    public BaseResult<String> add(@RequestBody Faq faq) {
        return BaseResult.ok(faqService.createFaq(faq));
    }

    /**
     * 更新FAQ
     * @param faq FAQ对象
     * @return 更新结果
     */
    @PutMapping("/update")
    public BaseResult update(@RequestBody Faq faq) {
        faqService.updateFaq(faq);
        return BaseResult.ok();
    }

    /**
     * 删除FAQ
     * @param id FAQ主键ID
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    public BaseResult delete(@RequestParam String id) {
        faqService.deleteFaq(id);
        return BaseResult.ok();
    }
}
