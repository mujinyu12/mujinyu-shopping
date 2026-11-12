package com.mujinyu.shopping_manager_api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.SensitiveWord;
import com.mujinyu.shopping_common.result.BaseResult;
import com.mujinyu.shopping_common.service.SensitiveWordService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 敏感词管理控制器
 */
@RestController
@RequestMapping("/sensitiveWord")
public class SensitiveWordController {
    @DubboReference
    private SensitiveWordService sensitiveWordService;

    /**
     * 添加敏感词
     * @param sensitiveWord 敏感词对象
     * @return 执行结果
     */
    @PostMapping("/add")
    public BaseResult addSensitiveWord(@RequestBody SensitiveWord sensitiveWord) {
        sensitiveWordService.addSensitiveWord(sensitiveWord);
        return BaseResult.ok("添加敏感词成功");
    }

    /**
     * 删除敏感词
     * @param id 敏感词主键ID
     * @return 执行结果
     */
    @DeleteMapping("/delete")
    public BaseResult deleteSensitiveWord(@RequestParam Long id) {
        sensitiveWordService.deleteSensitiveWord(id);
        return BaseResult.ok("删除敏感词成功");
    }

    /**
     * 分页查询敏感词
     * @param page 页码
     * @param size 每页条数
     * @param word 敏感词
     * @param type 类型
     * @return 分页查询结果
     */
    @GetMapping("/search")
    public BaseResult<Page<SensitiveWord>> searchSensitiveWord(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam(required = false) String word,
            @RequestParam(required = false) String type
    ) {
        return BaseResult.ok(sensitiveWordService.findSensitiveWords(page, size,word,type));
    }
}
