package com.mujinyu.shopping_custcare_service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.mujinyu.shopping_common.pojo.SensitiveWord;
import com.mujinyu.shopping_common.service.SensitiveWordService;
import com.mujinyu.shopping_custcare_service.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 敏感词服务实现类
 */
@DubboService
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {
    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordBs sensitiveWordBs;
    /**
     * 添加敏感词
     * @param sensitiveWord 敏感词对象
     */
    @Override
    public void addSensitiveWord(SensitiveWord sensitiveWord) {
        sensitiveWordMapper.insert(sensitiveWord);
        // 获取敏感词类型，如果为空则默认为“deny”，否则使用传入的类型，防止爆空指针异常
        String type = sensitiveWord.getType() == null ? "deny" : sensitiveWord.getType();
        // 刷新敏感词配置
        if ("deny".equals(type)) {
            // 添加到黑名单中
            sensitiveWordBs.addWord(sensitiveWord.getWord());
        } else if ("allow".equals(type)) {
            // 添加到白名单中
            sensitiveWordBs.addWordAllow(sensitiveWord.getWord());
        }
    }

    /**
     * 删除敏感词
     * @param id 敏感词ID
     */
    @Override
    public void deleteSensitiveWord(Long id) {
        SensitiveWord sensitiveWord = sensitiveWordMapper.selectById(id);
        sensitiveWordMapper.deleteById(id);
        if (sensitiveWord == null) return;
        // 刷新敏感词配置
        if ("deny".equals(sensitiveWord.getType())) {
            // 删除黑名单
            sensitiveWordBs.removeWord(sensitiveWord.getWord());
        } else if ("allow".equals(sensitiveWord.getType())) {
            // 删除白名单
            sensitiveWordBs.removeWordAllow(sensitiveWord.getWord());
        }
    }

    /**
     * 分页查询敏感词
     * @param page 页码
     * @param size 每页条数
     * @param word 关键词（可选，模糊匹配）
     * @param type 类型（可选，“deny”或“allow”）
     * @return 分页结果
     */
    @Override
    public Page<SensitiveWord> findSensitiveWords(int page, int size, String word, String type) {
        QueryWrapper<SensitiveWord> queryWrapper = new QueryWrapper<>();
        if (word != null && !word.isEmpty()) {
            queryWrapper.like("word", word);
        }
        if (type != null && !type.isEmpty()) {
            queryWrapper.eq("type", type);
        }
        return sensitiveWordMapper.selectPage(new Page<>(page, size), queryWrapper);
    }
}
