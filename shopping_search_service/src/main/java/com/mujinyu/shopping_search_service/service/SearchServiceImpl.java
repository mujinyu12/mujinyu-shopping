package com.mujinyu.shopping_search_service.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.*;
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mujinyu.shopping_common.pojo.*;
import com.mujinyu.shopping_common.result.BusException;
import com.mujinyu.shopping_common.result.CodeEnum;
import com.mujinyu.shopping_common.service.SearchService;
import com.mujinyu.shopping_search_service.repository.GoodsESRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

@DubboService
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {
    private final ElasticsearchClient client;
    private final GoodsESRepository goodsESRepository;
    private final ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 相关性下限：低于该分值的文档视为“不相关”，不再返回（缺陷 23）
     */
    private static final float MIN_SCORE = 1.0f;
    /**
     * 深分页保护：page * size 不得超过该值（缺陷 32）
     */
    private static final long MAX_PAGE_WINDOW = 10_000L;
    /**
     * 每页条数上限（缺陷 32）
     */
    private static final int MAX_PAGE_SIZE = 100;
    /**
     * 查询面板（品牌/类目/规格）默认展示条数
     */
    private static final int SEARCH_PANEL_SIZE = 20;

    /**
     * 分词方法
     * @param text 待分词的文本
     * @param analyzer 分词器
     * @return 分词结果
     */
    @SneakyThrows
    public List<String> analyze(String text, String analyzer) {
        // 创建分词请求
        AnalyzeRequest request = AnalyzeRequest.of(a -> a.index("goods").analyzer(analyzer).text(text));
        // 发送分词请求
        AnalyzeResponse response = client.indices().analyze(request);
        // 处理分词结果
        List<String> words = new ArrayList<>();
        List<AnalyzeToken> tokens = response.tokens();
        for (AnalyzeToken token : tokens) {
            String term = token.token();
            words.add(term);
        }
        return words;
    }
    
    @Override
    public List<String> autoSuggest(String keyword) {
        // 0、空关键字直接返回空集合，避免拿 null 去请求 ES
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        try {
            // 1、自动补齐查询条件
            Suggester suggester = Suggester.of(
                    s -> s.suggesters("prefix_suggestion", FieldSuggester.of(
                            fs -> fs.completion(
                                    cs -> cs.skipDuplicates(true) // 去重
                                            .size(10)
                                            .field("tags")
                            )
                    )).text(keyword.trim())
            );
            // 2、自动补齐查询
            SearchResponse<Map> response = client.search(s -> s.index("goods").suggest(suggester), Map.class);
            // 3、处理查询结果：suggest 结果可能整体为空、缺失前缀补全项，逐级判空，不能再直接 get(0)（缺陷 30）
            var resultMap = response.suggest();
            if (resultMap == null) {
                return Collections.emptyList();
            }
            var suggestionList = resultMap.get("prefix_suggestion");
            if (suggestionList == null || suggestionList.isEmpty()) {
                return Collections.emptyList();
            }
            var suggestion = suggestionList.get(0); // 获取第一个自动补齐建议
            if (suggestion == null || suggestion.completion() == null) {
                return Collections.emptyList();
            }
            // 4、收集自动补齐文本，过滤空值
            List<String> keywords = new ArrayList<>();
            for (var option : suggestion.completion().options()) {
                if (option.text() != null) {
                    keywords.add(option.text());
                }
            }
            return keywords;
        } catch (IOException e) {
            // 自动补全是可降级功能，ES 异常时只记录日志并返回空列表，不能把 500 抛给前端
            log.error("搜索自动补全失败，keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }

    @Override
    public GoodsSearchResult search(GoodsSearchParam goodsSearchParam) {
        // 查询参数为空时按参数异常处理，避免后续拆箱/解引用 NPE（缺陷 32）
        if (goodsSearchParam == null) {
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        // 1、构建ES搜索条件
        NativeQuery query = buildQuery(goodsSearchParam);
        // 2、搜索
        SearchHits<GoodsES> search = elasticsearchTemplate.search(query, GoodsES.class);
        // 3、将查询结果封装为Mybatis-Plus的Page对象
        // 3.1 将SearchHits对象转为List
        List<GoodsES> content = new ArrayList<>();
        for (SearchHit<GoodsES> goodsESSearchHit : search) {
            GoodsES goodsES = goodsESSearchHit.getContent();
            content.add(goodsES);
        }
        // 3.2 将List转为MybatisPlus的Page对象
        Page<GoodsES> goodsESPage = new Page<>();
        goodsESPage.setCurrent(goodsSearchParam.getPage()) // 设置当前页码
                .setSize(goodsSearchParam.getSize()) // 设置每页大小
                .setTotal(search.getTotalHits()) // 设置总记录数
                .setRecords(content); // 设置记录列表
        // 4、封装查询结果
        GoodsSearchResult goodsSearchResult = new GoodsSearchResult();
        // 4.1 封装商品
        goodsSearchResult.setGoodsPage(goodsESPage);
        // 4.2 封装查询参数
        goodsSearchResult.setGoodsSearchParam(goodsSearchParam);
        // 4.3 封装查询面板
        buildSearchPanel(goodsSearchParam, goodsSearchResult);
        return goodsSearchResult;
    }

    /**
     * 封装查询面板，即根据查询条件，找到查询结果关联度前20的名的商品进行封装
     * @param goodsSearchParam 查询条件对象
     * @param goodsSearchResult 查询结果对象
     */
    public void buildSearchPanel(GoodsSearchParam goodsSearchParam, GoodsSearchResult goodsSearchResult) {
        // 1、构造查询条件：用副本查询，避免把返回体里的真实分页/排序参数改成面板参数（缺陷 32）
        GoodsSearchParam panelParam = copyOf(goodsSearchParam);
        panelParam.setPage(1);
        panelParam.setSize(SEARCH_PANEL_SIZE);
        panelParam.setSort(null);
        panelParam.setSortFiled(null);
        NativeQuery query = buildQuery(panelParam);
        // 2、搜索
        SearchHits<GoodsES> search = elasticsearchTemplate.search(query, GoodsES.class);
        // 3、将结果封装为List对象
        List<GoodsES> content = new ArrayList<>();
        for (SearchHit<GoodsES> goodsESSearchHit : search) {
            GoodsES goodsES = goodsESSearchHit.getContent();
            content.add(goodsES);
        }
        // 4、遍历集合，封装查询面板
        // 商品相关的品牌列表
        Set<String> brands = new HashSet<>();
        // 商品相关的类型列表
        Set<String> productTypes = new HashSet<>();
        // 商品相关的规格列表
        Map<String, Set<String>> specifications = new HashMap<>();

        for (GoodsES goodsES : content) {
            // 获取品牌：空品牌不进面板，避免前端出现空白筛选项
            if (StringUtils.hasText(goodsES.getBrand())) {
                brands.add(goodsES.getBrand());
            }
            // 获取类型
            List<String> productType = goodsES.getProductType();
            if (productType != null) {
                productTypes.addAll(productType);
            }
            // 获取规格
            Map<String, List<String>> specification = goodsES.getSpecification();
            if (specification == null) {
                continue;
            }
            Set<Map.Entry<String, List<String>>> entries = specification.entrySet();
            for (Map.Entry<String, List<String>> entry : entries) {
                // 规格名
                String key = entry.getKey();
                // 规格值
                List<String> value = entry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                // 如果specifications有该规格项，则像规格中添加规格项，如果没有该规格，新增键值对
                if (!specifications.containsKey(key)) {
                    specifications.put(key, new HashSet<>(value));
                } else {
                    specifications.get(key).addAll(value);
                }
            }
        }
        goodsSearchResult.setBrands(brands);
        goodsSearchResult.setProductType(productTypes);
        goodsSearchResult.setSpecifications(specifications);
    }

    /**
     * 复制查询参数，供查询面板使用，避免污染调用方对象
     * @param source 原查询参数
     * @return 参数副本
     */
    private GoodsSearchParam copyOf(GoodsSearchParam source) {
        GoodsSearchParam copy = new GoodsSearchParam();
        copy.setKeyword(source.getKeyword());
        copy.setBrand(source.getBrand());
        copy.setHighPrice(source.getHighPrice());
        copy.setLowPrice(source.getLowPrice());
        copy.setSpecificationOption(source.getSpecificationOption());
        copy.setSortFiled(source.getSortFiled());
        copy.setSort(source.getSort());
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        return copy;
    }

    /**
     * 构造查询搜索条件
     * @param goodsSearchParam 查询条件对象
     * @return 封装好的搜索条件
     */
    private NativeQuery buildQuery(GoodsSearchParam goodsSearchParam) {
        // 0、分页参数先规范化，避免 page=0/size=0 直接抛 IllegalArgumentException（缺陷 32）
        normalizePaging(goodsSearchParam);
        // 1、创建复杂查询条件对象
        NativeQueryBuilder nativeQueryBuilder = new NativeQueryBuilder();
        BoolQuery.Builder builder = new BoolQuery.Builder();
        // 2、如果查询条件有关键词，关键词可以匹配商品名、副标题、品牌字段，否则查询所有商品
        if (!StringUtils.hasText(goodsSearchParam.getKeyword())) {
            MatchAllQuery matchAllQuery = new MatchAllQuery.Builder().build();
            builder.must(matchAllQuery._toQuery());
        } else {
            String keyword = goodsSearchParam.getKeyword().trim();
            // 商品名权重最高；operator=AND 表示所有分词都要命中，避免“搜什么都有货”（缺陷 23）
            MultiMatchQuery keywordQuery = MultiMatchQuery.of(q -> q.query(keyword)
                    .fields("goodsName^3", "caption^2", "brand^1")
                    .operator(Operator.And)
                    .minimumShouldMatch("100%"));
            builder.must(keywordQuery._toQuery());
        }
        // 3、如果查询条件有品牌，精准匹配品牌（缺陷 29）
        // brand 若被建成 text，term 查 "华为" 会因分词永远不命中；因此 keyword 子字段优先、text 分词短语兜底
        String brand = goodsSearchParam.getBrand();
        if (StringUtils.hasText(brand)) {
            String normalizedBrand = brand.trim();
            BoolQuery brandQuery = new BoolQuery.Builder()
                    .should(TermQuery.of(q -> q.field("brand.keyword").value(normalizedBrand))._toQuery())
                    .should(MatchPhraseQuery.of(q -> q.field("brand").query(normalizedBrand))._toQuery())
                    .minimumShouldMatch("1")
                    .build();
            builder.must(brandQuery._toQuery());
        }
        // 4、如果查询条件有价格，则匹配价格：上下界各自独立生效，并保证 low <= high（缺陷 31）
        Double highPrice = goodsSearchParam.getHighPrice();
        Double lowPrice = goodsSearchParam.getLowPrice();
        if (lowPrice != null || highPrice != null) {
            if (lowPrice != null && highPrice != null && lowPrice > highPrice) {
                // 前端传反了就自动交换，不直接返回空结果
                Double tmp = lowPrice;
                lowPrice = highPrice;
                highPrice = tmp;
            }
            final Double lowerBound = lowPrice;
            final Double upperBound = highPrice;
            RangeQuery priceQuery = RangeQuery.of(r -> r.number(n -> {
                n.field("price");
                if (lowerBound != null) {
                    n.gte(lowerBound);
                }
                if (upperBound != null) {
                    n.lte(upperBound);
                }
                return n;
            }));
            // 价格只做过滤，不参与评分，可被 ES 缓存
            builder.filter(priceQuery._toQuery());
        }
        // 5、只返回在售商品：下架商品即便还残留在索引里也不出现在搜索结果中（缺陷 21）
        builder.filter(TermQuery.of(q -> q.field("isMarketable").value(true))._toQuery());
        // 6、如果查询条件有规格项，则精准匹配规格项
        Map<String, String> specificationOptions = goodsSearchParam.getSpecificationOption();
        if (specificationOptions != null && !specificationOptions.isEmpty()) {
            Set<Map.Entry<String, String>> entries = specificationOptions.entrySet();
            for (Map.Entry<String, String> entry : entries) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                    TermQuery optionQuery = TermQuery.of(q -> q.field("specification." + key + ".keyword").value(value));
                    builder.must(optionQuery._toQuery());
                }
            }
        }
        nativeQueryBuilder.withQuery(builder.build()._toQuery());
        // 7、相关性下限：低于该分值的命中直接丢弃（缺陷 23）
        nativeQueryBuilder.withMinScore(MIN_SCORE);
        // 按价格/新品排序时 ES 默认不计算得分，会导致 minScore 把结果全部过滤掉，这里强制计算得分
        nativeQueryBuilder.withTrackScores(true);
        // 8、添加分页条件（page/size 已在 normalizePaging 中规范化）
        PageRequest pageable = PageRequest.of(goodsSearchParam.getPage() - 1, goodsSearchParam.getSize());
        nativeQueryBuilder.withPageable(pageable);
        // 9、如果查询条件有排序，则添加排序条件
        String sortFiled = goodsSearchParam.getSortFiled();
        String sort = goodsSearchParam.getSort();
        if (StringUtils.hasText(sort) && StringUtils.hasText(sortFiled)) {
            Sort sortParam = null;
            // 新品的正序是id的倒序
            if (sortFiled.equals("NEW")) {
                if (sort.equals("ASC")) {
                    sortParam = Sort.by(Sort.Direction.DESC, "id");
                }
                if (sort.equals("DESC")) {
                    sortParam = Sort.by(Sort.Direction.ASC, "id");
                }
            }
            if (sortFiled.equals("PRICE")) {
                if (sort.equals("ASC")) {
                    sortParam = Sort.by(Sort.Direction.ASC, "price");
                }
                if (sort.equals("DESC")) {
                    sortParam = Sort.by(Sort.Direction.DESC, "price");
                }
            }
            // 非法的排序枚举值不添加排序，而不是把 null 塞给 withSort
            if (sortParam != null) {
                nativeQueryBuilder.withSort(sortParam);
            }
        }
        // 10、返回封装好的搜索条件
        return nativeQueryBuilder.build();
    }

    /**
     * 分页参数规范化：page 至少为 1，size 落在 [1, 100]，并禁止 page*size 超过 10000 的深分页
     * @param goodsSearchParam 查询参数，会被就地回写为规范化后的值
     */
    private void normalizePaging(GoodsSearchParam goodsSearchParam) {
        int page = (goodsSearchParam.getPage() == null || goodsSearchParam.getPage() < 1)
                ? 1 : goodsSearchParam.getPage();
        int size = (goodsSearchParam.getSize() == null || goodsSearchParam.getSize() < 1)
                ? 10 : Math.min(goodsSearchParam.getSize(), MAX_PAGE_SIZE);
        if ((long) page * size > MAX_PAGE_WINDOW) {
            // 深分页会拖垮 ES，直接按参数异常拒绝
            throw new BusException(CodeEnum.PARAMETER_ERROR);
        }
        // 回写，保证返回体里的 page/size 与实际查询一致
        goodsSearchParam.setPage(page);
        goodsSearchParam.setSize(size);
    }

    @Override
    public void syncGoodsToES(GoodsDesc goodsDesc) {
        // 0、消息体本身为空时直接跳过，不能让消费线程抛 NPE 导致消息反复重试（缺陷 33）
        if (goodsDesc == null || goodsDesc.getId() == null) {
            log.warn("商品同步消息为空或缺少商品id，跳过ES同步");
            return;
        }
        Long goodsId = goodsDesc.getId();
        // 1、下架商品不再写入索引，而是删除已有文档：否则编辑一次下架商品就会把文档重新写回索引（缺陷 21）
        if (!Boolean.TRUE.equals(goodsDesc.getIsMarketable())) {
            try {
                goodsESRepository.deleteById(goodsId);
            } catch (Exception e) {
                log.warn("商品[{}]已下架，删除ES文档失败", goodsId, e);
            }
            return;
        }
        try {
            // 2、将商品详情数据转为GoodES对象：品牌、类目、副标题、规格都可能为空（缺陷 33）
            GoodsES goodsES = new GoodsES();
            goodsES.setId(goodsId);
            goodsES.setGoodsName(goodsDesc.getGoodsName());
            goodsES.setCaption(goodsDesc.getCaption());
            goodsES.setPrice(goodsDesc.getPrice());
            goodsES.setHeaderPic(goodsDesc.getHeaderPic());
            goodsES.setIsMarketable(Boolean.TRUE.equals(goodsDesc.getIsMarketable()));
            // 商品类型集合：三级类目可能缺失，只收集非空名称
            List<String> productType = new ArrayList<>();
            if (goodsDesc.getProductType1() != null) {
                productType.add(goodsDesc.getProductType1().getName());
            }
            if (goodsDesc.getProductType2() != null) {
                productType.add(goodsDesc.getProductType2().getName());
            }
            if (goodsDesc.getProductType3() != null) {
                productType.add(goodsDesc.getProductType3().getName());
            }
            productType.removeIf(name -> !StringUtils.hasText(name));
            goodsES.setProductType(productType);
            // 3、商品规格集合
            Map<String, List<String>> specificationMap = new HashMap<>();
            List<Specification> specifications = goodsDesc.getSpecifications();
            if (specifications != null) {
                for (Specification specification : specifications) {
                    if (specification == null || !StringUtils.hasText(specification.getSpecName())) {
                        continue;
                    }
                    // 拿到规格项名
                    List<String> optionNames = new ArrayList<>();
                    List<SpecificationOption> specificationOptions = specification.getSpecificationOptions();
                    if (specificationOptions != null) {
                        for (SpecificationOption option : specificationOptions) {
                            if (option != null && StringUtils.hasText(option.getOptionName())) {
                                optionNames.add(option.getOptionName());
                            }
                        }
                    }
                    specificationMap.put(specification.getSpecName(), optionNames);
                }
            }
            goodsES.setSpecification(specificationMap);
            // 4、关键字：品牌名 + 商品名分词 + 副标题分词，空值不参与分词
            List<String> tags = new ArrayList<>();
            if (goodsDesc.getBrand() != null && StringUtils.hasText(goodsDesc.getBrand().getName())) {
                goodsES.setBrand(goodsDesc.getBrand().getName()); // 品牌名是关键字
                tags.add(goodsDesc.getBrand().getName());
            }
            if (StringUtils.hasText(goodsDesc.getGoodsName())) {
                // 分词失败（如 ik 分词器缺失）不能影响商品入库，降级为不加该关键字
                try {
                    tags.addAll(analyze(goodsDesc.getGoodsName(), "ik_smart")); // 商品名分词后是关键字
                } catch (Exception e) {
                    log.warn("商品[{}]名称分词失败，跳过名称关键字", goodsId, e);
                }
            }
            if (StringUtils.hasText(goodsDesc.getCaption())) {
                try {
                    tags.addAll(analyze(goodsDesc.getCaption(), "ik_smart")); // 副标题分词后是关键字
                } catch (Exception e) {
                    log.warn("商品[{}]副标题分词失败，跳过副标题关键字", goodsId, e);
                }
            }
            // 去重并去掉分词结果里的空串，保持插入顺序
            tags.removeIf(tag -> !StringUtils.hasText(tag));
            goodsES.setTags(new ArrayList<>(new LinkedHashSet<>(tags)));

            // 5、将GoodES对象存入ES
            goodsESRepository.save(goodsES);
        } catch (Exception e) {
            // 同步失败只记录日志，避免异常回抛给 MQ 触发无意义的重复消费
            log.error("商品[{}]同步到ES失败", goodsId, e);
        }
    }

    @Override
    public void delete(Long id) {
        // 空 id 直接忽略，避免 ES 客户端抛异常导致 MQ 反复重试
        if (id == null) {
            log.warn("删除ES商品文档时商品id为空，忽略");
            return;
        }
        goodsESRepository.deleteById(id);
    }
}
