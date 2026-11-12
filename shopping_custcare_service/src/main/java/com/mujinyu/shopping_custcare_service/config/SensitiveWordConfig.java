package com.mujinyu.shopping_custcare_service.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.houbb.sensitive.word.api.IWordAllow;
import com.github.houbb.sensitive.word.api.IWordDeny;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.allow.WordAllows;
import com.github.houbb.sensitive.word.support.ignore.SensitiveWordCharIgnores;
import com.mujinyu.shopping_common.pojo.SensitiveWord;
import com.mujinyu.shopping_custcare_service.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词配置类
 */
@Configuration
@RequiredArgsConstructor
public class SensitiveWordConfig {

    /**
     * 品牌词兜底白名单。
     * <p>
     * 内置词库里的 "3p" 这类短 token 会把 iphone13pro 判成违禁（实测 findAll("帮我找一下 iphone13pro 手机") -> [3p]）。
     * 本次修复直接不再加载内置词库，这里再显式兜一层，防止以后有人往 bz_sensitive_word 的 deny 表里
     * 加了 "13p"/"3p" 之类的短词又把商品词误杀。
     * <p>
     * 注意：houbb 的 allow 是拿「命中的词」去比对，加整词不一定能拦住 "3p"，
     * 真正的修复是 {@link #sensitiveWordBs()} 里去掉 WordDenys.defaults()。
     */
    private static final List<String> BRAND_ALLOW_WORDS = List.of(
            "iphone13pro", "iphone14pro", "iphone16pro");

    private final SensitiveWordMapper sensitiveWordMapper;

    /**
     * 敏感词过滤器
     *
     * @return 初始化完成的过滤器
     */
    @Bean
    public SensitiveWordBs sensitiveWordBs() {
        return SensitiveWordBs.newInstance() // 创建敏感词实例
                // 只用数据库维护的黑名单，不再 chains(WordDenys.defaults())。
                // 原因：内置词库里有 "3p" 这种短 token，配合 ignoreWidth/charIgnore 后，
                // "iphone13pro" 里的 1-3-p 连续片段会被判命中（实测 findAll 返回 [3p]），
                // 而 iphone15/iphone16pro 不命中。去掉内置词库是最彻底的修法。
                .wordDeny(new IWordDeny() {
                    @Override
                    public List<String> deny() {
                        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
                        wrapper.eq("type", "deny");
                        return sensitiveWordMapper.selectList(wrapper)
                                .stream()
                                .map(SensitiveWord::getWord)
                                // 单字、空词最容易误伤正常咨询，deny 表里只保留 2 字以上的词
                                .filter(word -> word != null && word.length() >= 2)
                                .toList();
                    }
                })
                // 保留白名单：内置的白名单词（退货、正品…）加数据库 allow 表，再加品牌词兜底
                .wordAllow(WordAllows.chains(WordAllows.defaults(), new IWordAllow() {
                    @Override
                    public List<String> allow() {
                        QueryWrapper<SensitiveWord> wrapper = new QueryWrapper<>();
                        wrapper.eq("type", "allow");
                        List<String> allows = new ArrayList<>(sensitiveWordMapper.selectList(wrapper)
                                .stream()
                                .map(SensitiveWord::getWord)
                                .filter(word -> word != null && !word.isBlank())
                                .toList());
                        // 商品品牌词：iphone13pro / iphone14pro / iphone16pro 不能被误杀
                        allows.addAll(BRAND_ALLOW_WORDS);
                        return allows;
                    }
                }))
                // 忽略大小写：Fuck / FUCK / fuck 都能识别
                .ignoreCase(true)
                // 忽略全角半角：ＦＵＣＫ 等同于 FUCK
                .ignoreWidth(true)
                // 忽略夹在词中间的特殊字符：「傻*逼」「F*U*C*K」「草@泥@马」都能识别。
                // 这是 0.29.x 提供的 IWordFormat/charIgnore 机制，不开启时「傻*逼」是漏网的
                .charIgnore(SensitiveWordCharIgnores.specialChars())
                // 启用URL检查（默认就有，写出来是为了让规则一眼可见）
                .enableUrlCheck(true)
                // 启用邮箱检查
                .enableEmailCheck(true)
                // 启用数字检查
                .enableNumCheck(true)
                // 初始化（必须最后调用：init 会把上面的配置编译成 DFA，之后再改不生效）
                .init();
    }
}